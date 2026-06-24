#!/usr/bin/env bash
#
# Senda — lanzador de desarrollo de un clic.
# Levanta la base de datos (Docker), la API (Spring Boot) y el frontend (Vite),
# espera a que estén listos y abre el navegador. Cierra todo con Ctrl+C o al
# cerrar la ventana. Doble-clic desde Finder o `./start.command` desde la terminal.

set -uo pipefail

# Trabaja siempre desde la raíz del repo (donde vive este script), aunque se
# lance desde Finder (que arranca en $HOME).
cd "$(dirname "$0")"
ROOT="$(pwd)"

API_PORT=8080
WEB_PORT=5173
LOG_DIR="${TMPDIR:-/tmp}/senda-dev"
mkdir -p "$LOG_DIR"
API_LOG="$LOG_DIR/api.log"
WEB_LOG="$LOG_DIR/web.log"

# Colores (solo si la salida es un terminal)
if [ -t 1 ]; then
  BOLD=$'\033[1m'; GREEN=$'\033[32m'; BLUE=$'\033[34m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'
else
  BOLD=''; GREEN=''; BLUE=''; YELLOW=''; RED=''; RESET=''
fi
say()  { printf '%s\n' "${BOLD}${BLUE}▶ ${1}${RESET}"; }
ok()   { printf '%s\n' "${GREEN}✓ ${1}${RESET}"; }
warn() { printf '%s\n' "${YELLOW}! ${1}${RESET}"; }
die()  { printf '%s\n' "${RED}✗ ${1}${RESET}"; exit 1; }

API_PID=""
WEB_PID=""
TAIL_PIDS=()

cleanup() {
  trap '' INT TERM HUP EXIT   # evita re-entradas mientras limpiamos
  echo
  say "Deteniendo Senda…"
  for pid in "${TAIL_PIDS[@]:-}"; do [ -n "$pid" ] && kill "$pid" 2>/dev/null; done
  [ -n "$WEB_PID" ] && kill "$WEB_PID" 2>/dev/null
  [ -n "$API_PID" ] && kill "$API_PID" 2>/dev/null
  # Apagado por puerto (cubre hijos de Maven/Vite): primero educado, luego a la fuerza.
  for sig in TERM KILL; do
    for port in "$WEB_PORT" "$API_PORT"; do
      local pids; pids=$(lsof -ti "tcp:$port" 2>/dev/null) || true
      [ -n "$pids" ] && kill -"$sig" $pids 2>/dev/null || true
    done
    [ "$sig" = TERM ] && sleep 2
  done
  ok "Procesos detenidos. La base de datos sigue corriendo (docker compose down para pararla)."
}
trap cleanup INT TERM HUP EXIT

# Espera hasta que un comando tenga éxito, con timeout en segundos.
wait_for() {
  local desc="$1" timeout="$2"; shift 2
  local waited=0
  until "$@" >/dev/null 2>&1; do
    sleep 1
    waited=$((waited + 1))
    if [ "$waited" -ge "$timeout" ]; then
      return 1
    fi
  done
  return 0
}

# ----------------------------------------------------------------------------
# 0) Requisitos: Java 21 y Docker en marcha
# ----------------------------------------------------------------------------
say "Comprobando requisitos…"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  if [ -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  elif /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  else
    die "No encuentro Java 21. Instálalo con: brew install openjdk@21"
  fi
fi
ok "Java 21 en $JAVA_HOME"

if ! command -v docker >/dev/null 2>&1; then
  die "Docker no está instalado."
fi
if ! docker info >/dev/null 2>&1; then
  warn "Docker no está arrancado. Abriendo Docker Desktop…"
  open -a Docker 2>/dev/null || true
  if ! wait_for "Docker" 90 docker info; then
    die "Docker no se ha iniciado a tiempo. Ábrelo manualmente y reintenta."
  fi
fi
ok "Docker en marcha"

# ----------------------------------------------------------------------------
# 1) Base de datos (Postgres)
# ----------------------------------------------------------------------------
say "Levantando la base de datos…"
docker compose up -d >/dev/null 2>&1 || die "No se pudo levantar Postgres (docker compose up)."
if ! wait_for "Postgres" 60 docker exec senda-postgres pg_isready -U senda -d senda; then
  die "Postgres no respondió a tiempo."
fi
ok "Postgres listo en :5432"

# ----------------------------------------------------------------------------
# 2) API (Spring Boot) — salta si ya está corriendo
# ----------------------------------------------------------------------------
if curl -sf "http://localhost:$API_PORT/actuator/health" 2>/dev/null | grep -q UP; then
  ok "La API ya estaba corriendo en :$API_PORT"
else
  say "Arrancando la API (compila la primera vez, puede tardar)…"
  : > "$API_LOG"
  ( cd "$ROOT/backend" && exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=local ) >"$API_LOG" 2>&1 &
  API_PID=$!
  if ! wait_for "API" 180 bash -c "curl -sf http://localhost:$API_PORT/actuator/health | grep -q UP"; then
    die "La API no se puso lista. Revisa el log: $API_LOG"
  fi
  ok "API lista en http://localhost:$API_PORT"
fi

# ----------------------------------------------------------------------------
# 3) Frontend (Vite) — salta si ya está corriendo
# ----------------------------------------------------------------------------
if lsof -ti "tcp:$WEB_PORT" >/dev/null 2>&1; then
  ok "El frontend ya estaba corriendo en :$WEB_PORT"
else
  if [ ! -d "$ROOT/frontend/node_modules" ]; then
    say "Instalando dependencias del frontend (solo la primera vez)…"
    ( cd "$ROOT/frontend" && npm install ) || die "Falló npm install."
  fi
  say "Arrancando el frontend…"
  : > "$WEB_LOG"
  ( cd "$ROOT/frontend" && exec npm run dev ) >"$WEB_LOG" 2>&1 &
  WEB_PID=$!
  if ! wait_for "Frontend" 60 bash -c "lsof -ti tcp:$WEB_PORT"; then
    die "El frontend no arrancó. Revisa el log: $WEB_LOG"
  fi
  ok "Frontend listo en http://localhost:$WEB_PORT"
fi

# ----------------------------------------------------------------------------
# 4) Abre el navegador y transmite los logs
# ----------------------------------------------------------------------------
URL="http://localhost:$WEB_PORT"
say "Abriendo $URL"
open "$URL" 2>/dev/null || true

echo
ok "Senda en marcha. Pulsa ${BOLD}Ctrl+C${RESET}${GREEN} para detener todo.${RESET}"
echo "  ${BOLD}Logs:${RESET} API → $API_LOG   ·   Web → $WEB_LOG"
echo

# Transmite ambos logs con prefijo. Si la API/Front ya estaban corriendo (sin
# PID nuestro) igualmente seguimos los ficheros existentes.
( tail -n 5 -f "$API_LOG" 2>/dev/null | sed "s/^/${BLUE}[api]${RESET} /" ) &
TAIL_PIDS+=("$!")
( tail -n 5 -f "$WEB_LOG" 2>/dev/null | sed "s/^/${GREEN}[web]${RESET} /" ) &
TAIL_PIDS+=("$!")

# Mantén el script vivo hasta Ctrl+C. Si arrancamos algún servidor, espera por él.
if [ -n "$API_PID" ] || [ -n "$WEB_PID" ]; then
  wait $API_PID $WEB_PID 2>/dev/null
else
  # Nada que arrancar (todo ya estaba en marcha): espera indefinidamente.
  while true; do sleep 3600; done
fi
