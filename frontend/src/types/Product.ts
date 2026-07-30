import type { CatalogOrigin } from './Category'

export type ProductStatus = 'ACTIVE' | 'INACTIVE'
export type IncludeKind = 'INGREDIENT' | 'PACKAGING'

export interface ProductRequest {
  name: string
  price: number
  categoryId: string
  /**
   * Margem ideal (%) do produto, 0..100. `null` = não acompanhada.
   *
   * O backend atribui este campo incondicionalmente no update: um payload que o omite
   * apaga a margem ideal gravada. Sempre envie o valor atual quando o lojista não o
   * alterou, e `null` quando ele esvaziou o campo de propósito.
   */
  targetMarginPct?: number | null
}

export interface ProductResponse {
  id: string
  name: string
  price: number
  status: ProductStatus
  categoryId: string
  categoryName: string
  /** Margem ideal (%) cadastrada no produto. Null quando o lojista não a definiu. */
  targetMarginPct?: number | null
  origin?: CatalogOrigin
}

/**
 * Item da ficha técnica do produto (tabela `includes`).
 * Sem FK para `ingredients`: name/cost são armazenados direto, por produto.
 */
export interface IncludeRequest {
  name: string
  cost: number
  /** Opcional. Backend assume 1 quando ausente. */
  quantity?: number
  kind?: IncludeKind
}

export interface IncludeResponse {
  id: string
  productId: string
  name: string
  cost: number
  quantity: number
  totalCost: number
  kind: IncludeKind
}
