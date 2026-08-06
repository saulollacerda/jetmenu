package com.jetmenu.billing;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Derives a plan's stable key from its display name: lowercase, accents stripped,
 * non-alphanumerics collapsed into hyphens. {@code "Básico"} becomes {@code "basico"}.
 * <p>
 * <b>This derivation is meant to run exactly once, when the plan is created.</b> The result is
 * then frozen in {@code plans.slug} and everything external keys on it — notably
 * {@code stripe.price-ids.<slug>}. Re-deriving it from {@link Plan#getName()} at call time is
 * what this class exists to stop: the name is a display string the business is free to change,
 * and a rename must never silently move the plan's identity out from under its Stripe Price.
 * <p>
 * Stripping the accent is not cosmetic: {@code .properties} files are read as ISO-8859-1, so an
 * accented key would have to be written as a unicode escape and would silently stop matching
 * the moment someone typed it literally.
 */
public final class PlanSlug {

    private PlanSlug() {
    }

    public static String of(String planName) {
        if (planName == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(planName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    /** Configuration/environment suffix for a slug: {@code "plano-avancado"} → {@code "PLANO_AVANCADO"}. */
    public static String toEnvSuffix(String slug) {
        return slug.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
