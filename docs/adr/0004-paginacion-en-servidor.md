# ADR 0004 — Paginación en servidor desde el día 1

**Fecha:** 2026-06-12 · **Estado:** aceptada

## Contexto

El listado de movimientos crece sin límite (años de gastos diarios). Devolver la lista
completa y paginar/filtrar en el cliente funciona con pocos datos pero degrada con el
tiempo y obliga a un cambio de contrato de API justo cuando ya hay consumidores.

## Decisión

`GET /api/transactions` es paginado y filtrado **en servidor** desde el primer día:
parámetros `page` (0-based), `size` (default 20, máx 100), `from`, `to`, `categoryId`,
`type`; orden `date DESC, id DESC`. La respuesta usa un envoltorio propio
(`content`, `page`, `size`, `totalElements`, `totalPages`) en lugar de serializar el
`Page` de Spring, cuya forma JSON no es un contrato estable entre versiones.

## Consecuencias

- (+) Rendimiento estable con cualquier volumen; el contrato no cambia cuando los datos crecen.
- (+) El envoltorio propio desacopla la API de los internos de Spring Data.
- (−) Más trabajo inicial en backend (specification/queries con filtros) y en frontend (controles de página).
