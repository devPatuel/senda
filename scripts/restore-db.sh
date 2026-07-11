#!/usr/bin/env bash
#
# Restore de un backup de Senda sobre la base 'senda' (producción).
# DESTRUCTIVO: sobrescribe los datos actuales. Pide confirmación.
#
# Uso:  ./scripts/restore-db.sh <fichero.sql.gz>

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$REPO_DIR/docker-compose.prod.yml"

FILE="${1:-}"
if [[ -z "$FILE" || ! -f "$FILE" ]]; then
  echo "Uso: $0 <fichero.sql.gz>" >&2
  exit 1
fi

echo "ATENCIÓN: esto SOBRESCRIBE la base 'senda' con $FILE"
read -r -p "Escribe 'restaurar' para continuar: " CONFIRM
[[ "$CONFIRM" == "restaurar" ]] || { echo "Cancelado."; exit 1; }

# Aplica el dump. pg_dump incluye los DROP/CREATE de sus objetos si se generó
# con --clean; en su defecto, restaura sobre las tablas existentes.
gunzip -c "$FILE" | docker compose -f "$COMPOSE_FILE" exec -T postgres \
  psql -U senda -d senda -v ON_ERROR_STOP=1

echo "Restore completado desde $FILE"
echo "Reinicia el backend para que Flyway valide el esquema:"
echo "  docker compose -f $COMPOSE_FILE restart backend"
