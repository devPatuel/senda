-- Feature 9+10 fully supersede the old shopping module: WISHLIST rows were migrated
-- to wishlist_items (V22); the grocery list is now catalog-backed (V23). Free-text
-- GROCERY rows are intentionally dropped (not mappable to the catalog).
DROP TABLE shopping_items;
