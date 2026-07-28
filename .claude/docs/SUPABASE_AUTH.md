# Supabase Auth Integration

JetMenu delegates **authentication** (login, signup, password, email confirmation,
token issuance/refresh) to **Supabase**. The Spring Boot backend is a pure **resource
server**: it only validates Supabase-issued JWTs and maps the Supabase user to a local
`Merchant`. The Vue frontend talks to Supabase directly via `@supabase/supabase-js`.

> Replaces the old self-issued JWT flow (email/password validated against the
> `merchants` table, tokens signed in-process). That flow was fully removed.

---

## Identity model

Supabase owns the user record (`auth.users`). The JWT `sub` claim is the **Supabase
user id** — NOT the merchant id. A new table maps them:

`identities` (entity `identity/Identity.java`):

| Column | Notes |
|---|---|
| `id` | UUID PK |
| `merchant_id` | UUID — plain column (not a JPA relationship), points to `merchants.id` |
| `provider` | `"supabase"` (room for `google`, etc.) |
| `provider_user_id` | the JWT `sub` (Supabase user id) |
| `created_at` | timestamp |
| | **unique (`provider`, `provider_user_id`)** |

Repo: `IdentityRepository.findByProviderAndProviderUserId(provider, providerUserId)`.

---

## Backend

### Token validation — `security/JwtAuthFilter`

`OncePerRequestFilter` (wired in `SecurityConfig`, **replaces** `oauth2ResourceServer`):
reads `Authorization: Bearer <token>`, validates via the `JwtDecoder` bean, and sets a
`UsernamePasswordAuthenticationToken` whose **principal is the `sub` String** (Supabase
user id) in the `SecurityContext`. Invalid token → `401`. No header → chain continues
unauthenticated (and `anyRequest().authenticated()` then rejects).

`JwtDecoder` beans are profile-scoped:
- **prod** — `config/SupabaseJwtDecoderConfig` (`@Profile("prod")`): JWKS discovery via
  `NimbusJwtDecoder.withIssuerLocation(supabase.issuer-uri)` + `DelegatingOAuth2TokenValidator`
  (default issuer/timestamp validators + `AudienceValidator`).
- **dev/test** — `config/LocalJwtDecoderConfig` (`@Profile({"dev","test"})`): in-memory
  RSA public key decoder, so the app boots without a live Supabase (no network at startup).
- `config/AudienceValidator` — requires `aud` to contain `"authenticated"` (Supabase default).

`SecurityConfig`: CSRF off, CORS on, `anyRequest().authenticated()`,
`addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`. The explicit
`STATELESS` session policy was **removed** — the filter re-authenticates every request
from the token (effectively stateless), and keeping the default avoids breaking the
`@WebMvcTest` security-context bridge.

### Resolving the merchant — `auth/AuthHelper`

`@Component` with `getMerchantId(Authentication auth)`:
`(String) auth.getPrincipal()` → `identities` lookup → `merchantId`, or **`403`**
(`ResponseStatusException`) if the user has no merchant yet (signal for "not provisioned").

Cached with Caffeine: `@Cacheable("merchantIdByProviderUser", key = "#auth.principal")`,
TTL **10 min** (`config/CacheConfig`). Exceptions are not cached, so a not-yet-provisioned
user never "sticks". `MerchantService` mutations (`update`, `updateMe`, `updateAnotaAIKey`,
`updateMyPreferences`, `delete`) carry `@CacheEvict(allEntries = true)`.

> **`MerchantContext` was deleted.** Previously every service read the merchant id from the
> `SecurityContext` (because `sub == merchantId`). Now **controllers** take `Authentication
> auth`, call `authHelper.getMerchantId(auth)`, and pass `merchantId` as the first argument
> to the **service** methods. Services no longer touch security at all.

### Provisioning — `POST /api/auth/provision`

Just-in-time link between a Supabase user and a `Merchant` (`auth/ProvisionController` +
`auth/ProvisionService`). Authenticated; reads the Supabase user id from
`SecurityContextHolder.getContext().getAuthentication().getName()`. Body `ProvisionRequest`:
`merchantName`, `cnpj`, `email`, `phone?`.

- **Idempotent**: if an `Identity` already exists → returns the existing merchant.
- Otherwise dup-checks email/CNPJ (`409` on conflict), creates `Merchant` (**no password**)
  + `Identity`, returns `MerchantResponse` (`200`).

`Merchant.password` is now **nullable** (Supabase owns credentials).

### Endpoints touched

- New: `POST /api/auth/provision`.
- All business controllers (`/api/orders`, `/api/products`, `/api/categories`,
  `/api/fees`, `/api/ingredients`, `/api/customers`, `/api/dashboard`, `/api/export`,
  `/api/notifications`, `/api/merchants`, `/api/integrations/anotaai`, includes) now take
  the auth principal and pass `merchantId` down.
- `GET /api/merchants/me` is the canonical "current merchant" lookup the frontend uses.
- Removed: `POST /api/auth/login`, `POST /api/auth/register`.

### Removed / dependencies

- Deleted: `AuthController`, `AuthService`, `LoginRequest/LoginResponse`,
  `InvalidCredentialsException`, `InactiveMerchantException`, `JwtConfig` (self-issued
  tokens), `MerchantContext`, and their tests.
- `pom.xml`: removed `spring-boot-starter-oauth2-authorization-server`; added
  `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`. Kept
  `spring-boot-starter-oauth2-resource-server` (provides `Jwt`/`JwtDecoder`/validators).

### Backend config (prod)

`application-prod.properties`:
```
supabase.issuer-uri=${SUPABASE_ISSUER_URI}      # https://<project>.supabase.co/auth/v1
supabase.audience=${SUPABASE_AUDIENCE:authenticated}
```
Dev/test set **no** `issuer-uri` (local decoder is used instead).

---

## Frontend

### Client + env — `src/lib/supabase.ts`

`createClient(VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY, { auth: { persistSession,
autoRefreshToken, detectSessionInUrl } })`. Falls back to placeholder values when env is
absent so unit tests don't crash. Env vars (`.env`, documented in `.env.example`, typed in
`env.d.ts`): `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`, `VITE_API_BASE_URL`.

### API token — `src/services/api.ts`

Async request interceptor reads the access token from `supabase.auth.getSession()` and sets
`Authorization: Bearer <token>` (Supabase auto-refreshes it). `401` → `supabase.auth.signOut()`
+ redirect to `/login`.

### State — `src/stores/authStore.ts` (Pinia)

- `init()` — restore session (`getSession`), subscribe to `onAuthStateChange`, and
  best-effort load the merchant. Called in `main.ts` **before** `app.mount`.
- `login(email,password)` — `signInWithPassword` (pt-BR error messages) →
  `ensureProvisionedAndLoad()`.
- `register(form)` — `signUp({ email, password, options: { data: { merchantName, cnpj,
  phone }, emailRedirectTo } })`. **Email confirmation is ON**, so there is no session yet:
  sets `awaitingEmailConfirmation = true` (RegisterView shows a "confirm your email" notice).
- `ensureProvisionedAndLoad()` — `GET /merchants/me`; on **403** calls
  `POST /api/auth/provision` with the business data stored in `session.user.user_metadata`
  (+ email), then loads `/me`. This is where the first-login provision happens.
- `logout()` — `signOut()`; `updateAnotaAIKey()` unchanged.

`router/index.ts` guard uses `authStore.isAuthenticated` (Supabase session) instead of a
manual `localStorage` token. `services/authService.ts` → `provision()`; `userService.ts`
→ `getMe()`. Manual `localStorage` token/user management removed (Supabase persists its
own session).

---

## End-to-end flow (email confirmation ON, email/password)

```
Register (RegisterView)
  └─ supabase.auth.signUp(email, pwd, { data:{merchantName,cnpj,phone} })
     → no session → "confirme seu email"

User clicks confirmation link in email  → lands on /login

Login (LoginView)
  └─ supabase.auth.signInWithPassword → session (access+refresh tokens)
     └─ GET /api/merchants/me
          ├─ 200 → load merchant → dashboard            (returning user)
          └─ 403 → POST /api/auth/provision (from user_metadata)
                   → creates Merchant + Identity → GET /me → dashboard   (first login)

Every API request: axios interceptor attaches Bearer <supabase access_token>
  → backend JwtAuthFilter validates (JWKS) → AuthHelper maps sub→merchantId (cached)
```

---

## Decisions & rationale

- **Provision on first login (not at signup):** with email confirmation ON, `signUp`
  returns no session, so we stash `merchantName/cnpj/phone` in Supabase `user_metadata`
  and provision on the first authenticated request.
- **Principal as `String` (the `sub`):** lets `AuthHelper` cache by a stable key and keeps
  resolution explicit; `ProvisionController` reads it via `SecurityContextHolder` (the
  `Authentication` method-arg is null in `@WebMvcTest` slices).
- **Cache merchantId, not the entity:** avoids JPA detached-entity issues; 10-min TTL +
  evict-on-mutation.

---

## Out of scope / open items

- **Existing merchants** are not yet linked to Supabase users (no backfill). A current
  merchant signing up fresh would hit the `409` dup-email path — needs a link-by-email or
  data migration before go-live.
- **Email verification enforcement** in the backend (`email_verified` claim) is not enforced.
- **`MerchantRequest` still carries `password`** and `POST /api/merchants` (create) still
  hashes it — legacy path not yet unified with provisioning.
- **OAuth (Google):** would arrive without `cnpj` in metadata → needs a "complete
  registration" screen before provision.
- **"Forgot password" / "remember me"** in `LoginView` are still decorative.
- **Manual DB migration (dev/prod):** `ddl-auto=update` does not relax NOT NULL, so run
  `ALTER TABLE merchants ALTER COLUMN password DROP NOT NULL;` (test uses `create-drop`).
- **Pre-existing failures unrelated to this work:** backend
  `AnotaAISyncServiceIntegrationTest.syncOrders_iFood_shouldMatchProductByCanonicalNameWhenInternalIdIsBlank`;
  frontend empty `useValidation.spec.ts` and a few `require-to-throw-message` lint errors.

---

## Branches

- Backend: merged into `develop` (commit `94f7230`).
- Frontend: branch `feature/supabase-auth-frontend` (off `develop`).
