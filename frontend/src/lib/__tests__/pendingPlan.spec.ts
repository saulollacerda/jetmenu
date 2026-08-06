import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  savePendingPlan,
  peekPendingPlan,
  takePendingPlan,
  clearPendingPlan,
} from '@/lib/pendingPlan'

// The global localStorage in this environment is an unusable stub, so the specs
// install a real in-memory one.
function installStorage(): Storage {
  const map = new Map<string, string>()
  const storage = {
    getItem: (k: string) => map.get(k) ?? null,
    setItem: (k: string, v: string) => void map.set(k, v),
    removeItem: (k: string) => void map.delete(k),
    clear: () => map.clear(),
    key: () => null,
    get length() {
      return map.size
    },
  } as unknown as Storage
  Object.defineProperty(window, 'localStorage', { configurable: true, value: storage })
  return storage
}

describe('pendingPlan', () => {
  beforeEach(() => {
    installStorage()
  })

  it('guarda e devolve o plano escolhido', () => {
    savePendingPlan('basico')
    expect(peekPendingPlan()).toBe('basico')
  })

  it('peek não consome o valor', () => {
    savePendingPlan('basico')
    expect(peekPendingPlan()).toBe('basico')
    expect(peekPendingPlan()).toBe('basico')
  })

  it('take devolve o valor e limpa, para agir no máximo uma vez', () => {
    savePendingPlan('basico')
    expect(takePendingPlan()).toBe('basico')
    expect(takePendingPlan()).toBeNull()
    expect(peekPendingPlan()).toBeNull()
  })

  it('devolve null quando não há plano guardado', () => {
    expect(peekPendingPlan()).toBeNull()
    expect(takePendingPlan()).toBeNull()
  })

  it('ignora slug vazio', () => {
    savePendingPlan('')
    expect(peekPendingPlan()).toBeNull()
  })

  it('clear remove o valor', () => {
    savePendingPlan('basico')
    clearPendingPlan()
    expect(peekPendingPlan()).toBeNull()
  })

  it('não propaga erro quando o storage falha (Safari privado)', () => {
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        getItem: () => {
          throw new Error('denied')
        },
        setItem: () => {
          throw new Error('denied')
        },
        removeItem: () => {
          throw new Error('denied')
        },
      } as unknown as Storage,
    })

    expect(() => savePendingPlan('basico')).not.toThrow()
    expect(peekPendingPlan()).toBeNull()
    expect(takePendingPlan()).toBeNull()
    expect(() => clearPendingPlan()).not.toThrow()
  })
})
