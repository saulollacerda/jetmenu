import { describe, it, expect, afterEach } from 'vitest'
import { mount, enableAutoUnmount, RouterLinkStub } from '@vue/test-utils'
import LandingView from '@/views/LandingView.vue'

enableAutoUnmount(afterEach)

const GLOBAL = { global: { stubs: { RouterLink: RouterLinkStub } } }

describe('LandingView — pricing', () => {
  it('mostra apenas o plano Básico de R$ 50', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const cards = wrapper.findAll('.lp-pricing-card')
    expect(cards).toHaveLength(1)
    expect(cards[0]!.text()).toContain('Básico')
    expect(cards[0]!.text()).toContain('R$ 50')
  })

  it('não mostra os planos fictícios (Inicial, Profissional, Multi-loja)', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const text = wrapper.text()
    expect(text).not.toContain('R$ 79')
    expect(text).not.toContain('R$ 149')
    expect(text).not.toContain('R$ 299')
    expect(text).not.toContain('Profissional')
    expect(text).not.toContain('Multi-loja')
  })
})

describe('LandingView — apresentação pura (sem login/cadastro e sem teste grátis)', () => {
  it('não contém nenhum link para /login ou /register', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const targets = wrapper
      .findAllComponents(RouterLinkStub)
      .map((link) => link.props('to'))
    expect(targets).not.toContain('/login')
    expect(targets).not.toContain('/register')

    const hrefs = wrapper.findAll('a').map((a) => a.attributes('href') ?? '')
    expect(hrefs).not.toContain('/login')
    expect(hrefs).not.toContain('/register')
  })

  it('direciona os CTAs para a página de planos', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const targets = wrapper
      .findAllComponents(RouterLinkStub)
      .map((link) => link.props('to'))
    expect(targets).toContain('/planos')
  })

  it('não menciona teste grátis nem "sem cartão"', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const text = wrapper.text()
    expect(text).not.toContain('sem cartão')
    expect(text).not.toContain('Sem cartão')
    expect(text).not.toContain('grátis')
    expect(text).not.toContain('7 dias')
  })
})

describe('LandingView — estatísticas', () => {
  it('não exibe a estatística de "350 lojas"', () => {
    const wrapper = mount(LandingView, GLOBAL)

    expect(wrapper.text()).not.toContain('350')
    expect(wrapper.text()).not.toContain('lojas usam o jetmenu')
  })
})

describe('LandingView — depoimento', () => {
  it('atribui a citação a Lucas Almeda, dono do Açaí Goat', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const quote = wrapper.find('.lp-quote').text()
    expect(quote).toContain('Lucas Almeda')
    expect(quote).toContain('Dono do Açaí Goat')
    expect(quote).not.toContain('Renata Vasconcelos')
  })
})

describe('LandingView — layout responsivo', () => {
  it('não usa grid-template-columns inline nos passos de "Como funciona"', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const steps = wrapper.findAll('.lp-how-step')
    expect(steps).toHaveLength(3)
    for (const step of steps) {
      expect(step.attributes('style') ?? '').not.toContain('grid-template-columns')
    }
  })

  it('marca o passo com visual à esquerda com a classe modificadora', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const steps = wrapper.findAll('.lp-how-step')
    expect(steps[1]!.classes()).toContain('lp-how-step--flip')
    expect(steps[0]!.classes()).not.toContain('lp-how-step--flip')
    expect(steps[2]!.classes()).not.toContain('lp-how-step--flip')
  })

  it('não usa "order" inline dentro dos passos', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const inlineOrdered = wrapper
      .findAll('.lp-how-step [style]')
      .filter((el) => /(^|;)\s*order\s*:/.test(el.attributes('style') ?? ''))
    expect(inlineOrdered).toHaveLength(0)
  })

  it('envolve o preview do dashboard em .lp-hero-preview sem estilos inline', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const preview = wrapper.find('.lp-hero-preview')
    expect(preview.exists()).toBe(true)
    expect(preview.attributes('style')).toBeUndefined()
    expect(preview.find('.lp-browser-frame').exists()).toBe(true)
  })

  it('não define font-size inline no título do FAQ', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const h2 = wrapper.find('#faq .lp-section-h2')
    expect(h2.exists()).toBe(true)
    expect(h2.attributes('style') ?? '').not.toContain('font-size')
  })

  it('envolve a faixa de CTA em .lp-cta-wrap sem padding inline', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const wrap = wrapper.find('.lp-cta-wrap')
    expect(wrap.exists()).toBe(true)
    expect(wrap.attributes('style')).toBeUndefined()
    expect(wrap.find('.lp-cta-band').exists()).toBe(true)
  })
})

describe('LandingView — ícones', () => {
  it('renderiza os ícones como SVG (sem template strings que dependem do compilador em runtime)', () => {
    const wrapper = mount(LandingView, GLOBAL)

    expect(wrapper.find('.lp-hero-pill svg').exists()).toBe(true)
    expect(wrapper.find('.lp-hero-bullets svg').exists()).toBe(true)
    expect(wrapper.find('.lp-problem-icon svg').exists()).toBe(true)
    expect(wrapper.find('.lp-feat-icon svg').exists()).toBe(true)
    expect(wrapper.find('.lp-channel-icon svg').exists()).toBe(true)
    expect(wrapper.find('.lp-pricing-features svg').exists()).toBe(true)
  })

  it('aplica tamanho e cor recebidos por props', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const bulletIcon = wrapper.find('.lp-hero-bullets svg')
    expect(bulletIcon.attributes('width')).toBe('14')
    expect(bulletIcon.attributes('stroke')).toBe('#059669')
  })
})

describe('LandingView — rodapé', () => {
  it('exibe o CNPJ correto e o país sem cidade', () => {
    const wrapper = mount(LandingView, GLOBAL)

    const footer = wrapper.find('.lp-footer-bottom').text()
    expect(footer).toContain('67.595.605/0001-43')
    expect(footer).not.toContain('00.000.000/0001-00')
    expect(footer).not.toContain('São Paulo')
  })
})
