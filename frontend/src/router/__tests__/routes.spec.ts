import { describe, it, expect } from 'vitest'
import router from '@/router'

/**
 * The landing page moved out of this app into its own Next.js project served at
 * jetmenu.com.br. This app is served at app.jetmenu.com.br and no longer owns a
 * marketing page, so `/home` must not resolve and `/` must land on the product.
 */
describe('router — a landing page saiu do app', () => {
  it('não tem mais rota /home', () => {
    expect(router.resolve('/home').matched).toHaveLength(0)
  })

  it('não tem mais rota chamada landing', () => {
    const names = router.getRoutes().map((route) => route.name)

    expect(names).not.toContain('landing')
  })

  it('a raiz redireciona para o dashboard', () => {
    const root = router.getRoutes().find((route) => route.path === '/')

    expect(root?.redirect).toBe('/dashboard')
  })
})

/**
 * Pricing now lives only on the landing page. Its CTA sends the visitor to
 * /checkout?plan=<slug>, which is the app's only entry point into payment.
 */
describe('router — planos saíram do app, checkout entrou', () => {
  it('não tem mais rota /planos', () => {
    expect(router.resolve('/planos').matched).toHaveLength(0)
    expect(router.getRoutes().map((route) => route.name)).not.toContain('plans')
  })

  it('expõe /checkout como rota pública acessível também para autenticados', () => {
    const checkout = router.resolve('/checkout?plan=basico')

    expect(checkout.name).toBe('checkout')
    expect(checkout.matched).toHaveLength(1)
    expect(checkout.meta).toMatchObject({ public: true, allowAuthenticated: true })
    expect(checkout.query.plan).toBe('basico')
  })
})
