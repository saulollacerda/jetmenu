/** Where a catalog record came from: created in JetMenu or imported. */
export type CatalogOrigin = 'JETMENU' | 'ANOTA_AI' | 'IFOOD'

export function catalogOriginLabel(origin?: CatalogOrigin | null): string {
  if (origin === 'ANOTA_AI') return 'Anota.AI'
  if (origin === 'IFOOD') return 'iFood'
  return 'JetMenu'
}

export function catalogOriginPillColor(origin?: CatalogOrigin | null): 'gray' | 'blue' | 'rose' {
  if (origin === 'ANOTA_AI') return 'blue'
  if (origin === 'IFOOD') return 'rose'
  return 'gray'
}

export interface CategoryRequest {
  name: string
}

export interface CategoryResponse {
  id: string
  name: string
  origin?: CatalogOrigin
}
