# Arquitectura de Senda

## Visión

Senda es una **super app personal modular**: un único producto web con apartados que se
construyen de uno en uno (gastos, libros, pagos recurrentes…). Multiusuario con datos
aislados desde el día 1. Proyecto de aprendizaje y de uso real: calidad sobre velocidad,
rigor de producción.

Esta arquitectura cubre **Fase 0 (cimientos) + Fase 1 (gastos e ingresos)**.

## Estructura del monorepo

```
senda/
├── backend/                  # Spring Boot 3.5 (Java 21, Maven) — API REST
│   ├── Dockerfile            # multi-stage: maven build → JRE 21 runtime
│   └── src/...
├── frontend/                 # React + Vite + Tailwind (JavaScript) — SPA
│   ├── Dockerfile            # multi-stage: node build → nginx runtime
│   ├── nginx.conf            # SPA fallback + proxy /api → backend
│   └── src/...
├── docker-compose.yml        # desarrollo: solo Postgres 16
├── docker-compose.prod.yml   # producción (NAS): postgres + backend + frontend
└── docs/                     # este documento, ADRs, setup local, API
```

Ver [ADR 0001](adr/0001-monorepo.md) para el porqué del monorepo.

## Backend

### Capas

**Estrictas**: `controller → service → repository`.

- **Controller**: HTTP puro. Recibe DTOs validados (`@Valid`), delega en el servicio,
  devuelve DTOs de respuesta. Sin lógica de negocio.
- **Service**: lógica de negocio, transacciones, reglas (p. ej. el `type` de una
  transacción debe coincidir con el de su categoría; una categoría con movimientos se
  desactiva en lugar de borrarse).
- **Repository**: Spring Data JPA. Nunca se invoca desde un controller.

Las entidades JPA **no se exponen** en la API: siempre DTOs de request/response.

### Módulos (paquetes)

La modularidad se expresa en paquetes por dominio. Añadir un módulo futuro (libros,
pagos recurrentes) no toca los existentes.

| Paquete | Contenido |
|---|---|
| `dev.jordi.senda.auth` | Registro, login, emisión de JWT |
| `dev.jordi.senda.user` | Entidad y repositorio de usuario |
| `dev.jordi.senda.category` | CRUD de categorías + set por defecto al registrarse |
| `dev.jordi.senda.transaction` | CRUD de movimientos, listado paginado, summary mensual |
| `dev.jordi.senda.account` | Cuentas de dinero líquido (banco/efectivo), saldo manual, archivado, saldo total |
| `dev.jordi.senda.debt` | Deudas (me deben / debo) con abonos fraccionados y pendiente calculado |
| `dev.jordi.senda.investment` | Clases de activo, posiciones (coste medio), lotes, NFTs y servicio de precios |
| `dev.jordi.senda.allocation` | Reparto de sueldo por sobres (suma 100%) y saldos acumulados |
| `dev.jordi.senda.networth` | Patrimonio neto: agrega cuentas, inversiones y deudas (solo lectura, sin tablas) |
| `dev.jordi.senda.wishlist` | Lista de deseos (nombre, foto, enlace, precio estimado) |
| `dev.jordi.senda.common` | Config, seguridad compartida, manejo global de errores |

### Manejo de errores

`@RestControllerAdvice` global con respuesta JSON consistente:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "campo": "mensaje" } }
```

`fieldErrors` solo aparece en errores de validación. Nunca se devuelven stack traces.

Convención de códigos: 201 en alta, 400 validación, 401 credenciales o token inválido,
404 recurso inexistente **o de otro usuario** (nunca 403, para no revelar existencia),
409 conflicto de estado (email ya registrado, nombre de categoría duplicado para ese
usuario y tipo, categoría inactiva al crear/editar un movimiento).

### Dinero y fechas

- Dinero: `BigDecimal` en Java, `NUMERIC(12,2)` en Postgres. Nunca float/double.
  Las finanzas (cuentas, deudas, sobres, NFTs) usan `NUMERIC(14,2)`; las cantidades y
  precios de activos, `NUMERIC(20,8)` (criptomonedas con muchos decimales).
- Fecha de movimiento: `LocalDate` (`DATE`). Timestamps: `created_at`.

### Servicios externos

El módulo `investment` consulta precios de criptomonedas en **CoinGecko** (API pública
gratuita, **sin API key**) mediante `RestClient`, con **timeouts de 5 s** y degradación
silenciosa ante fallos (un error de red no rompe el endpoint, devuelve "sin precio"). Los
precios se **cachean en memoria** (TTL 15 min para aciertos, 2 min para fallos —
*negative caching*) para no agotar el rate limit del proveedor. Metales (oro/plata) y
fondos por ISIN se actualizan **a mano** por falta de una API fiable y gratuita
(ver [ADR 0007](adr/0007-precios-inversion-cripto-auto-resto-manual.md)).

## Modelo de datos

Gestionado con migraciones Flyway desde el día 1 ([ADR 0003](adr/0003-flyway-desde-dia-1.md)).

```
users         id (PK), email (UNIQUE, NOT NULL), password_hash, name, created_at

categories    id (PK), user_id (FK users, NOT NULL), name, type (INCOME|EXPENSE),
              color, active (boolean, default true)
              UNIQUE (user_id, name, type)

transactions  id (PK), user_id (FK users, NOT NULL), category_id (FK categories, NOT NULL),
              type (INCOME|EXPENSE), amount NUMERIC(12,2) > 0, date (DATE),
              description (nullable), created_at
              INDEX (user_id, date)
```

Módulos de finanzas (migraciones **V3–V7**):

```
accounts            id, user_id, name, type (BANK|CASH), balance NUMERIC(14,2),
                    currency (default 'EUR'), archived, created_at                       (V3)

debts               id, user_id, direction (THEY_OWE_ME|I_OWE), counterparty, concept,
                    original_amount NUMERIC(14,2) > 0, date, settled, created_at          (V4)
debt_payments       id, debt_id (FK debts ON DELETE CASCADE), user_id,
                    amount NUMERIC(14,2) > 0, date, note (nullable), created_at

asset_classes       id, user_id, name, pricing_source (CRYPTO|METAL|FUND|MANUAL),
                    created_at — UNIQUE (user_id, name)                                   (V5)
holdings            id, user_id, asset_class_id (FK), symbol, name,
                    quantity NUMERIC(20,8), avg_cost NUMERIC(20,8),
                    current_price NUMERIC(20,8) (nullable), last_priced_at (nullable)
holding_lots        id, holding_id (FK holdings ON DELETE CASCADE), user_id,
                    quantity NUMERIC(20,8), unit_price NUMERIC(20,8), date  — histórico de compras
nfts                id, user_id, name, collection, buy_crypto_symbol,
                    buy_crypto_amount NUMERIC(20,8), fiat_value_at_purchase NUMERIC(14,2),
                    our_current_value NUMERIC(14,2), utility, created_at

allocation_envelopes id, user_id, name, percentage NUMERIC(5,2), position, created_at     (V6)
envelope_balances   id, envelope_id (FK ON DELETE CASCADE, UNIQUE), user_id,
                    balance NUMERIC(14,2) — saldo acumulado por sobre

wishlist_items      id, user_id, name, image_url, product_url, estimated_price,
                    comment, created_at                                                   (V22)
```

Reglas de dominio de finanzas:

- **Cuentas**: saldo manual; archivar oculta de listados y del saldo total sin borrar.
- **Deudas**: pendiente = `original_amount − SUM(pagos)`; un pago no puede dejar el pendiente
  negativo; al llegar a 0 se marca `settled` (se revierte al borrar un pago).
- **Inversiones**: coste medio ponderado recalculado en cada compra (ver
  [ADR 0008](adr/0008-coste-medio-ponderado-compras.md)); precios CRYPTO automáticos vía
  CoinGecko, METAL y FUND manuales (ver [ADR 0007](adr/0007-precios-inversion-cripto-auto-resto-manual.md)).
  Las clases de activo por defecto (Cripto, Fondos, Oro, Plata) se siembran al registrarse.
- **Reparto**: los porcentajes de los sobres deben sumar exactamente 100
  (ver [ADR 0009](adr/0009-reparto-sobres-suma-100.md)).
- **Patrimonio**: agregación de solo lectura (ver [ADR 0010](adr/0010-calculo-patrimonio-neto.md)).
- **Deseos** (`wishlist`): lista simple de cosas que quieres comprar, sin cálculo asociado.

Reglas de dominio:

- Al registrarse, se **copia al usuario un set de categorías por defecto** (7 de gasto,
  2 de ingreso). Cada usuario es dueño de las suyas y puede personalizarlas.
- Una categoría con movimientos **no se borra: se desactiva** (`active = false`). Solo
  se borra físicamente si no tiene movimientos.
- El `type` del movimiento debe coincidir con el `type` de su categoría (validado en servicio).

## Autenticación (JWT stateless)

Ver [ADR 0002](adr/0002-jwt-stateless.md).

```
┌──────────┐  POST /api/auth/login (email, password)   ┌──────────┐
│ Frontend │ ─────────────────────────────────────────▶│ Backend  │
│          │ ◀──────── { token, user } ────────────────│          │
│          │                                           │          │
│ localStorage("senda_token")                          │          │
│          │  GET /api/... Authorization: Bearer <jwt> │          │
│          │ ─────────────────────────────────────────▶│ filtro   │
│          │                                           │ JWT →    │
│          │ ◀──────── datos del usuario del token ────│ user_id  │
└──────────┘                                           └──────────┘
```

- HS256 (jjwt 0.12.x), claim `sub` = userId, expiración 24 h.
- Secreto en la propiedad `senda.jwt.secret`, leída del env `SENDA_JWT_SECRET`.
  **Sin default**: si falta la variable, el backend no arranca (un secreto por defecto
  committeado permitiría forjar tokens de cualquier usuario). En desarrollo se usa el
  perfil `local` (`application-local.yml`).
- Contraseñas con BCrypt (máx. 72 bytes UTF-8, validado en los DTOs).
  Endpoints públicos: solo `/api/auth/**` (y `/actuator/health`), con rate limiting
  en memoria por IP (10 peticiones/minuto) para frenar fuerza bruta y abuso de CPU.
- **Aislamiento multiusuario**: toda consulta filtra por el `user_id` extraído del token,
  nunca por parámetros del cliente. Un recurso de otro usuario responde 404.
- 401 de la API en el frontend → logout y redirección a login.

## Frontend

React + Vite + Tailwind con componentes propios, en JavaScript sin TypeScript
([ADR 0005](adr/0005-javascript-sin-typescript.md)). Responsive (uso desde móvil).

- React Router con rutas protegidas.
- Hook `useAuth` + contexto; token en `localStorage` (clave `senda_token`), adjuntado
  por un cliente HTTP central (wrapper de `fetch`).
- Base URL de la API: `import.meta.env.VITE_API_URL` (default `http://localhost:8080/api`;
  en producción se construye con `/api` y nginx hace de proxy).
- Pantallas: Login/Registro, Dashboard (balance del mes), Movimientos (lista paginada
  con filtros + formulario), Categorías.
- Importes formateados con `Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' })`.

## Despliegue

- **Desarrollo**: `docker-compose.yml` levanta solo Postgres; back y front corren en
  local con hot-reload (ver [setup-local.md](setup-local.md)).
- **Producción (futuro NAS)**: `docker-compose.prod.yml` levanta los tres servicios.
  nginx sirve la SPA y hace proxy de `/api/` al backend; Postgres no expone puerto al host.
  El backend expone `/actuator/health` (sin autenticación, solo estado) para el
  healthcheck del compose; el frontend no arranca hasta que el backend está sano.

> **⚠️ TLS obligatorio antes de uso real.** El stack expone HTTP plano en el puerto
> `8088`: credenciales de login/registro y el JWT (`Authorization: Bearer`) viajarían
> en claro por la LAN. El despliegue real debe ir **detrás del reverse proxy del NAS
> con TLS terminado allí** (y, una vez con HTTPS, añadir HSTS en ese proxy). El
> `nginx.conf` del frontend ya añade cabeceras de seguridad al SPA
> (`X-Content-Type-Options`, `X-Frame-Options`, CSP); la CSP importa especialmente
> porque el token vive en `localStorage` y un XSS es el vector directo para robarlo.

## Decisiones clave (ADRs)

| ADR | Decisión |
|---|---|
| [0001](adr/0001-monorepo.md) | Monorepo backend + frontend + docs |
| [0002](adr/0002-jwt-stateless.md) | JWT stateless en lugar de sesiones |
| [0003](adr/0003-flyway-desde-dia-1.md) | Flyway desde el día 1 |
| [0004](adr/0004-paginacion-en-servidor.md) | Paginación en servidor desde el día 1 |
| [0005](adr/0005-javascript-sin-typescript.md) | JavaScript sin TypeScript en el frontend |
| [0006](adr/0006-spring-boot-3.md) | Spring Boot 3.5.15 y no 4.x |
| [0007](adr/0007-precios-inversion-cripto-auto-resto-manual.md) | Precios: cripto automática (CoinGecko), metales y fondos manuales |
| [0008](adr/0008-coste-medio-ponderado-compras.md) | Coste medio ponderado en las compras de inversiones |
| [0009](adr/0009-reparto-sobres-suma-100.md) | Reparto de sueldo: los sobres suman exactamente 100% |
| [0010](adr/0010-calculo-patrimonio-neto.md) | Cálculo del patrimonio neto como agregación de solo lectura |
