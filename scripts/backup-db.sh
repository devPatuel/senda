#!/usr/bin/env bash
#
# Backup del volumen de Postgres de Senda (producción).
# Vuelca la base 'senda' del contenedor de compose a un .sql.gz fechado.
#
# Uso:  ./scripts/backup-db.sh [directorio_destino]
#       (por defecto: ./backups)
#
# Requiere que la pila de producción esté levantada:
#   docker compose -f docker-compose.prod.yml ps

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$REPO_DIR/docker-compose.prod.yml"
DEST_DIR="${1:-$REPO_DIR/backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$DEST_DIR/senda-$STAMP.sql.gz"

mkdir -p "$DEST_DIR"

echo "Volcando la base 'senda' a $OUT ..."
docker compose -f "$COMPOSE_FILE" exec -T postgres \
  pg_dump -U senda -d senda | gzip > "$OUT"

echo "Backup completado: $OUT ($(du -h "$OUT" | cut -f1))"
