package com.jetmenu.category;

/**
 * Where a catalog record (product/category) came from: created by the merchant in
 * JetMenu or imported from an external sales channel. Mirrors OrderOrigin naming.
 */
public enum CatalogOrigin {
    JETMENU,
    ANOTA_AI,
    IFOOD
}
