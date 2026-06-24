#!/usr/bin/env bash
#
# Senda — apagado total de un clic.
# Detiene la API (8080), el frontend (5173) y la base de datos (Docker).
# Los datos de Postgres se conservan en el volumen `senda_pgdata`.
# Doble-clic desde Finder o `./stop.command` desde la terminal.

set -uo pipefail

# Raíz del repo, resolviendo symlinks (para funcionar como alias del Escritorio).
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
cd "$(cd -P "$(dirname "$SOURCE")" && pwd)"

API_PORT=8080
WEB_PORT=5173

if [ -t 1 ]; then
  BOLD=$'\033[1m'; GREEN=$'\033[32m'; BLUE=$'\033[34m'; RESET=$'\033[0m'
else
  BOLD=''; GREEN=''; BLUE=''; RESET=''
fi
say() { printf '%s\n' "${BOLD}${BLUE}▶ ${1}${RESET}"; }
ok()  { printf '%s\n' "${GREEN}✓ ${1}${RESET}"; }

say "Deteniendo Senda…"

# Para cualquier lanzador activo y los servidores por puerto (educado → a la fuerza).
pkill -f 'start.command' 2>/dev/null || true
for sig in TERM KILL; do
  for port in "$WEB_PORT" "$API_PORT"; do
    pids=$(lsof -ti "tcp:$port" 2>/dev/null) || true
    [ -n "$pids" ] && kill -"$sig" $pids 2>/dev/null || true
  done
  [ "$sig" = TERM ] && sleep 2
done
ok "API y frontend detenidos."

# Para la base de datos (conserva el volumen de datos).
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  docker compose down >/dev/null 2>&1 && ok "Base de datos detenida (datos conservados en el volumen)."
else
  printf '%s\n' "  (Docker no está activo; la base de datos ya estaba parada.)"
fi

ok "Senda detenido por completo."
# Pausa breve para que la ventana muestre el resultado al hacer doble-clic.
sleep 1
