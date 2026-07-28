# iFood — Orders

MenuBank consome pedidos do iFood via **polling de eventos** (não webhook). São processados três
eventos do ciclo de vida: `CONFIRMED` (importa o pedido cedo), `CANCELLED` (cancela e tira dos ganhos)
e `CONCLUDED` (solidifica). Os demais (`PLACED`, `DISPATCHED`, `READY_TO_PICKUP`, etc.) são apenas
reconhecidos (acknowledged) para não voltarem na fila, sem nenhuma ação de negócio.

**Base URL:** `https://merchant-api.ifood.com.br/order/v1.0`

## Eventos processados e ciclo de vida

O pedido é importado já no `CONFIRMED` para dar visibilidade imediata ao lojista — o `CONCLUDED` só é
emitido pelo iFood muito depois (entrega confirmada, ou timeout de 4h/6h/13h dependendo do tipo de
loja/entrega, somado a `deliveryTimeInSeconds`). Como o `CONCLUDED` chega **automaticamente** mesmo que
o app nunca chame `confirm`/`startPreparation`/`dispatch`, ele funciona como *safety net*: qualquer
pedido cujo `CONFIRMED` tenha sido perdido (ou anterior a esta feature) é importado nesse momento.
Não há máquina de estados local; as ações de escrita disponíveis são as exigidas pela homologação
(`confirm`, `readyToPickup`, `dispatch`) — ver [Ações do ciclo de vida](#ações-do-ciclo-de-vida-escrita).
`startPreparation` não é implementado (não é exigido na homologação).

| Evento | Pedido já existe? | Ação |
|---|---|---|
| `CONFIRMED` | não | `GET /orders/{id}` → importa com `status = PENDING` |
| `CONFIRMED` | sim | ignora (dedup por `externalOrderId`) |
| `CONCLUDED` | sim | atualiza `status → PAID` (solidificação) — exceto se já `CANCELLED` |
| `CONCLUDED` | não | importa completo com `status = PAID` (fallback: `CONFIRMED` perdido/anterior à feature) |
| `CANCELLED` | sim | `status → CANCELLED` + notificação `ORDER_CANCELLED` (não precisa buscar detalhe) |
| `CANCELLED` | não | `GET /orders/{id}` → importa com `status = CANCELLED` (404 → loga e pula) |

Regras de transição:

- `CANCELLED` **sempre vence** (iFood é a fonte da verdade), inclusive sobre `PAID` — é isso que tira
  o pedido dos ganhos.
- `CONCLUDED` **nunca reverte** `CANCELLED`.
- Eventos repetidos são idempotentes (no-op): `CONFIRMED` de pedido existente é ignorado, `CANCELLED`
  de pedido já cancelado não gera nova notificação, `CONCLUDED` de pedido já `PAID` não muda nada.

### Cancelamento e ganhos

Dashboard e exportação (`DashboardService`, `ExportService`) só agregam pedidos com `status = PAID`.
O cancelamento remove o pedido das métricas pela simples troca de status — não existe lógica de
estorno. Quando um pedido **já importado** é cancelado, é criada uma notificação `ORDER_CANCELLED`
(`NotificationType`) para o lojista, incluindo `cancelReasonDescription` quando disponível no detalhe
do pedido.

## Ações do ciclo de vida (escrita)

Ações disparadas pelo **lojista** (nunca automáticas — não existe scheduler que confirme pedidos).
`IfoodOrderActionClient` → `IfoodOrderActionService` → `IfoodOrderActionController`, com o mesmo
padrão de retry em 401 do `IfoodOrderSyncService` (401 → `handleUnauthorized()` → repete uma vez).

| Ação | Chamada ao iFood | Status local depois | Quando |
|---|---|---|---|
| Confirmar | `POST /orders/{id}/confirm` | `PENDING` (inalterado) | SLA de **8 min** para `DELIVERY` e `TAKEOUT`, independente do `orderTiming` |
| Pronto para retirada | `PUT /orders/{id}/readyToPickup` | `READY` | Pedidos `TAKEOUT`, para notificar o cliente |
| Despachar | `PUT /orders/{id}/dispatch` | `DELIVERED` | Pedidos `DELIVERY` com entrega própria, ao sair para entrega |

> **Verbos HTTP não validados.** `PUT` para `readyToPickup`/`dispatch` vem do `HOMOLOGATION.md`;
> `confirm` usa `POST`. Não foi possível conferir com a doc oficial do iFood — validar contra a
> sandbox antes da homologação. Cada verbo está em uma única linha do `IfoodOrderActionClient`.

Guard rails (rejeitam antes de chamar o iFood): pedido inexistente para o merchant (404), pedido com
`origin != IFOOD`, sem `externalOrderId`, ou em status terminal (`CANCELLED`/`TEST`) → 409. Erros do
iFood: 404 → 404, 401 persistente → reautorização (409), demais `4xx` → 400 com o detalhe da API.
O status local só muda **depois** de o iFood aceitar a ação.

**Tipo do pedido.** A homologação amarra `readyToPickup` a `TAKEOUT` e `dispatch` a `DELIVERY`, e o
backend recusa a combinação errada com 409 (`Order.orderType` conhecido e contraditório). `orderType`
**nulo não bloqueia**: é o valor de todo pedido manual e de tudo importado antes da V27, e recusar
esses quebraria a base existente. `confirm` não depende do tipo.

**Retry em falha transitória.** Só o `confirm` repete (2 tentativas, 300 ms de intervalo) em `5xx` e
erro de rede: é a única ação com prazo, e um blip no sétimo minuto do SLA custa o pedido ao lojista.
`dispatch`/`readyToPickup` não repetem — sem prazo, o erro sobe na hora e o lojista tenta de novo.
Nenhuma ação repete em `4xx`.

### SLA de confirmação (8 minutos)

Regra: `deadline = Order.dateTime + 8 min` (`Order.dateTime` já é `createdAt` convertido para
`America/Sao_Paulo`). O backend expõe isso pronto em
`GET /api/integrations/ifood/orders/{orderId}/confirmation-window` →
`{orderId, createdAt, deadline, remainingSeconds, expired}`, com `remainingSeconds` travado em zero
quando a janela acaba. A UI pode chamar uma vez e contar regressivamente a partir do `deadline`.

### Endpoints REST expostos ao frontend

| Método | Path | Resposta |
|---|---|---|
| POST | `/api/integrations/ifood/orders/{orderId}/confirm` | `200` `{orderId, externalOrderId, status}` |
| POST | `/api/integrations/ifood/orders/{orderId}/ready-to-pickup` | `200` `{orderId, externalOrderId, status}` |
| POST | `/api/integrations/ifood/orders/{orderId}/dispatch` | `200` `{orderId, externalOrderId, status}` |
| GET | `/api/integrations/ifood/orders/{orderId}/confirmation-window` | `200` `{orderId, createdAt, deadline, remainingSeconds, expired}` |

Nenhuma requisição tem corpo. Erros são `ProblemDetail` com `detail` em pt-BR — inclusive
`409 "Só é possível marcar como pronto para retirada um pedido de retirada."` e
`409 "Só é possível despachar um pedido de entrega."` para o tipo errado.

## Polling de eventos

```
GET /events:polling
Authorization: Bearer {accessToken}
Header: x-polling-merchants: {ifoodMerchantId1},{ifoodMerchantId2},...
```

Resposta (200 OK) — array puro, sem wrapper `events`:

```json
[
  {
    "id": "evt_123",
    "code": "CON",
    "fullCode": "CONCLUDED",
    "orderId": "ord_456",
    "merchantId": "df8eb84a-...",
    "createdAt": "2024-04-25T19:00:00Z",
    "salesChannel": "IFOOD"
  }
]
```

> `code` vem **abreviado** (ex.: `CON` para concluído, `CFM` para confirmado, `CAN` para cancelado,
> `PLC` para `PLACED`) — o filtro de negócio deve comparar contra `fullCode`, não `code`. Confirmado
> via chamada real à API em 2026-07-04; a doc anterior (`GET /orders:polling`, wrapper
> `{"events":[...]}`, `code` por extenso) estava desatualizada e retorna
> `404 {"message":"no Route matched with those values"}` (erro de gateway do Kong, não de negócio).

> **`fullCode` com e sem prefixo `ORDER_`:** os payloads de referência do estilo webhook mostram
> `ORDER_CONFIRMED`/`ORDER_CANCELLED` com `metadata` rica (itens, cliente, `cancelReason`), mas o
> polling real retorna eventos enxutos com `fullCode` sem prefixo (`CONCLUDED` verificado em
> 2026-07-04). O filtro compara `fullCode` case-insensitive aceitando **as duas formas**
> (`CONFIRMED`/`ORDER_CONFIRMED`, `CANCELLED`/`ORDER_CANCELLED`, `CONCLUDED`/`ORDER_CONCLUDED`).
> O `metadata` do evento **não é usado** — quando o detalhe é necessário, vem sempre de
> `GET /orders/{id}`; para `CANCELLED` de pedido já existente basta o `orderId`.

Chamado a cada 30s (limite de rate do iFood). Um único merchant-app token cobre todos os `ifoodMerchantId`
autorizados — não é necessário chamar por merchant.

## Reconhecimento de eventos (acknowledgment)

```
POST /events/acknowledgment
Authorization: Bearer {accessToken}
Content-Type: application/json

[{ "id": "evt_123" }, { "id": "evt_124" }]
```

Resposta: `202 Accepted`. Corpo é array puro de objetos `{"id": ...}` — não o wrapper `{"acknowledgedEventIds": [...]}`
da doc anterior. **Todo** evento recebido no polling deve ser reconhecido, mesmo os ignorados (fora de
`CONFIRMED`/`CANCELLED`/`CONCLUDED`) — senão o iFood reenvia os mesmos eventos no próximo polling.

## Detalhe do pedido — `GET /orders/{id}`

```
GET /orders/{id}
Authorization: Bearer {token}
```

Retorna `200 OK` com a estrutura completa do pedido. `404` se o detalhe ainda não estiver disponível
ou se o pedido tiver mais de 7 dias — nesses casos, logar e pular (sem retry infinito). No `CONFIRMED`
um 404 é mais plausível (início do ciclo de vida); não há problema, o fallback do `CONCLUDED` importa
o pedido depois.

### Informações gerais

| Campo | Tipo | Descrição |
|---|---|---|
| `id` | uuid | Identificador único do pedido |
| `displayId` | string | ID amigável para exibição |
| `orderType` | enum | `DELIVERY`, `TAKEOUT`, `DINE_IN` |
| `orderTiming` | enum | `IMMEDIATE` ou `SCHEDULED` |
| `salesChannel` | string | `IFOOD`, `DIGITAL_CATALOG`, `POS`, `TOTEM`, `IFOOD_SHOP`, `GROCERY_WHITELABEL` |
| `category` | string | `FOOD`, `GROCERY`, `ANOTAI`, `FOOD_SELF_SERVICE` — **MenuBank só processa `category == "FOOD"`**; demais categorias são ignoradas na importação |
| `createdAt` | date | Data/hora de criação (UTC) |
| `preparationStartDateTime` | date | Horário recomendado para início do preparo |
| `isTest` | boolean | Pedido de teste — **importado com `status = TEST`** (independente do evento de origem). `TEST` é terminal: `CONCLUDED`/`CANCELLED` não o alteram, então o pedido nunca entra nos ganhos (dashboard/exportação só agregam `PAID`) nem gera notificação de cancelamento |
| `extraInfo` | string | Informações adicionais livres (ex.: `"Pago Online. NÃO LEVAR MÁQUINA"`) — **persistido em `orders.extra_info` para uso futuro** (ex.: impressão de comanda), sem consumo funcional na v1 |

### `merchant`

| Campo | Tipo | Descrição |
|---|---|---|
| `id` | uuid | Identificador único da loja no iFood — usado para rotear o pedido ao `Merchant` local via `Merchant.ifoodMerchantId` |
| `name` | string | Nome da loja |

### `customer`

| Campo | Tipo | Descrição |
|---|---|---|
| `id` | uuid | Identificador único do cliente |
| `name` | string | Nome do cliente |
| `documentNumber` | string | CPF/documento (opcional, só para NF) |
| `phone.number` | string | Telefone do cliente — **opcional, some 3h após a entrega**; alguns pedidos podem não trazer mesmo antes disso. Frequentemente vem o **0800 da central do iFood** (compartilhado entre clientes) em vez do número real — números 0800 **não deduplicam nem são persistidos** |
| `phone.localizer` | string | Código para contato via 0800 |

> **Resolução de `Customer`:** 1º por `customer.id` do iFood (`customers.external_id`), 2º por telefone
> (exceto 0800). Sem match, cria cliente novo com `externalId` preenchido.

> **Vantagem do import no `CONFIRMED`:** como o telefone some 3h após a entrega, importar no início do
> ciclo aumenta a chance de o número real estar presente — dedup de `Customer` mais confiável do que
> no `CONCLUDED`.

### `items[]`

| Campo | Tipo | Descrição |
|---|---|---|
| `externalCode` | string | Código PDV (opcional) — 1ª tentativa de match de produto |
| `name` | string | Nome do item — usado no match por **nome canônico** contra `products.canonical_name` quando não há `externalCode`. Item sem match é pulado (não vira `OrderItem`) e gera notificação `MISSING_PRODUCT` — o pedido é importado mesmo assim, com `totalValue` cheio e custo parcial |
| `quantity` | double | Quantidade |
| `unitPrice` | double | Preço unitário |
| `optionsPrice` | double | Preço dos complementos |
| `totalPrice` | double | Preço total (item + complementos) |
| `options[]` | array | Complementos — resolvidos contra `ingredients` por **nome canônico** (`IngredientNameNormalizer`), igual ao fluxo já usado para Anota.AI; sem match → notificação `MISSING_INGREDIENT` e o complemento é pulado (o subItem cru **não** é persistido — não existe tabela `order_item_unmatched_subitems`; backfill automático ao cadastrar o ingrediente não cobre pedidos já importados) |
| `options[].customizations[]` | array | 3º nível de customização — **fora do escopo v1**, não processado |

### `total`

| Campo | Tipo | Descrição |
|---|---|---|
| `subTotal` | double | Somatório dos itens |
| `deliveryFee` | double | Taxa de entrega → `Order.deliveryFee` |
| `orderAmount` | double | Total do pedido (`subTotal + deliveryFee + additionalFees - benefits`) → `Order.totalValue` |

### `payments`

Exigido pela homologação do módulo Order (bandeira do cartão e troco).

| Campo | Tipo | Descrição |
|---|---|---|
| `prepaid` | double | Valor já pago online → `Order.paymentPrepaidAmount` |
| `pending` | double | Valor a receber na entrega → `Order.paymentPendingAmount` |
| `methods[].value` | double | Valor deste meio de pagamento |
| `methods[].currency` | string | Moeda (`BRL`) |
| `methods[].method` | string | `CREDIT`, `DEBIT`, `CASH`, `MEAL_VOUCHER`, `PIX`, ... |
| `methods[].type` | string | `ONLINE` (já pago) ou `OFFLINE` (recebido pela loja) |
| `methods[].card.brand` | string | Bandeira do cartão — **exibida na comanda** |
| `methods[].cash.changeFor` | double | Nota que o cliente vai entregar; o troco é `changeFor − value`, calculado no backend e exposto como `changeAmount` |

Um pedido pode ter **mais de um meio de pagamento**, por isso cada um vira uma linha em
`order_payment_methods` (child table de `orders`), não colunas em `orders`.

### `benefits[]`

| Campo | Tipo | Descrição |
|---|---|---|
| `value` | double | Valor do cupom/desconto |
| `target` | string | `CART`, `DELIVERY_FEE`, `ITEM`, ... |
| `sponsorshipValues[].name` | string | `IFOOD` ou `MERCHANT` — **quem banca o desconto** |
| `sponsorshipValues[].value` | double | Parcela bancada por esse patrocinador |

Vários benefícios são **somados** em `Order.discountTotal`, e o rateio em
`Order.discountIfoodValue` / `Order.discountMerchantValue`. Patrocinador desconhecido entra no total
mas não no rateio (fica logado). **Nada disso é deduzido de nenhum valor** — o desconto já está
embutido no `total.orderAmount` e é gravado apenas para exibição.

### `delivery` / `takeout`

| Campo | Destino |
|---|---|
| `delivery.mode` | `Order.deliveryMode` |
| `delivery.deliveredBy` | `Order.deliveredBy` (`IFOOD` ou `MERCHANT`) |
| `delivery.deliveryDateTime` | `Order.deliveryDateTime` (UTC → `America/Sao_Paulo`) |
| `delivery.observations` | `Order.deliveryObservations` — observações de entrega na comanda |
| `delivery.pickupCode` | `Order.pickupCode` — código de coleta |
| `takeout.mode` | `Order.takeoutMode` |
| `takeout.takeoutDateTime` | `Order.takeoutDateTime` (UTC → `America/Sao_Paulo`) |

`delivery.deliveryAddress` **não é persistido** — MenuBank não guarda endereço.

### Campos fora do escopo

`additionalFees`, `deliveryAddress`, `dineIn` (parseado, não persistido) e `picking` — presentes na
resposta da API, mas não persistidos. Revisitar se algum dashboard precisar desses dados no futuro.

## Mapeamento → `Order`/`OrderItem`/`OrderItemExtraIngredient`

| Campo iFood | Destino MenuBank |
|---|---|
| `id` | `Order.externalOrderId` |
| `displayId` | `Order.displayId` |
| `orderType` / `orderTiming` | `Order.orderType` / `Order.orderTiming` (valor desconhecido → `null`) |
| `customer.documentNumber` | `Order.customerDocument` (CPF/CNPJ da NF-e) |
| `items[].observations` | `OrderItem.observations` |
| `payments` | `Order.paymentPrepaidAmount`/`paymentPendingAmount` + `OrderPaymentMethod` (1:N) |
| `benefits[]` | `Order.discountTotal`/`discountIfoodValue`/`discountMerchantValue` |
| `delivery` / `takeout` | Colunas descritivas em `Order` (ver acima) |
| `createdAt` (UTC) | `Order.dateTime` — parse `OffsetDateTime` → converte para `America/Sao_Paulo` → `LocalDateTime` |
| `customer.id` (1º) ou `customer.phone.number` não-0800 (2º) | Resolução de `Customer` — `customer.id` → `customers.external_id` |
| `items[].externalCode` ou `items[].name` | Resolução de `Product` — `externalCode` primeiro, nome canônico como fallback |
| `items[].quantity` / `unitPrice` | `OrderItem.quantity` / `unitPrice` |
| — (ficha técnica local) | `OrderItem.unitCost` via `ProductCostCalculator` |
| `items[].options[]` | `OrderItemExtraIngredient` (match por nome canônico) |
| `total.orderAmount` | `Order.totalValue` |
| `total.deliveryFee` | `Order.deliveryFee` |
| `extraInfo` | `Order.extraInfo` |
| `merchant.id` | Roteamento — resolve `Order.merchant` local, não persistido como está |
| — | `Order.fee = null` (payments é lista, não mapeia 1:1 para `Fee`) |
| — | `Order.origin = OrderOrigin.IFOOD` |
| evento que originou o import | `Order.status` — `CONFIRMED → PENDING`, `CONCLUDED → PAID`, `CANCELLED → CANCELLED`; `isTest = true` sobrepõe tudo com `TEST` |

> A entidade `Order` não tem campo de motivo/timestamp de cancelamento — o cancelamento é apenas a
> troca de `status`; o `cancelReasonDescription` (quando disponível) vai no texto da notificação
> `ORDER_CANCELLED`.
