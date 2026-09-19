-- Removes four modules that turned the app into a catch-all: shopping list,
-- product catalog, category rules and habits. Dropped in dependency order
-- (habit_entries and shopping_list_items reference their parent tables).
DROP TABLE IF EXISTS shopping_list_items;
DROP TABLE IF EXISTS price_entries;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS category_rules;
DROP TABLE IF EXISTS habit_entries;
DROP TABLE IF EXISTS habits;
