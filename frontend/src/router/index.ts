import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/esqueci-senha',
      name: 'forgot-password',
      component: () => import('@/views/ForgotPasswordView.vue'),
      meta: { public: true },
    },
    {
      path: '/redefinir-senha',
      name: 'reset-password',
      component: () => import('@/views/ResetPasswordView.vue'),
      // The Supabase recovery link signs the user in (detectSessionInUrl), so this
      // page must stay visible when authenticated instead of bouncing to the dashboard.
      meta: { public: true, allowAuthenticated: true },
    },
    {
      path: '/email-verificado',
      name: 'email-verified',
      component: () => import('@/views/EmailVerifiedView.vue'),
      // Confirming the email logs the user in (detectSessionInUrl), so this page must
      // stay visible even when authenticated instead of bouncing to the dashboard.
      meta: { public: true, allowAuthenticated: true },
    },
    {
      // The landing page lives in its own project at jetmenu.com.br. This app is
      // served from app.jetmenu.com.br, so its root belongs to the product: the
      // guard sends anonymous visitors to /login from here.
      path: '/',
      redirect: '/dashboard',
    },
    {
      // Pricing lives only on the landing page; its CTA links here with the chosen
      // plan. Public so an anonymous visitor reaches it and is sent to register, and
      // allowAuthenticated so a logged-in merchant goes straight to payment.
      path: '/checkout',
      name: 'checkout',
      component: () => import('@/views/CheckoutView.vue'),
      meta: { public: true, allowAuthenticated: true },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('@/views/OrdersView.vue'),
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('@/views/ProductsView.vue'),
    },
    {
      path: '/ingredients',
      name: 'ingredients',
      component: () => import('@/views/IngredientsView.vue'),
    },
    {
      path: '/customers',
      name: 'customers',
      component: () => import('@/views/CustomersView.vue'),
    },
    {
      path: '/fees',
      name: 'fees',
      component: () => import('@/views/FeesView.vue'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue'),
    },
  ],
})

/**
 * Auth guard for every navigation. Exported for tests.
 *
 * The initial navigation starts at `app.use(router)`, before main.ts finishes
 * awaiting the session restore — so on a page refresh this guard would see a
 * not-yet-hydrated store and bounce a logged-in user to /login while the token
 * is still in storage. Awaiting init() (single-flight, idempotent) guarantees
 * the persisted session is loaded before any redirect decision.
 */
export async function authGuard(to: RouteLocationNormalized) {
  const auth = useAuthStore()
  await auth.init()
  const isPublic = to.meta.public === true

  if (!auth.isAuthenticated && !isPublic) {
    return { name: 'login' }
  }

  if (auth.isAuthenticated && isPublic && to.meta.allowAuthenticated !== true) {
    return { name: 'dashboard' }
  }

  // /email-verificado is only reachable from a Supabase confirmation link (which
  // carries #access_token in the hash) or when the user is already authenticated
  // (e.g. page refresh after confirming). Direct navigation without a token is
  // meaningless, so redirect to register.
  if (to.name === 'email-verified') {
    const hasToken = window.location.hash.includes('access_token')
    if (!hasToken && !auth.isAuthenticated) {
      return { name: 'register' }
    }
  }

  // /redefinir-senha is only reachable from a Supabase recovery link (which carries
  // #access_token in the hash) or with an active session. Direct navigation without
  // either cannot set a password, so redirect to the request form.
  if (to.name === 'reset-password') {
    const hasToken = window.location.hash.includes('access_token')
    if (!hasToken && !auth.isAuthenticated) {
      return { name: 'forgot-password' }
    }
  }
}

router.beforeEach(authGuard)

export default router
