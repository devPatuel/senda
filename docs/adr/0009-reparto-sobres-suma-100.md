# ADR 0009 — Reparto de sueldo: los sobres deben sumar exactamente 100%

**Fecha:** 2026-06-16 · **Estado:** aceptada

## Contexto

El reparto (*envelope budgeting*) divide un importe (p. ej. la nómina) entre varios
sobres, cada uno con un porcentaje. Para que el reparto sea consistente, los porcentajes
**deben sumar exactamente 100%**: si sumaran 90 o 110, una parte del dinero quedaría sin
asignar o se asignaría de más. Además, al repartir importes con decimales, el redondeo de
cada sobre puede hacer que la suma de las partes no cuadre con el importe original (se
pierde o se crea algún céntimo).

## Decisión

**El plan de sobres se guarda de forma atómica** con `PUT /api/allocation/envelopes`:
todo el plan del usuario se reemplaza en una sola operación (se conservan por `id` los
sobres existentes para preservar su saldo acumulado, se borran los que ya no están y se
crean los nuevos con saldo 0). Al guardar se valida que la suma de porcentajes sea
exactamente 100 usando **`BigDecimal.compareTo`** (no `equals`, que distingue escala:
`100` ≠ `100.00`). Si no suma 100 → **`409 Conflict`**.

El reparto (`POST /api/allocation/distribute`) calcula la parte de cada sobre
(`amount * pct / 100`, `HALF_UP` a 2 decimales) y **ajusta el residuo del redondeo en el
último sobre**, de modo que `sum(allocated) == amount` exacto. Antes de repartir
revalida que el plan actual sume 100. Con `persist=true` cada parte se acumula en el
saldo del sobre (`envelope_balances`) dentro de la misma transacción; con `persist=false`
es una simulación pura (no toca saldos).

## Consecuencias

- (+) Imposible guardar un plan inconsistente: o suma 100 o se rechaza.
- (+) El reparto nunca pierde ni inventa céntimos; la suma de partes cuadra con el importe.
- (+) Los saldos por sobre (`envelope_balances`) sobreviven a renombrados y reordenaciones
  del plan, porque el guardado conserva los sobres por `id`.
- (−) Editar un plan obliga a reenviar el plan completo (no hay edición sobre/parcial);
  es la contrapartida del guardado atómico.
- (−) El residuo de redondeo siempre recae en el último sobre, que puede recibir uno o
  dos céntimos de más o de menos respecto a su porcentaje exacto.
