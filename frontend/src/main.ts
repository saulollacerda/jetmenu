import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
// Tailwind first so the hand-written app styles in main.css keep precedence.
import './assets/tailwind.css'
import './assets/main.css'
import { useAuthStore } from '@/stores/authStore'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(router)

// Restore the persisted session before mounting so the first paint already has
// auth state (no login-layout flash). The router guard also awaits this same
// single-flight init(), which is what actually protects the initial navigation —
// app.use(router) starts navigating before the restore settles.
//
// Deliberately NOT a top-level await: init() lazily import()s the auth provider
// chunk, and that chunk statically imports this entry chunk back (Rollup hoists the
// shared modules into the entry). Suspending the entry mid-evaluation leaves the
// lazy chunk unable to finish linking, so the awaited promise never settles — the
// app silently never mounts and the page stays blank. Mounting from a callback lets
// the entry finish evaluating first, which breaks the cycle.
//
// Mount even when the restore fails: a rejected init() must not cost the user the
// whole app — the router guard re-awaits the same promise and handles auth itself.
useAuthStore(pinia)
  .init()
  .catch(() => {
    // ignored — an unrestorable session just means "logged out" for the guard.
  })
  .finally(() => {
    app.mount('#app')
  })
