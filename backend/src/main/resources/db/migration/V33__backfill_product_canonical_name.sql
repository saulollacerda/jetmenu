-- V33 — Backfill products.canonical_name
--
-- Até aqui só o cadastro manual (ProductService) preenchia canonical_name; produtos
-- importados do catálogo da Anota.AI nasciam com a coluna nula. Isso passou despercebido
-- enquanto o único match de pedido era por internalId — mas os pedidos do canal iFood
-- chegam com internalId vazio e dependem exclusivamente do nome canônico.
--
-- Reproduz em SQL o mesmo IngredientNameNormalizer.normalize() do Java:
-- trim → remoção de acentos → lowercase → colapso de espaços internos.
-- unaccent não é usado (exige extensão); translate cobre os acentos do pt-BR.

update products
set canonical_name = regexp_replace(
        lower(translate(
            btrim(name),
            'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
            'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'
        )),
        '\s+', ' ', 'g'
    )
where canonical_name is null
  and name is not null;
