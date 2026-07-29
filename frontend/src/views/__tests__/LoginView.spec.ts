import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, enableAutoUnmount, RouterLinkStub } from '@vue/test-utils'
import LoginView from '@/views/LoginView.vue'

const routeState = vi.hoisted(() => ({ query: {} as Record<string, string> }))
const pushMock = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: pushMock }),
}))

const authState = {
  error: null as string | null,
  loading: false,
  login: vi.fn(),
}

vi.mock('@/stores/authStore', () => ({ useAuthStore: () => authState }))

enableAutoUnmount(afterEach)

const GLOBAL = { global: { stubs: { RouterLink: RouterLinkStub } } }

beforeEach(() => {
  vi.clearAllMocks()
  authState.error = null
  authState.loading = false
  routeState.query = {}
})

async function submit(wrapper: ReturnType<typeof mount>) {
  const inputs = wrapper.findAll('input')
  await inputs[0]!.setValue('a@b.com')
  await inputs[1]!.setValue('senha123')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('LoginView', () => {
  it('"Esqueceu a senha?" é um link para /esqueci-senha', () => {
    const wrapper = mount(LoginView, GLOBAL)

    const links = wrapper.findAllComponents(RouterLinkStub)
    const forgot = links.find((l) => l.props('to') === '/esqueci-senha')
    expect(forgot).toBeDefined()
    expect(forgot!.text()).toContain('Esqueceu a senha?')
  })

  it('não exibe a opção sem efeito "Manter conectado"', () => {
    const wrapper = mount(LoginView, GLOBAL)

    expect(wrapper.text()).not.toContain('Manter conectado')
  })

  it('vai para o dashboard após entrar', async () => {
    const wrapper = mount(LoginView, GLOBAL)

    await submit(wrapper)

    expect(pushMock).toHaveBeenCalledWith('/dashboard')
  })

  it('retoma o checkout quando o login carrega um plano vindo da landing page', async () => {
    routeState.query = { plan: 'basico' }
    const wrapper = mount(LoginView, GLOBAL)

    await submit(wrapper)

    expect(pushMock).toHaveBeenCalledWith({ path: '/checkout', query: { plan: 'basico' } })
  })
})
