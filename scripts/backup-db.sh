#!/usr/bin/env bash
#
# Backup de la base de datos de Senda a un .sql.gz fechado.
#
# Uso:  ./scripts/backup-db.sh [--local] [directorio_destino]
#       (destino por defecto: ~/Documents/Senda/backups)
#
#   sin opciones  pila de producción (docker-compose.prod.yml)
#   --local       pila de desarrollo (docker-compose.yml), la que arranca start.command
#
# The default lives outside the repo on purpose: a dump holds real financial
# data and must never be committed.
#
# Requiere que el Postgres de la pila elegida esté levantado.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$REPO_DIR/docker-compose.prod.yml"
if [[ "${1:-}" == "--local" ]]; then
  COMPOSE_FILE="$REPO_DIR/docker-compose.yml"
  shift
fi
DEST_DIR="${1:-$HOME/Documents/Senda/backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$DEST_DIR/senda-$STAMP.sql.gz"

mkdir -p "$DEST_DIR"

echo "Volcando la base 'senda' a $OUT ..."
# --clean --if-exists: the dump drops each object before recreating it, so it
# restores over a database that already has the schema (restore-db.sh).
# Written to a temp name first: a failed dump must not leave a file that looks
# like a valid backup.
if ! docker compose -f "$COMPOSE_FILE" exec -T postgres \
    pg_dump -U senda -d senda --clean --if-exists | gzip > "$OUT.partial"; then
  rm -f "$OUT.partial"
  echo "El volcado ha fallado: ¿está levantado el Postgres de $COMPOSE_FILE?" >&2
  exit 1
fi
mv "$OUT.partial" "$OUT"

echo "Backup completado: $OUT ($(du -h "$OUT" | cut -f1))"
