# API de Senda

Base: `/api` (en desarrollo, `http://localhost:8080/api`).

Autenticación por header `Authorization: Bearer <credencial>` en todos los endpoints
salvo el registro y el login. La credencial es el JWT del login (expira a las 24 h, o
antes si se cambia la contraseña) o un [token personal](#tokens-personales), que no
caduca pero solo sirve para apuntar gastos.

**Ámbito personal o de espacio.** Categorías, cuentas, movimientos e importación
aceptan un `spaceId` opcional. Sin él se trabaja con lo personal del usuario; con él,
con lo del [espacio compartido](#espacios-compartidos), siempre que el usuario sea
miembro activo (si no, `404`).

## Formato de error (común a toda la API)

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": { "email": "must be a well-formed email address" }
}
```

`fieldErrors` solo aparece en errores de validación. Códigos usados:

| Código | Cuándo |
|---|---|
| 400 | Validación de entrada fallida |
| 401 | Credenciales incorrectas, token ausente/inválido/expirado/revocado |
| 403 | Registro cerrado, o token personal usado fuera de lo que tiene permitido. También lo devuelve cualquier escritura hecha desde un origen que no está en `SENDA_CORS_ORIGINS` |
| 422 | La petición es válida pero incumple una regla (p. ej. contraseña actual incorrecta al cambiarla) |
| 404 | Recurso inexistente **o de otro usuario o espacio** (nunca 403) |
| 409 | Conflicto de estado: email ya registrado, nombre de categoría duplicado (mismo usuario y tipo), categoría inactiva al crear/editar un movimiento |
| 429 | Demasiados intentos en `/api/auth/**` (límite por IP: 10 peticiones/minuto) |

---

## Auth

Registro y login son públicos. Todo `/api/auth/**` tiene **rate limiting por IP**
(10 peticiones/minuto en total): al superarlo devuelve `429 Too Many Requests`. Protege
contra fuerza bruta y contra agotamiento de CPU (cada intento ejecuta BCrypt). Detrás
de nginx la IP se lee de la cabecera `X-Real-IP`, que nginx sobrescribe en cada
petición; sin proxy delante se usa la dirección de la conexión.

### POST /api/auth/register

Alta de usuario. Crea automáticamente sus categorías por defecto.

**Cerrado por defecto.** El alta self-service solo funciona si el despliegue pone
`senda.auth.registration-enabled: true` (env `SENDA_REGISTRATION_ENABLED`); si no,
responde `403 Forbidden` antes de tocar la base de datos. Senda es de un solo usuario
en la práctica y cualquier despliegue es alcanzable por toda su red: se abre lo justo
para crear la primera cuenta y se vuelve a cerrar. El perfil `local` lo trae abierto.

Body:

```json
{ "email": "jordi@example.com", "password": "secreta123", "name": "Jordi" }
```

`password`: mínimo 8 caracteres y máximo **72 bytes UTF-8** (límite duro de BCrypt;
ojo, bytes y no caracteres: con tildes o eñes cada carácter puede ocupar 2 bytes).

Respuesta `201 Created`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": { "id": 1, "email": "jordi@example.com", "name": "Jordi" }
}
```

Errores: `403` registro cerrado, `409` email duplicado, `400` validación.

### POST /api/auth/login

Body:

```json
{ "email": "jordi@example.com", "password": "secreta123" }
```

Respuesta `200 OK`: mismo cuerpo que register (`token` + `user`).

Errores: `401` credenciales incorrectas, `400` validación (p. ej. contraseña de más
de 72 bytes, que nunca puede ser válida). El `401` es idéntico, y tarda lo mismo, tanto
si el email no existe como si la contraseña es incorrecta: así no se puede averiguar
qué emails tienen cuenta.

### POST /api/auth/password

Cambia la contraseña del usuario autenticado. Requiere sesión (JWT); un token personal
recibe `403`.

```json
{ "currentPassword": "secreta123", "newPassword": "otra-mas-larga" }
```

Respuesta `200 OK`: mismo cuerpo que el login (`token` + `user`). **Todas las sesiones
anteriores dejan de valer** en ese momento; el `token` de la respuesta es la sesión
nueva de quien ha hecho el cambio. Los tokens personales no se ven afectados.

Errores: `422` contraseña actual incorrecta (no `401`, que el cliente web interpreta
como sesión caducada), `400` contraseña nueva de menos de 8 caracteres o más de 72 bytes.

---

## Categorías

`CategoryResponse`:

```json
{ "id": 3, "name": "Comida", "type": "EXPENSE", "color": "#EF4444", "emoji": "🍎",
  "active": true, "fixed": false, "transfer": false }
```

### GET /api/categories

Categorías del usuario autenticado.

| Parámetro | Tipo | Descripción |
|---|---|---|
| `type` | `INCOME` \| `EXPENSE` | Opcional, filtra por tipo |
| `includeInactive` | boolean | Opcional, default `false` (solo activas) |
| `spaceId` | number | Opcional, categorías del espacio en vez de las personales |

Respuesta `200 OK`: `[CategoryResponse]`.

Dos marcas opcionales al crear o editar una categoría:

| Campo | Significado |
|---|---|
| `fixed` | Gasto recurrente no discrecional (alquiler, seguros). Separa fijo de variable en el resumen. Solo tiene sentido en categorías de gasto |
| `transfer` | Movimiento de dinero entre cuentas propias. Ni ingreso ni gasto: se resume aparte (ver el resumen mensual) |

Categorías por defecto creadas al registrarse:

| Tipo | Nombre | Color |
|---|---|---|
| EXPENSE | Comida | `#EF4444` |
| EXPENSE | Transporte | `#3B82F6` |
| EXPENSE | Vivienda | `#8B5CF6` |
| EXPENSE | Ocio | `#F59E0B` |
| EXPENSE | Salud | `#10B981` |
| EXPENSE | Compras | `#EC4899` |
| EXPENSE | Otros gastos | `#6B7280` |
| INCOME | Nómina | `#22C55E` |
| INCOME | Otros ingresos | `#14B8A6` |

### POST /api/categories

Body:

```json
{ "name": "Mascotas", "type": "EXPENSE", "color": "#F97316" }
```

Opcionales: `emoji`, `fixed`, `transfer` y `spaceId` (crea la categoría en ese espacio).

Respuesta `201 Created`: `CategoryResponse`.

Errores: `400` validación, `409` nombre duplicado para ese usuario y tipo.

### PUT /api/categories/{id}

Body (distinto del POST):

```json
{ "name": "Mascotas", "color": "#F97316", "active": true }
```

- `type` **no se puede cambiar**: es inmutable tras la creación. Si se envía un campo
  `type` en el JSON, se ignora silenciosamente.
- `active`, `fixed` y `transfer` son opcionales: si se omiten (o son `null`) se
  conserva el valor actual. Enviar `active: true` es la forma de **reactivar** una
  categoría desactivada. `emoji` también es opcional.

Respuesta `200 OK`: `CategoryResponse`.

Errores: `404` si no existe o es de otro usuario, `400` validación,
`409` nombre duplicado para ese usuario y tipo.

### DELETE /api/categories/{id}

- Sin movimientos asociados → borrado físico.
- Con movimientos → **desactivación** (`active = false`); los movimientos históricos se conservan.

Respuesta `204 No Content`. Errores: `404` si no existe o es de otro usuario.

### Presupuesto por sobres

Cada categoría de gasto tiene un "sobre": dinero asignado del que se va gastando.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/categories/budget?spaceId=` | Resumen del presupuesto y una línea por categoría de gasto |
| POST | `/api/categories/{id}/assign?spaceId=` | Suma `amount` al sobre. Puede ser negativo, para sacar dinero. Body: `{ "amount": 150.00 }` |
| POST | `/api/categories/{id}/target?spaceId=` | Fija el objetivo del sobre. Body: `{ "targetAmount": 300.00 }`; con `null` lo quita |

Los tres devuelven el presupuesto completo, para refrescar la pantalla de una vez:

```json
{
  "totalAccounts": 2000.00,
  "totalAssigned": 1500.00,
  "totalAvailable": 900.00,
  "overspent": 0.00,
  "toAssign": 1100.00,
  "categories": [
    { "id": 3, "name": "Comida", "color": "#EF4444", "balance": 400.00,
      "spentThisMonth": 120.00, "spent": 250.00, "available": 150.00,
      "targetPercentage": 20.00, "targetAmount": 300.00 }
  ]
}
```

- `balance` es lo asignado al sobre, `spent` todo lo gastado de él y `available` la
  diferencia. `available` negativo significa que la categoría está pasada; la suma de
  esos negativos es `overspent`.
- `toAssign = totalAccounts − totalAvailable`: el dinero de las cuentas que aún no está
  en ningún sobre. Puede ser negativo si los sobres guardan más de lo que hay.
- En un espacio, `totalAccounts` no es el saldo de sus cuentas (que solo se edita a mano)
  sino lo que dejan sus movimientos: todo lo que entra, aportaciones incluidas, menos todo
  lo que sale. Así cada aportación aparece sola en `toAssign`. Supone que la cuenta común
  empezó a cero con el espacio.
- Los traspasos no cuentan como gasto de ningún sobre. En un espacio sí entran en
  `totalAccounts`: una aportación es dinero que llega a la cuenta común.

---

## Movimientos

`TransactionResponse`:

```json
{
  "id": 12,
  "categoryId": 3,
  "categoryName": "Comida",
  "categoryColor": "#EF4444",
  "categoryEmoji": "🍎",
  "type": "EXPENSE",
  "amount": 23.50,
  "date": "2026-06-12",
  "description": "Compra semanal",
  "createdAt": "2026-06-12T18:30:00Z"
}
```

### GET /api/transactions

Listado paginado del usuario autenticado. Orden: `date DESC, id DESC`.

| Parámetro | Tipo | Default | Descripción |
|---|---|---|---|
| `spaceId` | long | — | Movimientos del espacio en vez de los personales |
| `page` | int (0-based) | 0 | Página |
| `size` | int | 20 (máx 100) | Tamaño de página |
| `from` | `YYYY-MM-DD` | — | Fecha mínima (inclusive) |
| `to` | `YYYY-MM-DD` | — | Fecha máxima (inclusive) |
| `categoryId` | long | — | Filtrar por categoría |
| `type` | `INCOME` \| `EXPENSE` | — | Filtrar por tipo |

Respuesta `200 OK` (envoltorio propio, no el `Page` de Spring):

```json
{
  "content": [ { "id": 12, "categoryId": 3, "categoryName": "Comida", "categoryColor": "#EF4444", "type": "EXPENSE", "amount": 23.50, "date": "2026-06-12", "description": "Compra semanal", "createdAt": "2026-06-12T18:30:00Z" } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### GET /api/transactions/{id}

Respuesta `200 OK`: `TransactionResponse`. Errores: `404` si no existe o es de otro usuario.

### POST /api/transactions

Body:

```json
{ "categoryId": 3, "type": "EXPENSE", "amount": 23.50, "date": "2026-06-12", "description": "Compra semanal" }
```

`description` es opcional (máx. 500 caracteres). El `type` debe coincidir con el de la
categoría. Con `spaceId` el movimiento se crea en ese espacio y la categoría debe ser
del mismo espacio; sin él, debe ser una categoría personal.

Respuesta `201 Created`: `TransactionResponse`.

Errores: `400` validación (importe ≤ 0, tipo que no coincide con la categoría…),
`404` si la categoría no existe o es de otro usuario,
`409` si la categoría está inactiva (desactivada con el DELETE de categorías).

### PUT /api/transactions/{id}

Mismo body que el POST. Respuesta `200 OK`: `TransactionResponse`. El ámbito de un
movimiento no cambia al editarlo: el `spaceId` del body se ignora.

Errores: `404` si no existe o es de otro usuario, `400` validación,
`409` si la categoría destino está inactiva.

### DELETE /api/transactions/{id}

Respuesta `204 No Content`. Errores: `404` si no existe o es de otro usuario.

### GET /api/transactions/summary?year=&month=

Resumen mensual del usuario autenticado (o del espacio, con `spaceId`).
`balance = totalIncome - totalExpense`. `byCategory` solo incluye categorías con
movimientos en ese mes.

Las categorías marcadas como **traspaso** (`transfer`) quedan fuera de
`totalIncome` y `totalExpense` y se resumen aparte en `transfersIn` /
`transfersOut`: mover dinero entre cuentas propias (financiar la cuenta conjunta,
por ejemplo) no es ni ingreso ni gasto, y contarlo como tal infla los totales y
cualquier ratio derivado de ellos. Siguen apareciendo en `byCategory` con su
marca, para poder mostrarlas por separado.

`fixedExpensePercentage` es `null` cuando no hay ingresos reales en el mes: un
porcentaje "sobre nada" no significa nada, y el cliente debe distinguir ese caso
de un 0%.

Respuesta `200 OK`:

```json
{
  "year": 2026,
  "month": 6,
  "totalIncome": 1800.00,
  "totalExpense": 23.50,
  "balance": 1776.50,
  "fixedExpenseTotal": 0.00,
  "variableExpenseTotal": 23.50,
  "fixedExpensePercentage": 0.00,
  "transfersIn": 0.00,
  "transfersOut": 0.00,
  "topExpenseCategory": { "categoryId": 3, "categoryName": "Comida", "categoryColor": "#EF4444", "type": "EXPENSE", "total": 23.50, "fixed": false, "transfer": false },
  "byCategory": [
    { "categoryId": 8, "categoryName": "Nómina", "categoryColor": "#22C55E", "type": "INCOME", "total": 1800.00, "fixed": false, "transfer": false },
    { "categoryId": 3, "categoryName": "Comida", "categoryColor": "#EF4444", "type": "EXPENSE", "total": 23.50, "fixed": false, "transfer": false }
  ]
}
```

Errores: `400` si `year`/`month` faltan o no son válidos.

### GET /api/transactions/summary/year?year=

El año de un vistazo (o el del espacio, con `spaceId`): lo gastado, lo aportado como
traspaso y los doce meses en orden, también los que no tienen movimientos.

```json
{
  "year": 2026,
  "totalExpense": 5400.00,
  "totalTransfersIn": 6000.00,
  "months": [ { "year": 2026, "month": 1, "expense": 450.00, "transfersIn": 500.00 } ]
}
```

No hay media anual a propósito: dividir entre doce trataría los meses sin actividad
como meses baratos.

### GET /api/transactions/trends?months=6

Ingresos, gastos y balance personales de los últimos `months` meses (de 1 a 24, por
defecto 6), del más antiguo al más reciente:

```json
[ { "year": 2026, "month": 6, "income": 1800.00, "expense": 950.00, "balance": 850.00 } ]
```

Errores: `400` si `months` está fuera de rango.

### POST /api/transactions/quick

Captura rápida, pensada para un atajo de iOS con un
[token personal](#tokens-personales). Crea siempre un **gasto personal con fecha de
hoy**.

```json
{ "amount": 12.50, "description": "Mercadona", "categoryId": 3 }
```

Los tres campos son obligatorios. Respuesta `201 Created`: `TransactionResponse`.

Errores: `400` validación o categoría que no es de gasto, `404` categoría inexistente
o ajena, `409` categoría inactiva.

---

## Importación de extractos

El navegador lee el archivo (CSV o Excel) y envía las filas ya interpretadas; la API no
recibe archivos. Son dos pasos.

### POST /api/imports/preview

```json
{
  "spaceId": null,
  "rows": [ { "date": "2026-09-03", "description": "Mercadona", "amount": -73.15 } ]
}
```

`amount` lleva signo: negativo es gasto, positivo es ingreso. Respuesta `200 OK`, una
fila por cada una enviada, con el importe en positivo, el tipo deducido y si ya existe
un movimiento con la misma fecha, importe y descripción:

```json
[ { "date": "2026-09-03", "description": "Mercadona", "amount": 73.15, "type": "EXPENSE", "duplicate": false } ]
```

### POST /api/imports/commit

```json
{
  "spaceId": null,
  "rows": [ { "date": "2026-09-03", "description": "Mercadona", "amount": 73.15, "type": "EXPENSE", "categoryId": 3 } ]
}
```

Máximo **5000 filas** por petición, tanto aquí como en la previsualización (`400` si se
supera).

Crea un movimiento por fila. Los duplicados se vuelven a comprobar aquí, así que una
previsualización antigua o dos líneas iguales en el mismo archivo no crean movimientos
repetidos. Respuesta `200 OK`: `{ "imported": 1, "skipped": 0 }`.

Errores: `400` validación, categoría inactiva o de un tipo distinto al de la fila;
`404` categoría inexistente o de otro ámbito. Si una fila falla no se importa ninguna.

---

## Cuentas (dinero líquido)

`AccountResponse`:

```json
{ "id": 1, "name": "Cuenta nómina", "type": "BANK", "balance": 1500.00, "currency": "EUR", "archived": false, "createdAt": "2026-06-16T10:00:00Z" }
```

`type`: `BANK` | `CASH`. El saldo se actualiza **a mano**. `currency` es opcional al crear
(default `EUR`).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/accounts?includeArchived=false&spaceId=` | Lista las cuentas personales, o las del espacio (solo no archivadas por defecto) |
| GET | `/api/accounts/balance` | Saldo total de las cuentas personales no archivadas: `{ "total": 1550.00 }` |
| POST | `/api/accounts` | Crea una cuenta (`201`). Body: `name`, `type`, `balance`, `currency?`, `spaceId?` |
| PUT | `/api/accounts/{id}` | Edita (incluye `archived` para archivar/restaurar) |
| DELETE | `/api/accounts/{id}` | Borrado físico (`204`) |

Errores: `400` validación, `404` cuenta inexistente o de otro usuario.

---

## Deudas

`DebtResponse` incluye el importe original, lo **pagado** y lo **pendiente**:

```json
{ "id": 1, "direction": "THEY_OWE_ME", "counterparty": "Rodrigo", "concept": "Cena",
  "originalAmount": 100.00, "paidAmount": 30.00, "pendingAmount": 70.00,
  "settled": false, "date": "2026-06-01", "createdAt": "2026-06-16T10:00:00Z" }
```

`direction`: `THEY_OWE_ME` (me deben) | `I_OWE` (debo).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/debts?direction=&settled=` | Lista las deudas (filtros opcionales por dirección y estado) |
| POST | `/api/debts` | Crea (`201`). Body: `direction`, `counterparty`, `concept`, `originalAmount`, `date` |
| GET | `/api/debts/{id}` | Una deuda |
| PUT | `/api/debts/{id}` | Edita; el nuevo `originalAmount` no puede ser menor que lo ya pagado (`400`) |
| DELETE | `/api/debts/{id}` | Borra la deuda y sus pagos (`204`) |
| POST | `/api/debts/{id}/payments` | Registra un abono (`201`). Body: `amount`, `date`, `note?` |
| GET | `/api/debts/{id}/payments` | Lista los abonos de la deuda |
| DELETE | `/api/debts/{id}/payments/{paymentId}` | Borra un abono (`204`) |

Reglas: el pendiente = `originalAmount − SUM(pagos)`; un abono que **excede el pendiente**
da `400`; al llegar a 0 la deuda queda `settled` (se revierte al borrar un abono).
Errores: `400` validación / abono excesivo, `404` recurso inexistente o de otro usuario.

---

## Inversiones

### Clases de activo

`AssetClassResponse`: `{ "id": 1, "name": "Cripto", "pricingSource": "CRYPTO" }`.
`pricingSource`: `CRYPTO` (auto vía CoinGecko, con Coinbase de respaldo) | `METAL` | `FUND` | `MANUAL` (estos tres,
precio a mano). Al registrarse se siembran por defecto: Cripto, Fondos, Oro, Plata.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/investments/asset-classes` | Lista las clases del usuario |
| POST | `/api/investments/asset-classes` | Crea (`201`). `409` si el nombre ya existe |
| PUT | `/api/investments/asset-classes/{id}` | Edita; `409` al cambiar `pricingSource` si ya tiene posiciones |
| DELETE | `/api/investments/asset-classes/{id}` | Borra; `409` si tiene posiciones |

### Posiciones (holdings)

`HoldingResponse` incluye valor de mercado y P&L calculados:

```json
{ "id": 1, "assetClassId": 1, "assetClassName": "Cripto", "pricingSource": "CRYPTO",
  "symbol": "BTC", "name": "Bitcoin", "quantity": 0.50000000, "avgCost": 50000.00000000,
  "currentPrice": 58000.00000000, "lastPricedAt": "2026-06-16T10:00:00Z",
  "cost": 25000.00, "marketValue": 29000.00, "pnl": 4000.00,
  "pnlPct": 16.00, "rewardsCost": 12.50 }
```

`marketValue`, `pnl` y `pnlPct` son `null` cuando no hay `currentPrice` (`pnlPct` también
si `cost` es 0). `pnlPct` = `pnl / cost × 100` con 2 decimales. `rewardsCost` es la parte
de `cost` que viene de lotes `REWARD` (0 si no hay). `symbol` debe ser
alfanumérico.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/investments/holdings?assetClassId=` | Lista las posiciones (filtro opcional por clase) |
| POST | `/api/investments/holdings` | Crea (`201`). `400` si `quantity` 0 con `avgCost` ≠ 0 |
| GET | `/api/investments/holdings/{id}` | Una posición |
| POST | `/api/investments/holdings/{id}/buys` | Registra una compra (`201`): recalcula cantidad y coste medio y guarda un lote. Body: `quantity`, `unitPrice`, `date` y `kind` opcional (`BUY` \| `REWARD`, por defecto `BUY`; otro valor → `400`) |
| GET | `/api/investments/holdings/{id}/lots` | Histórico de lotes, del más reciente al más antiguo; cada uno con `kind` |
| PUT | `/api/investments/holdings/{id}/price` | Fija el precio a mano (MANUAL/FUND/METAL). Body: `price` |
| DELETE | `/api/investments/holdings/{id}` | Borra la posición y sus lotes (`204`) |
| POST | `/api/investments/refresh-prices` | Refresca los precios CRYPTO de las posiciones y devuelve la lista actualizada |

### NFTs

`NftResponse` incluye `currentPurchaseValue` = `buyCryptoAmount ×` precio actual de
`buyCryptoSymbol` (cuánto vale **hoy** lo que se pagó; `null` si no hay precio):

```json
{ "id": 1, "name": "Punk", "collection": "Larva Labs", "buyCryptoSymbol": "ETH",
  "buyCryptoAmount": 2.00000000, "fiatValueAtPurchase": 4000.00, "ourCurrentValue": 8000.00,
  "currentPurchaseValue": 6000.00, "utility": "Acceso a la comunidad", "createdAt": "..." }
```

| Método | Ruta | Descripción |
|---|---|---|
| GET / POST / PUT / DELETE | `/api/investments/nfts[/{id}]` | CRUD de NFTs |

Errores comunes de inversiones: `400` validación, `404` recurso ajeno/inexistente, `409`
conflictos de clase de activo.

---

## Reparto de sueldo

El reparto trabaja sobre las **categorías de gasto personales**: el plan dice qué
porcentaje del sueldo va a cada una, y repartir suma el dinero a sus sobres (los
mismos del [presupuesto](#presupuesto-por-sobres)).

`EnvelopeResponse` (`id` es el id de la categoría):

```json
{ "id": 3, "name": "Comida", "color": "#EF4444", "percentage": 20.00,
  "balance": 400.00, "spent": 250.00, "available": 150.00 }
```

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/allocation/envelopes` | Todas las categorías de gasto activas, con su porcentaje (0 si no están en el plan) |
| PUT | `/api/allocation/envelopes` | Guarda el **plan completo**: las categorías que no vengan en la lista salen del plan. Body: `{ "envelopes": [ { "categoryId": 3, "percentage": 20.00 }, ... ] }` |
| POST | `/api/allocation/distribute` | Reparte un importe según el plan. Body: `{ "amount": 2000.00, "persist": false }` |

`distribute` devuelve el reparto por categoría, ajustando los céntimos del redondeo en
la última para que la suma cuadre con el importe. Con `persist: false` es una
simulación; con `true` suma cada parte al saldo de su sobre.

Errores: `400` validación o reparto sin plan guardado; `404` categoría inexistente,
ajena o que no es de gasto; `409` plan que no suma exactamente 100.

---

## Pagos recurrentes

`RecurringPaymentResponse`:

```json
{ "id": 1, "name": "Netflix", "amount": 13.99, "frequency": "MONTHLY",
  "categoryId": 4, "categoryName": "Ocio", "categoryColor": "#F59E0B",
  "dayOfMonth": 5, "month": null, "dayOfWeek": null,
  "nextDueDate": "2026-10-05", "monthlyEquivalent": 13.99, "endDate": null,
  "previousAmount": 12.99, "changePct": 7.7 }
```

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/recurring` | Lista los pagos recurrentes |
| POST | `/api/recurring` | Crea (`201`) |
| PUT | `/api/recurring/{id}` | Edita. Si cambia el importe, se guarda en el historial |
| DELETE | `/api/recurring/{id}` | Borra (`204`) |

Body de alta y edición: `name`, `amount`, `frequency`, `categoryId` (una categoría de
gasto), `dayOfMonth` (1–31, siempre se envía) y, según la frecuencia:

| `frequency` | Campo obligatorio adicional |
|---|---|
| `WEEKLY` | `dayOfWeek` (1 = lunes … 7 = domingo) |
| `MONTHLY` | — |
| `QUARTERLY` | `month` (1–12): un mes cualquiera en el que cae el pago |
| `ANNUAL` | `month` (1–12) |

`endDate` es opcional: un recordatorio de cuándo cancelar.

Campos calculados: `nextDueDate` (si el día no existe en el mes, el último día),
`monthlyEquivalent` (semanal × 52 / 12, trimestral / 3, anual / 12) y, cuando el
importe ha cambiado alguna vez, `previousAmount` y `changePct`.

Errores: `400` validación o campo de frecuencia ausente, `404` recurso o categoría
inexistente o ajena.

---

## Avisos

`GET /api/alerts` — avisos heurísticos para el Inicio. Las dos listas suelen venir
vacías.

```json
{
  "antExpenses": [ { "categoryId": 3, "categoryName": "Cafés", "categoryColor": "#F59E0B", "count": 12, "total": 64.80 } ],
  "forgottenSubscriptions": [ { "recurringId": 1, "name": "Gimnasio", "categoryId": 5, "categoryName": "Salud", "categoryColor": "#10B981" } ]
}
```

- **Gasto hormiga**: categoría con 8 o más compras este mes, de 10 € de media como
  máximo, que suman 50 € o más.
- **Suscripción olvidada**: pago recurrente cuya categoría no tiene gastos desde hace
  dos meses.

---

## Patrimonio

`GET /api/networth` — agrega cuentas, inversiones y deudas del usuario:

```json
{ "liquid": 1550.00, "investments": 37000.00, "investmentsHoldings": 29000.00,
  "investmentsNfts": 8000.00, "debtsInFavor": 70.00, "debtsAgainst": 300.00,
  "net": 38320.00 }
```

`net` = `liquid + investments + coupleShare + debtsInFavor − debtsAgainst`.
`coupleShare` (también en la respuesta) es la mitad del saldo de las cuentas de los
espacios del usuario.

`GET /api/networth/history?days=30` — serie diaria, de la más antigua a la más
reciente, con los mismos campos más `date`. La primera consulta del patrimonio de cada
día guarda la foto de ese día, así que los días sin abrir la app no aparecen. `days` se
ajusta al rango 1–3650.

---

## Espacios compartidos

Un espacio es un libro compartido (la cuenta conjunta de una pareja) con sus propias
categorías, cuentas y movimientos.

`SpaceResponse`: `{ "id": 7, "name": "Pareja", "myStatus": "ACTIVE", "createdBy": 1, "createdAt": "..." }`.
`myStatus`: `PENDING` (invitación sin aceptar) | `ACTIVE`.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/spaces` | Crea un espacio (`201`) con las categorías por defecto. Body: `{ "name": "Pareja" }`. Quien lo crea queda como miembro activo |
| GET | `/api/spaces` | Espacios del usuario, incluidas las invitaciones pendientes |
| POST | `/api/spaces/{id}/members` | Invita por email a un usuario ya registrado (`201`). Body: `{ "email": "..." }` |
| GET | `/api/spaces/{id}/members` | Miembros: `userId`, `email`, `name`, `status` |
| POST | `/api/spaces/{id}/accept` | Acepta la invitación (`204`) |
| POST | `/api/spaces/{id}/decline` | Rechaza la invitación (`204`) |
| DELETE | `/api/spaces/{id}/members/me` | Abandona el espacio (`204`) |

Solo un miembro activo puede invitar o ver los miembros. Errores: `404` espacio
inexistente o del que no se es miembro, o email sin cuenta; `409` usuario ya invitado
o ya miembro, o invitación ya aceptada.

---

## Lista de deseos

`GET /api/wishlist` devuelve los deseos y la suma de sus precios:

```json
{
  "items": [ { "id": 1, "name": "Auriculares", "imageUrl": null, "productUrl": "https://...",
               "comment": null, "price": 89.00, "priority": 2, "createdAt": "..." } ],
  "total": 89.00
}
```

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/wishlist` | Crea (`201`). Solo `name` es obligatorio; `priority` va de 1 a 5; `imageUrl` y `productUrl` deben empezar por `http://` o `https://` (`400` si no) |
| PUT | `/api/wishlist/{id}` | Edita |
| DELETE | `/api/wishlist/{id}` | Borra (`204`) |

---

## Tokens personales

Credenciales de larga duración para clientes que no pueden hacer login, como un atajo
de iOS. Se usan igual que el JWT: `Authorization: Bearer senda_pat_...`.

Un token personal **solo puede hacer dos cosas**: leer las categorías
(`GET /api/categories`) y apuntar un gasto (`POST /api/transactions/quick`). En
cualquier otro endpoint recibe `403`. Vive en un móvil o en un script, fuera del
control de la app, así que si se filtra no entrega las finanzas de su dueño. Crear,
listar y revocar tokens exige haber iniciado sesión.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/tokens` | Crea un token (`201`). Body: `{ "name": "Atajo iPhone" }`. Respuesta: `{ "id": 1, "name": "Atajo iPhone", "value": "senda_pat_..." }` |
| GET | `/api/tokens` | Lista: `id`, `name`, `createdAt`, `lastUsedAt`, `revokedAt` |
| DELETE | `/api/tokens/{id}` | Revoca el token (`204`) |

`value` solo aparece en la respuesta de creación: el servidor guarda su hash SHA-256 y
no puede volver a mostrarlo. Conviene crear uno por dispositivo y revocarlo si se
pierde.

---

## Flujo completo con curl

```bash
API=http://localhost:8080/api

# 1. Registro (devuelve token + user)
curl -s -X POST "$API/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email": "jordi@example.com", "password": "secreta123", "name": "Jordi"}'

# 2. Login y captura del token
TOKEN=$(curl -s -X POST "$API/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email": "jordi@example.com", "password": "secreta123"}' \
  | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")

# 3. Ver las categorías por defecto (apuntar un id de tipo EXPENSE, p. ej. Comida)
curl -s "$API/categories" -H "Authorization: Bearer $TOKEN"

# 4. Crear un movimiento (sustituir categoryId por el id real del paso 3)
curl -s -X POST "$API/transactions" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"categoryId": 1, "type": "EXPENSE", "amount": 23.50, "date": "2026-06-12", "description": "Compra semanal"}'

# 5. Listar movimientos de junio
curl -s "$API/transactions?from=2026-06-01&to=2026-06-30" \
  -H "Authorization: Bearer $TOKEN"

# 6. Resumen del mes
curl -s "$API/transactions/summary?year=2026&month=6" \
  -H "Authorization: Bearer $TOKEN"

# 7. Apuntar un gasto con un token personal (creado en la pantalla Tokens)
curl -s -X POST "$API/transactions/quick" \
  -H "Authorization: Bearer senda_pat_..." \
  -H "Content-Type: application/json" \
  -d '{"amount": 12.50, "description": "Mercadona", "categoryId": 1}'
```
