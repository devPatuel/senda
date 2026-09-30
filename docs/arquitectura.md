# Arquitectura de Senda

## Visión

Senda es una app web de **finanzas personales** construida por módulos: cada apartado
(movimientos, cuentas, inversiones, pagos recurrentes…) es un paquete independiente.
Multiusuario con datos aislados desde el día 1, aunque en la práctica la usan una o dos
personas. Proyecto de aprendizaje y de uso real: calidad sobre velocidad, rigor de
producción.

El alcance está acotado a finanzas a propósito: en septiembre de 2026 se retiraron los
módulos de lista de la compra, catálogo de productos, reglas de categorización y hábitos
(migración `V27`).

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
├── start.command             # macOS: levanta todo en local con un doble clic
├── stop.command              # macOS: lo apaga
├── scripts/                  # backup y restore de la base de datos
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

La modularidad se expresa en paquetes por dominio. Añadir un módulo no toca los
existentes.

| Paquete | Contenido |
|---|---|
| `dev.jordi.senda.auth` | Registro, login, emisión de JWT |
| `dev.jordi.senda.apitoken` | Tokens personales de acceso (atajos, scripts): se guarda solo su hash |
| `dev.jordi.senda.user` | Entidad y repositorio de usuario |
| `dev.jordi.senda.category` | CRUD de categorías, set por defecto al registrarse y presupuesto por sobres (saldo asignado a cada categoría de gasto) |
| `dev.jordi.senda.transaction` | CRUD de movimientos, listado paginado, resumen mensual y anual, tendencia y captura rápida |
| `dev.jordi.senda.imports` | Importación de extractos: previsualización con detección de duplicados y alta en lote |
| `dev.jordi.senda.recurring` | Pagos recurrentes (semanal, mensual, trimestral, anual): próxima fecha, coste mensual equivalente e historial de cambios de importe |
| `dev.jordi.senda.alerts` | Avisos del Inicio: gastos hormiga y suscripciones sin uso (solo lectura, sin tablas) |
| `dev.jordi.senda.space` | Espacios compartidos, sus miembros y la guardia de acceso `SpaceAccess` |
| `dev.jordi.senda.account` | Cuentas de dinero líquido (banco/efectivo), saldo manual, archivado, saldo total |
| `dev.jordi.senda.debt` | Deudas (me deben / debo) con abonos fraccionados y pendiente calculado |
| `dev.jordi.senda.investment` | Clases de activo, posiciones (coste medio), lotes, NFTs y servicio de precios |
| `dev.jordi.senda.allocation` | Reparto de sueldo por porcentajes (suman 100%) sobre las categorías de gasto |
| `dev.jordi.senda.networth` | Patrimonio neto: agrega cuentas, inversiones y deudas, y guarda una foto diaria para el histórico |
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
usuario y tipo, categoría inactiva al crear/editar un movimiento), 429 demasiados
intentos en `/api/auth/**`. El único 403 de la API es el registro cerrado, que no
depende de ningún recurso.

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
*negative caching*) para no agotar el rate limit del proveedor. Si CoinGecko no da
precio para un símbolo (o bloquea la IP), se pregunta a **Coinbase** solo por los que
faltan (`FallbackCryptoPriceProvider`). Metales (oro/plata) y
fondos por ISIN se actualizan **a mano** por falta de una API fiable y gratuita
(ver [ADR 0007](adr/0007-precios-inversion-cripto-auto-resto-manual.md)).

## Modelo de datos

Gestionado con migraciones Flyway desde el día 1 ([ADR 0003](adr/0003-flyway-desde-dia-1.md)).

```
users         id (PK), email (UNIQUE, NOT NULL), password_hash, name, created_at,
              token_version (sube al cambiar la contraseña)

categories    id (PK), user_id (FK users, NOT NULL), space_id (FK spaces, nullable),
              name, type (INCOME|EXPENSE), color, emoji, active (default true),
              fixed (gasto fijo), is_transfer (traspaso), target_percentage (reparto)
              UNIQUE (user_id, name, type) en las personales;
              UNIQUE (space_id, name, type) en las de un espacio

transactions  id (PK), user_id (FK users, NOT NULL), space_id (FK spaces, nullable),
              category_id (FK categories, NOT NULL),
              type (INCOME|EXPENSE), amount NUMERIC(12,2) > 0, date (DATE),
              description VARCHAR(500) (nullable), created_at
              INDEX (user_id, date)
```

`space_id` nulo significa "personal"; con valor, el recurso pertenece a ese espacio
(ver [Personal y espacios](#personal-y-espacios)).

Módulos de finanzas (entre paréntesis, la migración que crea cada tabla):

```
accounts            id, user_id, space_id (nullable), name, type (BANK|CASH),
                    balance NUMERIC(14,2), currency (default 'EUR'), archived,
                    created_at                                                            (V3)

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
                    quantity NUMERIC(20,8), unit_price NUMERIC(20,8), date,
                    kind (BUY|REWARD)  — histórico de compras y recompensas
nfts                id, user_id, name, collection, buy_crypto_symbol,
                    buy_crypto_amount NUMERIC(20,8), fiat_value_at_purchase NUMERIC(14,2),
                    our_current_value NUMERIC(14,2), utility, created_at

category_balances   id, category_id (FK ON DELETE CASCADE, UNIQUE), user_id,
                    balance NUMERIC(14,2), target_amount (nullable)
                    — el "sobre" de cada categoría de gasto                               (V8)

recurring_payments  id, user_id, name, amount NUMERIC(12,2) > 0,
                    frequency (WEEKLY|MONTHLY|QUARTERLY|ANNUAL), category_id (FK),
                    day_of_month, month (nullable), day_of_week (nullable),
                    end_date (nullable), created_at                                       (V9)
recurring_amount_history  id, recurring_id (FK ON DELETE CASCADE), amount, changed_at     (V14)

net_worth_snapshots id, user_id, snapshot_date, net, liquid, investments,
                    debts_in_favor, debts_against, couple_share
                    — UNIQUE (user_id, snapshot_date)                                     (V13)

spaces              id, name, created_by (FK users), created_at                           (V16)
space_members       id, space_id (FK ON DELETE CASCADE), user_id,
                    status (PENDING|ACTIVE), joined_at — UNIQUE (space_id, user_id)

api_tokens          id, user_id (FK ON DELETE CASCADE), token_hash (SHA-256, UNIQUE),
                    name, created_at, last_used_at, revoked_at                            (V19)

wishlist_items      id, user_id, space_id (nullable), name, image_url, product_url,
                    comment, price, priority, created_at                                  (V22)
```

Las tablas del primer reparto por sobres (`allocation_envelopes`, `envelope_balances`)
se sustituyeron en `V8` por `category_balances`: el sobre dejó de ser una entidad aparte
y pasó a ser el saldo de una categoría de gasto.

Reglas de dominio de finanzas:

- **Cuentas**: saldo manual; archivar oculta de listados y del saldo total sin borrar.
- **Deudas**: pendiente = `original_amount − SUM(pagos)`; un pago no puede dejar el pendiente
  negativo; al llegar a 0 se marca `settled` (se revierte al borrar un pago).
- **Inversiones**: coste medio ponderado recalculado en cada compra (ver
  [ADR 0008](adr/0008-coste-medio-ponderado-compras.md)); precios CRYPTO automáticos vía
  CoinGecko, METAL y FUND manuales (ver [ADR 0007](adr/0007-precios-inversion-cripto-auto-resto-manual.md)).
  Las clases de activo por defecto (Cripto, Fondos, Oro, Plata) se siembran al registrarse.
  Un lote puede ser una compra (`BUY`) o una recompensa (`REWARD`, p. ej. staking),
  que entra a precio de mercado del día.
- **Presupuesto por sobres**: cada categoría de gasto tiene un saldo asignado
  (`category_balances`). Lo disponible es lo asignado menos lo gastado, y puede ser
  negativo. "Por asignar" = saldo de las cuentas − lo que queda en los sobres.
- **Reparto**: el plan es el conjunto de categorías de gasto con `target_percentage`;
  los porcentajes deben sumar exactamente 100
  (ver [ADR 0009](adr/0009-reparto-sobres-suma-100.md)). Repartir un importe lo suma al
  saldo de cada categoría; el último sobre absorbe el céntimo del redondeo.
- **Patrimonio**: agregación de cuentas, inversiones y deudas (ver
  [ADR 0010](adr/0010-calculo-patrimonio-neto.md)). Cada miembro suma la mitad del
  saldo de las cuentas de sus espacios. La primera consulta de cada día guarda una foto
  en `net_worth_snapshots` (sin tarea programada); los días sin abrir la app no tienen
  punto en el histórico.
- **Recurrentes**: la próxima fecha se calcula al consultar, no se guarda. Un día que
  no existe en el mes (el 31 en febrero) se ajusta al último día. Cada cambio de importe
  deja una entrada en el historial para mostrar cuánto ha subido.
- **Avisos**: reglas conservadoras calculadas al vuelo. Gasto hormiga: una categoría
  con 8 o más compras en el mes, de 10 € de media como máximo, que suman 50 € o más.
  Suscripción olvidada: un pago recurrente cuya categoría lleva dos meses sin gastos.
- **Deseos** (`wishlist`): lista simple de cosas que quieres comprar, sin cálculo asociado.

Reglas de dominio:

- Al registrarse, se **copia al usuario un set de categorías por defecto** (7 de gasto,
  2 de ingreso). Cada usuario es dueño de las suyas y puede personalizarlas.
- Una categoría con movimientos **no se borra: se desactiva** (`active = false`). Solo
  se borra físicamente si no tiene movimientos.
- El `type` del movimiento debe coincidir con el `type` de su categoría (validado en servicio).
- **Traspasos**: una categoría marcada como traspaso (`is_transfer`) representa dinero
  que se mueve entre cuentas propias, como la aportación a la cuenta conjunta. Sus
  movimientos quedan fuera de los totales de ingresos y gastos y se resumen aparte. Es
  una marca de la categoría, no un tercer tipo de movimiento.
- **Importación**: el navegador lee el archivo (CSV o `.xlsx`) y envía las filas ya
  interpretadas; el archivo nunca llega al servidor. Los duplicados se detectan por
  (fecha, importe, descripción) dos veces: al previsualizar, para avisar, y otra vez al
  confirmar, para que una previsualización antigua no cree movimientos repetidos.

### Personal y espacios

Un **espacio** es un libro compartido entre varios usuarios (la cuenta conjunta de una
pareja). Categorías, cuentas y movimientos llevan un `space_id` opcional: nulo es
personal, con valor pertenece al espacio. Deudas, inversiones y pagos recurrentes son
siempre personales.

- Toda operación sobre un recurso con `space_id` pasa por `SpaceAccess`: solo un
  miembro **ACTIVE** del espacio puede verlo o tocarlo. Quien no lo es recibe 404, igual
  que con un recurso de otro usuario.
- Se entra por invitación: un miembro activo invita por email (`PENDING`) y el invitado
  acepta o rechaza.
- Un movimiento solo puede usar una categoría de su mismo ámbito, y el ámbito de un
  movimiento existente no se puede cambiar al editarlo.
- Al crear un espacio se le copian las categorías por defecto.

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
- **Cierre de sesiones**: el token lleva además la versión (`ver`) con la que se emitió
  y el filtro la compara con `users.token_version` en cada petición. Cambiar la
  contraseña sube la versión, así que todas las sesiones anteriores dejan de valer al
  instante. Cuesta una consulta por petición: es la única excepción al "sin estado" de
  [ADR 0002](adr/0002-jwt-stateless.md), asumida porque sin ella una sesión robada
  seguiría viva hasta 24 h después de cambiar la contraseña.
- Secreto en la propiedad `senda.jwt.secret`, leída del env `SENDA_JWT_SECRET`.
  **Sin default**: si falta la variable, el backend no arranca (un secreto por defecto
  committeado permitiría forjar tokens de cualquier usuario). En desarrollo se usa el
  perfil `local` (`application-local.yml`).
- Contraseñas con BCrypt (máx. 72 bytes UTF-8, validado en los DTOs).
  Endpoints públicos: solo el registro y el login (y `/actuator/health`). Todo
  `/api/auth/**` tiene rate limiting en memoria por IP (10 peticiones/minuto) para
  frenar fuerza bruta y abuso de CPU. Detrás de nginx todas las peticiones llegan con
  la IP del proxy, así que en producción la IP real se lee de `X-Real-IP`
  (`SENDA_CLIENT_IP_HEADER`), que nginx sobrescribe y el cliente no puede falsificar
  porque el puerto del backend no se publica.
- El login hace una comprobación BCrypt también cuando el email no existe: responder
  más rápido en ese caso delataría qué emails tienen cuenta.
- **Aislamiento multiusuario**: toda consulta filtra por el `user_id` extraído del token,
  nunca por parámetros del cliente. Un recurso de otro usuario responde 404.
- **Registro cerrado por defecto** (`SENDA_REGISTRATION_ENABLED`): cualquier despliegue
  es alcanzable por toda su red, así que el alta solo se abre para crear las cuentas.
  El perfil `local` lo trae abierto.
- **Tokens personales** (`apitoken`): para clientes que no pueden hacer login, como un
  atajo de iOS. Van en la misma cabecera `Authorization: Bearer` y se distinguen del JWT
  por el prefijo `senda_pat_`. El valor se muestra una sola vez al crearlo; en la base
  de datos solo queda su hash SHA-256, así que una fuga de la base no entrega tokens
  utilizables. No caducan: se revocan desde la pantalla Tokens. Entran con el rol
  `TOKEN`, que solo puede leer categorías y usar la captura rápida; todo lo demás
  exige el rol `USER` de una sesión con contraseña.
- 401 de la API en el frontend → logout y redirección a login.

## Frontend

React + Vite + Tailwind con componentes propios, en JavaScript sin TypeScript
([ADR 0005](adr/0005-javascript-sin-typescript.md)). Responsive (uso desde móvil).

- React Router con rutas protegidas.
- Hook `useAuth` + contexto; token en `localStorage` (clave `senda_token`), adjuntado
  por un cliente HTTP central (wrapper de `fetch`).
- Base URL de la API: `import.meta.env.VITE_API_URL` (default `http://localhost:8080/api`;
  en producción se construye con `/api` y nginx hace de proxy).
- Pantallas: Login y Registro; Inicio (resumen del mes, ritmo de gasto, comparación
  con el mes anterior, tendencia, vista anual y avisos), Patrimonio, Pareja (espacio
  compartido); Movimientos, Recurrentes, Importar; Reparto, Categorías (con el
  presupuesto por sobres), Deseos; Cuentas, Inversiones, Deudas; Tokens y Contraseña.
- Los extractos se leen en el navegador: `lib/csv.js` para CSV y `lib/xlsx.js` para
  Excel (librería `read-excel-file`, cargada solo al importar un `.xlsx`).
- Importes formateados con `Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' })`.

## Despliegue

- **Desarrollo y uso en local**: `docker-compose.yml` levanta solo Postgres; back y
  front corren en local con hot-reload (ver [setup-local.md](setup-local.md)). En
  macOS, `start.command` lo arranca todo y guarda el secreto JWT fuera del repo, en
  `~/Documents/Senda/jwt-secret`. Postgres y la API escuchan solo en `127.0.0.1`: el
  perfil `local` tiene el registro abierto y una contraseña de base de datos conocida,
  y no debe quedar al alcance de la red a la que esté conectado el portátil.
- **Producción (futuro NAS)**: `docker-compose.prod.yml` levanta los tres servicios.
  nginx sirve la SPA y hace proxy de `/api/` al backend; Postgres no expone puerto al host.
  El backend expone `/actuator/health` (sin autenticación, solo estado) para el
  healthcheck del compose; el frontend no arranca hasta que el backend está sano.
  Variables: `SENDA_DB_PASSWORD` y `SENDA_JWT_SECRET` (obligatorias),
  `SENDA_CORS_ORIGINS` (las URL desde las que se abre la app; una que falte recibe 403
  en cada escritura) y `SENDA_REGISTRATION_ENABLED`.
- **Backups**: `scripts/backup-db.sh` y `scripts/restore-db.sh`, con `--local` para la
  base de desarrollo. Los volcados van fuera del repo porque contienen datos reales.

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
| [0006](adr/0006-spring-boot-3.md) | Spring Boot 3.5.x y no 4.x |
| [0007](adr/0007-precios-inversion-cripto-auto-resto-manual.md) | Precios: cripto automática (CoinGecko), metales y fondos manuales |
| [0008](adr/0008-coste-medio-ponderado-compras.md) | Coste medio ponderado en las compras de inversiones |
| [0009](adr/0009-reparto-sobres-suma-100.md) | Reparto de sueldo: los sobres suman exactamente 100% |
| [0010](adr/0010-calculo-patrimonio-neto.md) | Cálculo del patrimonio neto como agregación de solo lectura |
| [0011](adr/0011-catalogo-productos-y-listas.md) | Catálogo de productos y listas — **obsoleto**: el módulo se retiró; queda la lista de deseos |
