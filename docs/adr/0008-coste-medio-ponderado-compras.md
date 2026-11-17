# ADR 0008 — Coste medio ponderado en compras (holdings y lotes)

**Fecha:** 2026-06-16 · **Estado:** aceptada

## Contexto

Una posición (`holding`) se construye con varias compras a lo largo del tiempo, cada una
a un precio distinto. Para calcular un P&L correcto hace falta saber cuánto se pagó de
media por cada unidad: el **coste base** de la posición. Mantener solo el último precio
de compra, o recalcular sumando importes de movimientos sueltos, no da un coste medio
fiable ni un histórico auditable.

## Decisión

Cada posición almacena `quantity` y `avg_cost` (coste medio ponderado). Al añadir una
compra (`POST /api/investments/holdings/{id}/buys`) se recalcula:

```
new_avg = (old_qty * old_avg + buy_qty * unit_price) / (old_qty + buy_qty)
```

con `BigDecimal`, **escala 8** y redondeo `HALF_UP`, acorde a las columnas
`NUMERIC(20,8)` de cantidades y precios. `new_qty > 0` siempre, porque `buy_qty > 0`
está validado y `old_qty >= 0`.

Cada compra se persiste además como una fila en `holding_lots` (cantidad, precio
unitario, fecha): el **histórico** de compras, consultable con
`GET /api/investments/holdings/{id}/lots`. Borrar la posición elimina sus lotes en
cascada (`ON DELETE CASCADE`).

Las cifras derivadas las calcula el servicio al construir la respuesta:

```
cost        = quantity * avgCost          (lo que pusiste)
marketValue = quantity * currentPrice     (null si no hay precio)
pnl         = marketValue - cost          (null si no hay precio)
```

## Consecuencias

- (+) Coste medio correcto e independiente del orden de las compras; P&L fiable.
- (+) `holding_lots` deja un histórico auditable de cada compra, sin recalcular nada.
- (+) `NUMERIC(20,8)` admite activos con muchos decimales (cripto fraccionada).
- (−) El coste medio ponderado no modela ventas parciales con criterio fiscal (FIFO/LIFO):
  Senda registra compras y posición, no plusvalías por venta. Si en el futuro se
  necesita, habrá que ampliar el modelo de lotes.
- (−) Un holding sin precio (`current_price` nulo) deja `marketValue` y `pnl` en `null`,
  que el frontend debe saber representar.

## Actualización 2026-09-29 — tipo de lote

Cada lote lleva `kind` (`BUY` | `REWARD`, migración `V30`, por defecto `BUY`). Las
recompensas (staking, intereses) se registran al **precio de mercado del momento en que
se reciben** y entran en la media ponderada igual que una compra: es el criterio fiscal
(rendimiento del capital mobiliario, cuyo valor pasa a ser el coste de adquisición) y
evita inflar la rentabilidad con unidades a coste 0. La fórmula no cambia; `kind` solo
etiqueta el lote para mostrar qué parte del invertido vino de recompensas.
`POST /holdings/{id}/buys` acepta `kind` opcional (omitido → `BUY`).

## Actualización 2026-11-17 — venta simple

Nuevo tipo de lote `SELL` (migración `V36`) y `POST /holdings/{id}/sells`. Una venta
**resta cantidad y no toca el coste medio**: con media ponderada, cada unidad vendida
sale al coste medio, así que lo que queda conserva su media. Si la posición llega a 0, el
coste medio pasa a 0 (misma regla que al crear una posición vacía). El precio de venta se
guarda en el lote solo como historial; **no se calculan plusvalías realizadas**. Se
descartó FIFO de momento: haría falta un lote abierto por compra y solo sirve para la renta.

- Vender más de lo que hay → `400`. La posición se lee con bloqueo (`PESSIMISTIC_WRITE`)
  en compras y ventas, para que dos operaciones a la vez no pisen la cantidad.
- `/buys` rechaza `kind: SELL` (`400`): una venta por ahí sumaría cantidad.
- `rewardsCost` ya no es la suma bruta de las recompensas: cada venta conserva la parte
  proporcional (`cantidad después / cantidad antes`), porque las unidades vendidas se
  llevan su parte de recompensas. Se calcula recorriendo los lotes en orden de alta.
