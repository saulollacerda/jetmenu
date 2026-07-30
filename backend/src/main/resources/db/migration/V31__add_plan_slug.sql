-- V31 — Stable slug on plans, decoupling a plan's identity from its display name
--
-- The Stripe Price of a plan is read from configuration keyed by slug
-- (stripe.price-ids.<slug> / STRIPE_PRICE_<SLUG>). Until now that slug was derived from
-- plans.name on every checkout, which made a display string load-bearing: renaming
-- "Básico" to anything else — a marketing decision, applied with a plain UPDATE — moved
-- the configuration key and broke checkout for that plan, with no error until a merchant
-- clicked "Assinar" and got a 503.
--
-- After this migration the slug is stored once and frozen. plans.name is free to change.
--
-- ⚠️ The UPDATE below is the step an EMPTY database cannot validate: on an empty table it
-- matches zero rows and a broken expression looks exactly like a correct one, then the
-- NOT NULL right after it fails on the first real deploy. Validate against a database that
-- actually contains plans (see .claude/docs — V29 took production down this way).

alter table plans add column slug varchar(100);

-- Same rule as com.jetmenu.billing.PlanSlug: strip accents, lowercase, collapse every
-- non-alphanumeric run into a single hyphen, trim leading/trailing hyphens.
-- translate() is used instead of the unaccent extension so the migration does not depend
-- on an extension being installable on the target database.
update plans
   set slug = trim(both '-' from
                   regexp_replace(
                       lower(translate(name,
                                       'áàâãäéèêëíìîïóòôõöúùûüñçÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÑÇ',
                                       'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC')),
                       '[^a-z0-9]+', '-', 'g'))
 where slug is null;

-- Not null: a plan with no slug cannot be sold, and letting one exist only defers the
-- failure to checkout time. Unique: two plans resolving to the same slug would share a
-- Stripe Price and bill the wrong amount — that must fail here, loudly, not in production.
alter table plans alter column slug set not null;

alter table plans
    add constraint plans_slug_key unique (slug);

-- The Stripe catalog is the source of truth for what a plan costs. These mirror it:
-- StripeCatalogSync lists the active monthly BRL Prices and upserts them here, keyed by
-- the Price's lookup_key (which maps 1:1 onto slug above). Nothing types a price id in by
-- hand any more, and price_monthly stops being a second, hand-maintained copy of the
-- amount Stripe actually charges — the two had already drifted (R$ 50 here vs R$ 70 in
-- the sandbox) before anyone had paid.
--
-- Nullable: a plan exists before the first sync runs, and an environment with Stripe
-- unconfigured never syncs at all. Checkout answers 503 for a plan with no price id.
-- Unique on the price id: two plans sharing one Stripe Price would bill the same amount
-- under two names, which is a catalog mistake, not a valid state.
alter table plans add column stripe_price_id varchar(255);
alter table plans add column stripe_product_id varchar(255);

alter table plans
    add constraint plans_stripe_price_id_key unique (stripe_price_id);
