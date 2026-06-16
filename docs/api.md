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

Errores: `409` email duplicado, `400` validación.

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
