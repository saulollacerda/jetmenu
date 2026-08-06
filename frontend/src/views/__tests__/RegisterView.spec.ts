import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  mount,
  flushPromises,
  enableAutoUnmount,
  RouterLinkStub,
  type VueWrapper,
} from '@vue/test-utils'
import RegisterView from '@/views/RegisterView.vue'
import { savePendingPlan, peekPendingPlan } from '@/lib/pendingPlan'

const routeState = vi.hoisted(() => ({ query: {} as Record<string, string> }))
const pushMock = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: pushMock }),
}))

const registerFn = vi.fn()
const authState = {
  error: null as string | null,
  loading: false,
  awaitingEmailConfirmation: false,
  register: registerFn,
}

vi.mock('@/stores/authStore', () => ({ useAuthStore: () => authState }))

enableAutoUnmount(afterEach)

const GLOBAL = { global: { stubs: { RouterLink: RouterLinkStub } } }

// Well-known valid test CNPJ (check digits pass).
const VALID_CNPJ = '11.222.333/0001-81'

function mountView() {
  return mount(RegisterView, GLOBAL)
}

async function fillForm(wrapper: VueWrapper, overrides: Record<string, string> = {}) {
  const values: Record<string, string> = {
    merchantName: 'Loja',
    cnpj: VALID_CNPJ,
    email: 'a@b.com',
    password: 'senha123',
    confirmPassword: 'senha123',
    phone: '11999999999',
    ...overrides,
  }
  for (const [id, value] of Object.entries(values)) {
    await wrapper.find(`#${id}`).setValue(value)
  }
}

async function acceptTerms(wrapper: VueWrapper) {
  const termsLabel = wrapper.findAll('label').find((l) => l.text().includes('Aceito os'))
  expect(termsLabel).toBeDefined()
  await termsLabel!.trigger('click')
}

beforeEach(() => {
  vi.clearAllMocks()
  authState.error = null
  authState.loading = false
  authState.awaitingEmailConfirmation = false
  routeState.query = {}
})

describe('RegisterView', () => {
  it('termos desmarcados por padrão: submit sem aceite é bloqueado', async () => {
    const wrapper = mountView()
    await fillForm(wrapper)

    await wrapper.find('form').trigger('submit')

    expect(registerFn).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('aceitar os termos')
  })

  it('senha com menos de 6 caracteres é bloqueada no frontend', async () => {
    const wrapper = mountView()
    await acceptTerms(wrapper)
    await fillForm(wrapper, { password: 'abc12', confirmPassword: 'abc12' })

    await wrapper.find('form').trigger('submit')

    expect(registerFn).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('no mínimo 6 caracteres')
  })

  it('senhas divergentes são bloqueadas no frontend', async () => {
    const wrapper = mountView()
    await acceptTerms(wrapper)
    await fillForm(wrapper, { confirmPassword: 'outraSenha' })

    await wrapper.find('form').trigger('submit')

    expect(registerFn).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('As senhas não conferem')
  })

  it('CNPJ inválido é bloqueado no frontend', async () => {
    const wrapper = mountView()
    await acceptTerms(wrapper)
    await fillForm(wrapper, { cnpj: '11.111.111/1111-11' })

    await wrapper.find('form').trigger('submit')

    expect(registerFn).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('CNPJ inválido')
  })

  it('formulário válido com termos aceitos chama register com os dados preenchidos', async () => {
    registerFn.mockResolvedValue(undefined)
    const wrapper = mountView()
    await acceptTerms(wrapper)
    await fillForm(wrapper)

    await wrapper.find('form').trigger('submit')

    expect(registerFn).toHaveBeenCalledOnce()
    expect(registerFn).toHaveBeenCalledWith({
      merchantName: 'Loja',
      cnpj: VALID_CNPJ,
      email: 'a@b.com',
      password: 'senha123',
      confirmPassword: 'senha123',
      phone: '11999999999',
    })
  })

  it('mostra o aviso de confirmação quando o cadastro aguarda o email', () => {
    authState.awaitingEmailConfirmation = true
    const wrapper = mountView()

    expect(wrapper.text()).toContain('email de confirmação')
  })

  it('retoma o checkout quando a conta já nasce com sessão e há um plano na query', async () => {
    routeState.query = { plan: 'basico' }
    registerFn.mockResolvedValue(undefined)
    const wrapper = mountView()
    await acceptTerms(wrapper)
    await fillForm(wrapper)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(pushMock).toHaveBeenCalledWith({ path: '/checkout', query: { plan: 'basico' } })
  })

  it('não redireciona quando o cadastro ainda aguarda confirmação de email', async () => {
    routeState.query = { plan: 'basico' }
    registerFn.mockImplementation(async () => {
      authState.awaitingEmailConfirmation = true
    })
    const wrapper = mountView()
    await acceptTerms(wrapper)
    await fillForm(wrapper)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(pushMock).not.toHaveBeenCalled()
  })

  it('o link de login carrega o plano escolhido para retomar o checkout', async () => {
    routeState.query = { plan: 'basico' }
    authState.awaitingEmailConfirmation = true
    const wrapper = mountView()

    const links = wrapper.findAllComponents(RouterLinkStub)
    expect(
      links.some((l) => JSON.stringify(l.props('to')).includes('"plan":"basico"')),
    ).toBe(true)
  })

  it('inputs obrigatórios têm required e a senha tem minlength', () => {
    const wrapper = mountView()

    for (const id of ['merchantName', 'cnpj', 'email', 'password', 'confirmPassword']) {
      expect(wrapper.find(`#${id}`).attributes('required'), `#${id} required`).toBeDefined()
    }
    expect(wrapper.find('#password').attributes('minlength')).toBe('6')
  })
})

// The global localStorage in this environment is an unusable stub.
function installStorage() {
  const map = new Map<string, string>()
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: (k: string) => map.get(k) ?? null,
      setItem: (k: string, v: string) => void map.set(k, v),
      removeItem: (k: string) => void map.delete(k),
    } as unknown as Storage,
  })
}

// The submit handler used to do nothing after a successful register, on the assumption
// that email confirmation is always required. With the local dev provider a session comes
// back immediately, which left the merchant logged in but sitting on the form.
describe('RegisterView — navegação após criar a conta', () => {
  beforeEach(() => {
    installStorage()
    vi.clearAllMocks()
    authState.awaitingEmailConfirmation = false
    routeState.query = {}
    registerFn.mockReset()
  })

  async function completeSignup(wrapper: VueWrapper) {
    await fillForm(wrapper)
    await acceptTerms(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
  }

  it('com sessão e sem plano, vai para o painel', async () => {
    const wrapper = mountView()
    await completeSignup(wrapper)

    expect(registerFn).toHaveBeenCalled()
    expect(pushMock).toHaveBeenCalledWith('/dashboard')
  })

  it('com sessão e plano na query, vai para o checkout com o plano', async () => {
    routeState.query = { plan: 'basico' }

    const wrapper = mountView()
    await completeSignup(wrapper)

    expect(pushMock).toHaveBeenCalledWith({ path: '/checkout', query: { plan: 'basico' } })
  })

  it('com sessão e plano apenas guardado, vai para o checkout', async () => {
    savePendingPlan('basico')

    const wrapper = mountView()
    await completeSignup(wrapper)

    expect(pushMock).toHaveBeenCalledWith('/checkout')
  })

  it('sem sessão (confirmação de email ligada), permanece na tela', async () => {
    // register() resolving without a session is what turns the flag on.
    registerFn.mockImplementation(() => {
      authState.awaitingEmailConfirmation = true
    })

    const wrapper = mountView()
    await completeSignup(wrapper)

    expect(registerFn).toHaveBeenCalled()
    expect(pushMock).not.toHaveBeenCalled()
  })

  it('guarda o plano da query para sobreviver à confirmação de email', async () => {
    routeState.query = { plan: 'basico' }
    registerFn.mockImplementation(() => {
      authState.awaitingEmailConfirmation = true
    })

    const wrapper = mountView()
    await completeSignup(wrapper)

    expect(pushMock).not.toHaveBeenCalled()
    expect(peekPendingPlan()).toBe('basico')
  })
})
