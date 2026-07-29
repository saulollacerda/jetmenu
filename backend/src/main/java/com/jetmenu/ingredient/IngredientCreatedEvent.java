package com.jetmenu.ingredient;

import java.util.UUID;

public record IngredientCreatedEvent(UUID merchantId, UUID ingredientId, String canonicalName) {}
