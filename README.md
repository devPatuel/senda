# Senda

App web de finanzas personales para usar en casa: apuntas o importas tus movimientos,
repartes el sueldo por categorías y ves de un vistazo cuánto tienes, cuánto debes y en
qué se te va el dinero. Tus datos se quedan en tu máquina: no hay servicio en la nube
ni conexión con el banco.

> [!WARNING]
> **Senda no está terminada y puede tener fallos.** La hago para mi propio uso, así que
> es posible que no encaje con tu forma de llevar las cuentas. Por eso el código está a
> la vista: puedes usarlo y cambiarlo como quieras, sin fines comerciales. Lo que no
> puedes hacer es cobrar por él ni usarlo en un negocio. Detalles en [Licencia](#licencia).

Es un proyecto personal, en uso real desde septiembre de 2026 y hecho también para
aprender. Está pensado para una persona o una pareja, no para dar servicio a terceros.
La interfaz está en español y trabaja en euros.

## Qué hace

- **Movimientos**: gastos e ingresos por categoría, con filtros, resumen del mes,
  vista anual y tendencia de los últimos meses.
- **Importar extractos**: subes el CSV o el Excel (`.xlsx`) del banco, eliges qué
  columna es cada cosa, revisas la previsualización y se crean los movimientos. Los
  que ya existían se detectan y no se duplican.
- **Presupuesto por sobres**: asignas dinero a cada categoría de gasto y ves lo que
  queda en cada una. El reparto del sueldo lo hace por porcentajes.
- **Cuentas, deudas y patrimonio**: saldo de cada cuenta, lo que debes y te deben
  (con abonos parciales) y el patrimonio neto con su histórico.
- **Inversiones**: posiciones con coste medio ponderado, historial de compras y
  recompensas, y precio de las criptomonedas actualizado solo.
- **Pagos recurrentes**: suscripciones y recibos con su próxima fecha y su coste
  mensual equivalente.
- **Espacio compartido**: una cuenta conjunta con tu pareja, con sus propias
  categorías y movimientos, separada de lo personal de cada uno.
- **Traspasos**: mover dinero entre cuentas propias no cuenta como ingreso ni gasto.
- **Avisos**: gastos pequeños que suman mucho y suscripciones que sigues pagando sin
  usar.
- **Tokens personales**: para apuntar un gasto desde un atajo de iOS o un script sin
  usar tu contraseña. Solo sirven para eso: no pueden leer tus datos.
- **Lista de deseos**.

## Con qué está hecha

| Capa | Tecnología |
|---|---|
| API | Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA |
| Base de datos | PostgreSQL 16, migraciones con Flyway |
| Web | React 19, Vite, Tailwind CSS, React Router |
| Tests | JUnit 5 + Testcontainers (API), Vitest + Testing Library (web) |
| Despliegue | Docker Compose, nginx |

## Probarla en tu máquina

Necesitas **Java 21**, **Node 22 o superior** y **Docker**.

En macOS basta un doble clic en `start.command` (o `./start.command` desde la
terminal): levanta la base de datos, la API y la web, y abre el navegador en
<http://localhost:5173>. `stop.command` lo apaga todo. Los datos se conservan entre
arranques.

Si prefieres hacerlo a mano, o no usas macOS, los pasos están en
[docs/setup-local.md](docs/setup-local.md). La guía está escrita para macOS, pero los
tres comandos de arranque son los mismos en cualquier sistema con Java, Node y Docker.

La primera vez, pulsa **Regístrate** en la pantalla de entrada y crea tu cuenta. Con
ella vienen unas categorías por defecto que puedes cambiar.

## Importar el extracto del banco

En **Importar**, elige el archivo que descargas de tu banca online. Senda busca sola
la cabecera de la tabla (aunque el banco ponga los datos de la cuenta encima) y
propone las columnas de fecha, concepto e importe; tú las confirmas.

Lo que hay que saber:

- El importe tiene que venir en **una sola columna con signo**: negativo para los
  gastos, positivo para los ingresos. Los extractos con columnas separadas de cargo y
  abono no están soportados todavía.
- Fechas en formato `dd/mm/aaaa` o `aaaa-mm-dd`.
- Si alguna fila no se puede leer, la previsualización te dice cuántas.

## Instalarla en un servidor de casa

`docker-compose.prod.yml` levanta la base de datos, la API y la web (nginx) en el
puerto **8088**:

```bash
export SENDA_DB_PASSWORD='una-contraseña-larga'
export SENDA_JWT_SECRET="$(openssl rand -base64 48)"
docker compose -f docker-compose.prod.yml up -d --build
```

Guarda los dos valores en tu gestor de contraseñas: si el secreto JWT cambia, todo
el mundo tiene que volver a iniciar sesión, y la contraseña de la base de datos
queda fijada la primera vez que arranca.

| Variable | Obligatoria | Para qué sirve |
|---|---|---|
| `SENDA_DB_PASSWORD` | Sí | Contraseña de PostgreSQL |
| `SENDA_JWT_SECRET` | Sí | Firma de las sesiones. Sin ella la API no arranca |
| `SENDA_CORS_ORIGINS` | Si no entras por `http://localhost:8088` | Direcciones desde las que se abre la app, con su puerto y separadas por comas. Si falta la tuya, podrás ver los datos pero no guardar nada (error 403) |
| `SENDA_REGISTRATION_ENABLED` | No | `true` permite crear cuentas. Por defecto está cerrado |

Dos cosas que conviene hacer bien:

1. **Crear la primera cuenta.** El registro viene cerrado, porque cualquiera que
   llegue a la app por tu red podría darse de alta. Arranca una vez con
   `SENDA_REGISTRATION_ENABLED=true`, crea tu cuenta (y la de tu pareja) y vuelve a
   arrancar sin la variable.
2. **No la publiques en internet.** La pila sirve HTTP sin cifrar. Para entrar desde
   fuera de casa, usa una VPN privada: el paso a paso con Tailscale está en
   [docs/runbook-acceso-remoto-tailscale.md](docs/runbook-acceso-remoto-tailscale.md).

## Copias de seguridad

```bash
./scripts/backup-db.sh            # pila del servidor
./scripts/backup-db.sh --local    # la que arranca start.command
```

Deja un `.sql.gz` con fecha en `~/Documents/Senda/backups`, fuera del repositorio a
propósito: el volcado contiene tus datos reales. Para recuperar una copia,
`./scripts/restore-db.sh [--local] <archivo>` (sobrescribe la base de datos y pide
confirmación).

## Tests

```bash
cd backend && ./mvnw test     # necesita Docker en marcha
cd frontend && npm test
```

## Documentación

| Documento | Contenido |
|---|---|
| [docs/arquitectura.md](docs/arquitectura.md) | Módulos, modelo de datos, autenticación y despliegue |
| [docs/api.md](docs/api.md) | Todos los endpoints, con ejemplos |
| [docs/setup-local.md](docs/setup-local.md) | Montar el entorno de desarrollo y problemas habituales |
| [docs/runbook-acceso-remoto-tailscale.md](docs/runbook-acceso-remoto-tailscale.md) | Servidor en casa, acceso remoto y backups |
| [docs/adr/](docs/adr/) | Decisiones técnicas y su porqué |

## Limitaciones conocidas

- Una sola moneda: todo se suma como euros.
- Los saldos de las cuentas se actualizan a mano; importar un extracto crea los
  movimientos, no cambia el saldo.
- Solo las criptomonedas tienen precio automático. Fondos, oro y plata se actualizan
  a mano.
- La contraseña se cambia desde la app, pero no hay recuperación por correo si se
  olvida.
- El espacio compartido cubre categorías, cuentas y movimientos. Deudas, inversiones
  y pagos recurrentes son siempre personales.

## Licencia

[PolyForm Noncommercial 1.0.0](LICENSE). Puedes usar, modificar y compartir Senda sin
ánimo de lucro, siempre que acompañes el código con la licencia y su aviso de copyright.
Cualquier uso comercial (venderla, cobrar por instalarla o usarla en una empresa)
necesita mi permiso.
