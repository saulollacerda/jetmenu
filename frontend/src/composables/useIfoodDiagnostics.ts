import { ref } from 'vue'
import { ifoodDiagnosticsService } from '@/services/ifoodDiagnosticsService'

// Module-level state: UISidebar decide se mostra a entrada e abre a janela, App.vue a
// renderiza. Mesmo padrão do useSidebar — sem prop drilling nem store para dois booleanos.
const isOpen = ref(false)
const isAllowed = ref(false)

export function useIfoodDiagnostics() {
  /**
   * Consulta a whitelist. Chamado no mount da sidebar, que vive uma vez por sessão do app —
   * na prática uma chamada só. Sem isso a entrada apareceria para todo mundo: o backend
   * recusaria, mas a UI já não deve oferecer.
   */
  const checkAccess = async () => {
    isAllowed.value = Boolean(await ifoodDiagnosticsService.isEnabled())
  }

  const open = () => {
    isOpen.value = true
  }
  const close = () => {
    isOpen.value = false
  }

  return { isOpen, isAllowed, checkAccess, open, close }
}
