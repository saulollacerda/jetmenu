import { computed, getCurrentScope, onScopeDispose, ref } from 'vue'
import { connectErrorMessage, ifoodAuthService } from '@/services/ifoodAuthService'

export type IfoodFlowStep = 'loading' | 'code' | 'expired' | 'done'

export interface PendingIfoodAuth {
  v: 1
  userCode: string
  verificationUrl: string
  verificationUrlComplete: string
  expiresAt: number
}

export const IFOOD_PENDING_KEY = 'jetmenu.ifood.pendingAuth'

export function readPendingIfoodAuth(): PendingIfoodAuth | null {
  const raw = sessionStorage.getItem(IFOOD_PENDING_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as PendingIfoodAuth
    if (parsed?.v !== 1 || typeof parsed.userCode !== 'string' || typeof parsed.expiresAt !== 'number') {
      sessionStorage.removeItem(IFOOD_PENDING_KEY)
      return null
    }
    return parsed
  } catch {
    sessionStorage.removeItem(IFOOD_PENDING_KEY)
    return null
  }
}

export function hasPendingIfoodAuth(): boolean {
  return readPendingIfoodAuth() !== null
}

export function clearPendingIfoodAuth(): void {
  sessionStorage.removeItem(IFOOD_PENDING_KEY)
}

/**
 * State machine for the iFood OAuth linking flow. The countdown is always derived
 * from the absolute `expiresAt` timestamp (never a decremented counter), so it
 * survives reloads and background-tab interval throttling without drift. There is
 * no silent auto-renew: expiry moves to the 'expired' step and the user explicitly
 * regenerates — the paste input stays usable because the portal-issued
 * authorizationCode has its own independent lifetime.
 */
export function useIfoodConnectFlow() {
  const step = ref<IfoodFlowStep>('loading')
  const userCode = ref('')
  const verificationUrl = ref('')
  const verificationUrlComplete = ref('')
  const remainingSeconds = ref(0)
  const authCode = ref('')
  const error = ref<string | null>(null)
  const connecting = ref(false)

  let expiresAt = 0
  let timer: ReturnType<typeof setInterval> | null = null

  const flowInProgress = computed(() => step.value === 'code' || step.value === 'expired')

  function clearTimer() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function syncRemaining() {
    remainingSeconds.value = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000))
    if (remainingSeconds.value <= 0 && step.value === 'code') {
      clearTimer()
      step.value = 'expired'
    }
  }

  function armTimer() {
    clearTimer()
    syncRemaining()
    timer = setInterval(syncRemaining, 1000)
  }

  function hydrate(code: string, url: string, urlComplete: string, expiresAtMs: number) {
    userCode.value = code
    verificationUrl.value = url
    verificationUrlComplete.value = urlComplete
    expiresAt = expiresAtMs
  }

  async function start(): Promise<void> {
    error.value = null
    step.value = 'loading'
    try {
      const res = await ifoodAuthService.start()
      const expiresAtMs = Date.now() + res.expiresIn * 1000
      hydrate(res.userCode, res.verificationUrl, res.verificationUrlComplete, expiresAtMs)
      const pending: PendingIfoodAuth = {
        v: 1,
        userCode: res.userCode,
        verificationUrl: res.verificationUrl,
        verificationUrlComplete: res.verificationUrlComplete,
        expiresAt: expiresAtMs,
      }
      sessionStorage.setItem(IFOOD_PENDING_KEY, JSON.stringify(pending))
      step.value = 'code'
      armTimer()
    } catch {
      error.value = 'Não foi possível obter o código de vínculo. Tente novamente.'
      step.value = 'code'
    }
  }

  function resume(): boolean {
    const pending = readPendingIfoodAuth()
    if (!pending) return false

    hydrate(pending.userCode, pending.verificationUrl, pending.verificationUrlComplete, pending.expiresAt)
    if (pending.expiresAt <= Date.now()) {
      remainingSeconds.value = 0
      step.value = 'expired'
    } else {
      step.value = 'code'
      armTimer()
    }
    return true
  }

  async function regenerate(): Promise<void> {
    await start()
  }

  async function connect(): Promise<void> {
    const code = authCode.value.trim()
    if (!code) return
    connecting.value = true
    error.value = null
    try {
      await ifoodAuthService.connect(code)
      clearTimer()
      clearPendingIfoodAuth()
      step.value = 'done'
    } catch (e: unknown) {
      error.value = connectErrorMessage(e).message
    } finally {
      connecting.value = false
    }
  }

  function finish(): void {
    clearTimer()
    clearPendingIfoodAuth()
    step.value = 'done'
  }

  if (getCurrentScope()) {
    // Keep the storage record on dispose — resuming after navigation is the point.
    onScopeDispose(clearTimer)
  }

  return {
    step,
    userCode,
    verificationUrl,
    verificationUrlComplete,
    remainingSeconds,
    authCode,
    error,
    connecting,
    flowInProgress,
    start,
    resume,
    regenerate,
    connect,
    finish,
    syncRemaining,
  }
}
