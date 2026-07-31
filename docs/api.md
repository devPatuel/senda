# API de Senda

Base: `/api` (en desarrollo, `http://localhost:8080/api`).

Autenticación por header `Authorization: Bearer <jwt>` en todos los endpoints salvo
`/api/auth/**`. El JWT expira a las 24 h.

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
| 401 | Credenciales incorrectas, token ausente/inválido/expirado |
| 404 | Recurso inexistente **o de otro usuario** (nunca 403) |
| 409 | Conflicto de estado: email ya registrado, nombre de categoría duplicado (mismo usuario y tipo), categoría inactiva al crear/editar un movimiento |
| 429 | Demasiados intentos en `/api/auth/**` (límite por IP: 10 peticiones/minuto) |

---

## Auth

Los dos endpoints son públicos y tienen **rate limiting por IP** (10 peticiones/minuto
entre ambos): al superarlo devuelven `429 Too Many Requests`. Protege contra fuerza
bruta y contra agotamiento de CPU (cada intento ejecuta BCrypt).

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
de 72 bytes, que nunca puede ser válida).

---

## Categorías

`CategoryResponse`:

```json
{ "id": 3, "name": "Comida", "type": "EXPENSE", "color": "#EF4444", "active": true }
```

### GET /api/categories

Categorías del usuario autenticado.

| Parámetro | Tipo | Descripción |
|---|---|---|
| `type` | `INCOME` \| `EXPENSE` | Opcional, filtra por tipo |
| `includeInactive` | boolean | Opcional, default `false` (solo activas) |

Respuesta `200 OK`: `[CategoryResponse]`.

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

Respuesta `201 Created`: `CategoryResponse`.

Errores: `400` validación, `409` nombre duplicado para ese usuario y tipo.

### PUT /api/categories/{id}

Body (distinto del POST):

```json
{ "name": "Mascotas", "color": "#F97316", "active": true }
```

- `type` **no se puede cambiar**: es inmutable tras la creación. Si se envía un campo
  `type` en el JSON, se ignora silenciosamente.
- `active` es opcional: si se omite (o es `null`) se conserva el valor actual.
  Enviar `active: true` es la forma de **reactivar** una categoría desactivada.

Respuesta `200 OK`: `CategoryResponse`.

Errores: `404` si no existe o es de otro usuario, `400` validación,
`409` nombre duplicado para ese usuario y tipo.

### DELETE /api/categories/{id}

- Sin movimientos asociados → borrado físico.
- Con movimientos → **desactivación** (`active = false`); los movimientos históricos se conservan.

Respuesta `204 No Content`. Errores: `404` si no existe o es de otro usuario.

---

## Movimientos

`TransactionResponse`:

```json
{
  "id": 12,
  "categoryId": 3,
  "categoryName": "Comida",
  "categoryColor": "#EF4444",
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

`description` es opcional. El `type` debe coincidir con el de la categoría.

Respuesta `201 Created`: `TransactionResponse`.

Errores: `400` validación (importe ≤ 0, tipo que no coincide con la categoría…),
`404` si la categoría no existe o es de otro usuario,
`409` si la categoría está inactiva (desactivada con el DELETE de categorías).

### PUT /api/transactions/{id}

Mismo body que el POST. Respuesta `200 OK`: `TransactionResponse`.

Errores: `404` si no existe o es de otro usuario, `400` validación,
`409` si la categoría destino está inactiva.

### DELETE /api/transactions/{id}

Respuesta `204 No Content`. Errores: `404` si no existe o es de otro usuario.

### GET /api/transactions/summary?year=&month=

Resumen mensual del usuario autenticado. `balance = totalIncome - totalExpense`.
`byCategory` solo incluye categorías con movimientos en ese mes.

Respuesta `200 OK`:

```json
{
  "year": 2026,
  "month": 6,
  "totalIncome": 1800.00,
  "totalExpense": 23.50,
  "balance": 1776.50,
  "byCategory": [
    { "categoryId": 8, "categoryName": "Nómina", "categoryColor": "#22C55E", "type": "INCOME", "total": 1800.00 },
    { "categoryId": 3, "categoryName": "Comida", "categoryColor": "#EF4444", "type": "EXPENSE", "total": 23.50 }
  ]
}
```

Errores: `400` si `year`/`month` faltan o no son válidos.

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
| GET | `/api/accounts?includeArchived=false` | Lista las cuentas (solo no archivadas por defecto) |
| GET | `/api/accounts/balance` | Saldo total de las cuentas no archivadas: `{ "total": 1550.00 }` |
| POST | `/api/accounts` | Crea una cuenta (`201`). Body: `name`, `type`, `balance`, `currency?` |
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
`pricingSource`: `CRYPTO` (auto vía CoinGecko) | `METAL` | `FUND` | `MANUAL` (estos tres,
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
  "cost": 25000.00, "marketValue": 29000.00, "pnl": 4000.00 }
```

`marketValue` y `pnl` son `null` cuando no hay `currentPrice`. `symbol` debe ser
alfanumérico.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/investments/holdings?assetClassId=` | Lista las posiciones (filtro opcional por clase) |
| POST | `/api/investments/holdings` | Crea (`201`). `400` si `quantity` 0 con `avgCost` ≠ 0 |
| GET | `/api/investments/holdings/{id}` | Una posición |
| POST | `/api/investments/holdings/{id}/buys` | Registra una compra (`201`): recalcula cantidad y coste medio y guarda un lote. Body: `quantity`, `unitPrice`, `date` |
| GET | `/api/investments/holdings/{id}/lots` | Histórico de compras |
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

## Reparto de sueldo (sobres)

`EnvelopeResponse`: `{ "id": 1, "name": "Ahorro", "percentage": 50.00, "position": 0, "balance": 700.00 }`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/allocation/envelopes` | Lista el plan de sobres con su saldo acumulado |
| PUT | `/api/allocation/envelopes` | Guarda el **plan completo** (atómico). Body: `{ "envelopes": [ { "id?": 1, "name": "Ahorro", "percentage": 50.00 }, ... ] }`. `409` si los porcentajes no suman exactamente 100 |
| POST | `/api/allocation/distribute` | Reparte un importe por sobre. Body: `{ "amount": 2000.00, "persist": false }` |

`distribute` devuelve el reparto por sobre (ajustando los céntimos del redondeo en el
último para que la suma cuadre con el importe). Con `persist=true` acumula cada parte en
el saldo del sobre. Errores: `400` validación / sin sobres definidos, `409` plan que no
suma 100.

---

## Patrimonio

`GET /api/networth` — agrega cuentas, inversiones y deudas del usuario:

```json
{ "liquid": 1550.00, "investments": 37000.00, "investmentsHoldings": 29000.00,
  "investmentsNfts": 8000.00, "debtsInFavor": 70.00, "debtsAgainst": 300.00,
  "net": 38320.00 }
```

`net` = `liquid + investments + debtsInFavor − debtsAgainst`. Solo lectura.

---

## Lista de la compra y deseos

`ShoppingItemResponse`:

```json
{ "id": 1, "listType": "WISHLIST", "name": "NAS", "estimatedPrice": 600.00,
  "envelopeId": 2, "envelopeName": "Inversión", "envelopeBalance": 800.00,
  "priority": 1, "bought": false, "feasible": true, "notes": null, "createdAt": "..." }
```

`listType`: `GROCERY` (comida, check/uncheck) | `WISHLIST` (deseos). `feasible` (solo
deseos con sobre y precio) = saldo del sobre asociado ≥ `estimatedPrice`; `null` si no
aplica. `priority`: 1–5.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/shopping/items?listType=` | Lista los items (filtro opcional por tipo) |
| POST | `/api/shopping/items` | Crea (`201`). `404` si el `envelopeId` no es del usuario |
| PUT | `/api/shopping/items/{id}` | Edita; el `listType` no se puede cambiar (`400`) |
| PATCH | `/api/shopping/items/{id}/bought` | Marca comprado/no comprado. Body: `{ "bought": true }` |
| DELETE | `/api/shopping/items/{id}` | Borra (`204`) |

Errores: `400` validación / cambio de `listType`, `404` item o sobre ajeno/inexistente.

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
```
