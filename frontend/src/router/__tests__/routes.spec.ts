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
