// Design tokens — mirrors jetmenu handoff (ui.jsx UI constants).
export const UI = {
  bg: '#f6f7f9',
  bgSoft: '#fafbfc',
  panel: '#ffffff',
  border: '#e8eaee',
  borderSub: '#eef0f3',
  text: '#0f172a',
  textSub: '#64748b',
  textMute: '#94a3b8',
  navBg: '#0c1626',
  navBg2: '#0a1322',
  navText: '#cbd5e1',
  navActive: '#2563eb',
  emerald: '#10b981',
  emeraldBg: '#ecfdf5',
  emerald2: '#059669',
  blue: '#2563eb',
  blueBg: '#eff6ff',
  blue2: '#1d4ed8',
  amber: '#f59e0b',
  amberBg: '#fffbeb',
  amber2: '#92400e',
  rose: '#e11d48',
  roseBg: '#fff1f2',
  rose2: '#9f1239',
  violet: '#7c3aed',
  violetBg: '#f3e8ff',
  font: "'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif",
} as const

// Brazilian formatters.
export function brl(n: number, opts: { decimals?: number; prefix?: string } = {}): string {
  const { decimals = 2, prefix = 'R$ ' } = opts
  const maxDecimals = n !== 0 && Math.abs(n) < 0.01 ? 4 : decimals
  return (
    prefix +
    n.toLocaleString('pt-BR', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: maxDecimals,
    })
  )
}
export function pct(n: number, d = 1): string {
  return (n >= 0 ? '+' : '') + n.toFixed(d).replace('.', ',') + '%'
}
export function num(n: number): string {
  return n.toLocaleString('pt-BR')
}
