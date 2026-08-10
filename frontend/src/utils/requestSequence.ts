/**
 * Guarda de corrida para listagens paginadas com busca incremental.
 *
 * Cada chamada de `next()` invalida as anteriores. Uma resposta que chega depois de
 * uma requisição mais nova ter sido disparada é "stale" e deve ser descartada — sem
 * isso, respostas fora de ordem sobrescrevem a lista (e o termo de busca) com o
 * resultado de um termo que o usuário já não está mais digitando.
 */
export function createRequestSequence() {
  let latest = 0

  return {
    /** Registra uma nova requisição e devolve o token que a identifica. */
    next(): number {
      latest += 1
      return latest
    },
    /** `true` quando outra requisição foi disparada depois desta. */
    isStale(token: number): boolean {
      return token !== latest
    },
  }
}
