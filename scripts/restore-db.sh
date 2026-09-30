#!/usr/bin/env bash
#
# Restore de un backup de Senda sobre la base 'senda'.
# DESTRUCTIVO: sobrescribe los datos actuales. Pide confirmación.
#
# Uso:  ./scripts/restore-db.sh [--local] <fichero.sql.gz>
#
#   sin opciones  pila de producción (docker-compose.prod.yml)
#   --local       pila de desarrollo (docker-compose.yml), la que arranca start.command

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$REPO_DIR/docker-compose.prod.yml"
LOCAL=false
if [[ "${1:-}" == "--local" ]]; then
  COMPOSE_FILE="$REPO_DIR/docker-compose.yml"
  LOCAL=true
  shift
fi

FILE="${1:-}"
if [[ -z "$FILE" || ! -f "$FILE" ]]; then
  echo "Uso: $0 [--local] <fichero.sql.gz>" >&2
  exit 1
fi

echo "ATENCIÓN: esto SOBRESCRIBE la base 'senda' ($COMPOSE_FILE) con $FILE"
read -r -p "Escribe 'restaurar' para continuar: " CONFIRM
[[ "$CONFIRM" == "restaurar" ]] || { echo "Cancelado."; exit 1; }

# backup-db.sh dumps with --clean --if-exists, so the file drops and recreates
# every object. --single-transaction: if anything fails the database is left as
# it was, never half restored.
gunzip -c "$FILE" | docker compose -f "$COMPOSE_FILE" exec -T postgres \
  psql -U senda -d senda -v ON_ERROR_STOP=1 --single-transaction

echo "Restore completado desde $FILE"
if $LOCAL; then
  echo "Reinicia la API (cierra y vuelve a abrir Senda) para que Flyway valide el esquema."
else
  echo "Reinicia el backend para que Flyway valide el esquema:"
  echo "  docker compose -f $COMPOSE_FILE restart backend"
fi
