# ADR 0003 — Migraciones con Flyway desde el día 1

**Fecha:** 2026-06-12 · **Estado:** aceptada

## Contexto

Hibernate puede generar el esquema automáticamente (`ddl-auto: update`), lo que es cómodo
al empezar pero produce esquemas no reproducibles, sin historial y peligrosos en
producción (cambios implícitos sobre datos reales). Senda guardará datos financieros
reales y se desplegará en un NAS.

## Decisión

Flyway gestiona el esquema desde la primera tabla. Migraciones SQL versionadas en
`backend/src/main/resources/db/migration` (`V1__...sql`, `V2__...sql`).
Hibernate solo valida (`ddl-auto: validate`).

## Consecuencias

- (+) Esquema reproducible en cualquier entorno (dev, tests con Testcontainers, NAS) con historial versionado en git.
- (+) Hibernate `validate` detecta al arrancar cualquier desajuste entidad ↔ esquema.
- (−) Cada cambio de esquema exige escribir una migración a mano; es fricción deliberada que obliga a pensar los cambios.
- (−) Una migración aplicada no se edita: los errores se corrigen con una migración nueva.
