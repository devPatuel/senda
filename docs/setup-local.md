# Setup local (macOS)

De cero a la app corriendo en el navegador.

## Requisitos

| Herramienta | Instalación | Notas |
|---|---|---|
| Java 21 | `brew install openjdk@21` | **Keg-only**: Homebrew no lo enlaza en el PATH, hay que exportar `JAVA_HOME` (ver abajo) |
| Maven | `brew install maven` (opcional) | El repo incluye `./mvnw`, no hace falta Maven global |
| Node 22+ | `brew install node` | Incluye npm |
| Docker Desktop | https://www.docker.com/products/docker-desktop/ | Para Postgres y Testcontainers |

Export necesario para **cualquier** comando Maven (openjdk@21 es keg-only en Homebrew):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

Consejo: añádelo a `~/.zshrc` para no repetirlo en cada terminal.

## Arrancar la app

### 1. Base de datos

Desde la raíz del repo:

```bash
docker compose up -d
```

Levanta Postgres 16 en `localhost:5432` (db/usuario/contraseña: `senda`/`senda`/`senda`,
solo desarrollo) con volumen persistente. Comprobar que está sano:

```bash
docker compose ps   # debe mostrar "healthy"
```

### 2. Backend

```bash
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./mvnw spring-boot:run
```

API en `http://localhost:8080`. Flyway aplica las migraciones al arrancar.

### 3. Frontend

En otra terminal:

```bash
cd frontend
npm install
npm run dev
```

### 4. Abrir la app

http://localhost:5173 — registrarse, hacer login y empezar a crear movimientos.

## Tests

Backend (JUnit 5 + MockMvc + Testcontainers; Docker debe estar corriendo):

```bash
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./mvnw test
```

Frontend (Vitest + Testing Library):

```bash
cd frontend
npm test
```

## Problemas comunes

### `The JAVA_HOME environment variable is not defined correctly` o `release version 21 not supported`

Falta el export de `JAVA_HOME` en esa terminal (openjdk@21 es keg-only):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

### Puerto 5432 ocupado

Hay otro Postgres corriendo (otro proyecto o instalación local):

```bash
lsof -i :5432            # ver quién lo usa
brew services stop postgresql@16   # si es el de Homebrew
docker ps                # si es otro contenedor: docker stop <nombre>
```

### Puerto 8080 ocupado

```bash
lsof -i :8080
kill <PID>
```

### El backend no conecta con la base de datos

- ¿Está Docker Desktop arrancado? (`docker info`)
- ¿Está el contenedor sano? (`docker compose ps`)
- Logs de Postgres: `docker compose logs postgres`

### Los tests de backend fallan con errores de Testcontainers

Testcontainers necesita Docker corriendo. Arranca Docker Desktop y reintenta.

### Resetear la base de datos de desarrollo

```bash
docker compose down -v   # borra también el volumen
docker compose up -d
```
