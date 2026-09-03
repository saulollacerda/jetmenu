-- Webhook da Anota.AI: credencial por lojista e vínculo com a loja do lado deles.
--
-- webhook_secret: o "Token Externo" que o lojista cadastra no painel da Anota.AI. Chega no
-- header `authorization`, com o valor cru (sem "Bearer") e SEM assinatura — a captura em
-- produção confirmou que não existe header de HMAC. Esse segredo é, portanto, a única
-- credencial do endpoint. Guardado em texto (não hash) porque o lojista precisa poder
-- recopiá-lo na tela de integração, do mesmo jeito que a Stripe faz com o whsec_ e que a
-- anota_ai_api_key desta mesma tabela já é guardada.
--
-- anota_ai_merchant_id: o id da loja no Mongo da Anota.AI (ObjectId, não UUID), que vem em
-- `merchant.id` no corpo da entrega. Não confundir com a coluna merchant_id já existente,
-- que é a FK para merchants(id) — o nosso id. Sem essa terceira checagem, colar a URL e o
-- token da loja A no painel da loja B passa em todas as validações (URL e segredo são ambos
-- de A) e lança os pedidos de B na contabilidade de A.
ALTER TABLE anotaai_integration
    ADD COLUMN IF NOT EXISTS webhook_secret TEXT;

ALTER TABLE anotaai_integration
    ADD COLUMN IF NOT EXISTS anota_ai_merchant_id TEXT;
