const STORAGE_KEY = 'jetmenu.checkout.pending-plan'

/**
 * The plan a visitor picked on the landing page, remembered across sign-up.
 *
 * The slug normally rides in the URL (`/checkout?plan=basico` -> `/register?plan=basico`),
 * but that breaks when email confirmation is on: the confirmation link points at a fixed
 * `/email-verificado` with no query string, and it usually opens in a new tab from the mail
 * client. So the slug is also persisted here - `localStorage`, not `sessionStorage`,
 * precisely because that new tab is a different session.
 *
 * The URL stays the primary carrier; this is the fallback. Reads are destructive
 * (`takePendingPlan`) so a merchant who abandons checkout is not pushed back into payment
 * on every later login.
 *
 * Every access is guarded: storage throws in private-mode Safari and is a stub in some
 * test environments, and losing the slug must never break sign-up.
 */
export function savePendingPlan(slug: string): void {
  if (!slug) return
  try {
    localStorage.setItem(STORAGE_KEY, slug)
  } catch {
    // Not remembering the plan is recoverable: the merchant can subscribe from Settings.
  }
}

/** Reads the pending plan without consuming it. */
export function peekPendingPlan(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY) || null
  } catch {
    return null
  }
}

/** Reads the pending plan and clears it, so it is acted on at most once. */
export function takePendingPlan(): string | null {
  const slug = peekPendingPlan()
  clearPendingPlan()
  return slug
}

export function clearPendingPlan(): void {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to do - a stale slug is dropped on its next read anyway.
  }
}
