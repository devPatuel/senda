# 0011 — Catálogo de productos y listas (deseos + compra)

- **Fecha**: 2026-07-11
- **Estado**: Obsoleto desde 2026-09-19

> **Obsoleto.** El catálogo de productos y la lista de la compra se retiraron al acotar
> Senda a finanzas: el código se borró y la migración `V27` elimina sus tablas
> (`products`, `price_entries`, `shopping_list_items`). De este ADR solo sigue vigente la
> lista de deseos (`wishlist_items`, migración `V22`). Se conserva como registro de por
> qué existió el módulo.

## Contexto

La lista de la compra y la de deseos vivían mezcladas en un único módulo `shopping`
(`shopping_items` con `list_type ∈ {GROCERY, WISHLIST}`). Se quería: (a) un catálogo de
productos de comida con histórico de precios por supermercado para estimar el coste de la
compra, (b) una lista de deseos más rica (foto, enlace, comentario, precio, total), y
(c) una lista de la compra apoyada en el catálogo.

## Decisiones

1. **Catálogo como base común (F7).** Módulo `product` con `products` + `price_entries`
   (histórico). El "precio actual" de un producto por supermercado es su `PriceEntry` más
   reciente; la comparativa ordena de más barato a más caro. Aísla por usuario
   (`space_id IS NULL`), con `space_id` reservado para la futura variante de pareja.

2. **Lista de deseos como módulo separado (F9), no extender `ShoppingItem`.** Un modelo
   propio (`wishlist_items`) evita seguir acumulando columnas mono-tipo (`envelope_id`,
   `bought`) y da entidad/DTO/endpoint/página limpios. El total suma los precios (ignora
   nulos). Los WISHLIST antiguos se migran (V22).

3. **Lista de la compra sobre el catálogo con tabla puente (F10).** `shopping_list_items`
   referencia `products` (no un flag en `Product`): "por comprar" es estado transitorio
   (`quantity`, `checked`). El `estimatedTotal` = Σ (último precio conocido × cantidad),
   saltando productos sin precio. Un producto aparece a lo sumo una vez por usuario.

4. **Retiro del módulo `shopping` (V24, DROP `shopping_items`).** F9+F10 lo reemplazan por
   completo. Los WISHLIST se migraron (V22); los GROCERY de texto libre **se descartan**
   (no son mapeables al catálogo). Decisión de datos confirmada con Jordi — app personal.

## Consecuencias

- Migraciones V21 (catálogo), V22 (deseos + migración de datos), V23 (lista de compra),
  V24 (DROP). El orden de despliegue debe ser monótono (Flyway).
- El precio unitario de la compra usa el último precio global del producto (de cualquier
  super), no el de un super elegido; la comparativa por super se difiere.
- `space_id` presente en las tres tablas nuevas para habilitar la variante de pareja sin
  nueva migración.
