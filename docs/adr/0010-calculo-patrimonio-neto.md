# ADR 0010 — Cálculo del patrimonio neto

**Fecha:** 2026-06-16 · **Estado:** aceptada

## Contexto

La pantalla "Patrimonio" ("Mi dinero") necesita una visión única del patrimonio neto
del usuario que combine todo lo que ya viven en módulos separados: dinero líquido
(cuentas), inversiones (posiciones y NFTs) y deudas (a favor y en contra). No hay un
dato nuevo que almacenar: es una **agregación** de datos que ya existen.

## Decisión

El módulo `networth` es un **agregador de solo lectura sin tablas propias**: inyecta los
repositorios de `account`, `investment` y `debt` y calcula sobre la marcha, siempre
filtrando por el `user_id` del token (`@Transactional(readOnly = true)`).

`GET /api/networth` devuelve el desglose y el neto:

- **Líquido** = suma del `balance` de las cuentas **no archivadas**.
- **Inversiones** = valor de mercado de los *holdings* (`quantity * current_price`; un
  holding **sin precio** (`current_price` nulo) cuenta **0**, no rompe el cálculo) **+**
  el valor de los NFTs por su `our_current_value` (la valoración manual "que le damos
  nosotros", no el precio de mercado de la cripto con la que se compró).
- **Deudas a favor** = suma del **pendiente** de las deudas `THEY_OWE_ME`.
- **Deudas en contra** = suma del **pendiente** de las deudas `I_OWE`.
- **Neto** = líquido + inversiones + a favor − en contra.

El pendiente de cada deuda (`original − pagado`) se **acota a ≥ 0** (*clamp*): una deuda
saldada o sobrepagada aporta 0, nunca un valor negativo que distorsione el patrimonio.
Las sumas de pagos se obtienen con la consulta por lote `sumByDebtIds` (sin N+1) y todos
los totales se devuelven con escala 2.

## Consecuencias

- (+) Una sola fuente de verdad: el patrimonio se deriva de los módulos existentes, sin
  duplicar datos ni mantener un total desnormalizado que pudiera quedar desincronizado.
- (+) Multi-tenant por construcción: cada repositorio se consulta por `user_id` del token.
- (+) Robusto ante datos incompletos: holdings sin precio o deudas sin pagos no rompen el
  cálculo.
- (−) Acoplamiento de lectura entre módulos: `networth` depende de las firmas de los
  repositorios de `account`, `investment` y `debt`.
- (~) El valor de las inversiones depende de lo "fresco" que esté `current_price` (se
  refresca bajo demanda, ver [ADR 0007](0007-precios-inversion-cripto-auto-resto-manual.md));
  el patrimonio refleja el último precio conocido, no necesariamente el del instante.
