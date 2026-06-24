#!/usr/bin/env bash
#
# Genera (o regenera) los accesos directos del Escritorio con icono para Senda:
#   • "Senda — Iniciar.app"  → ejecuta start.command (BBDD + API + front + navegador)
#   • "Senda — Detener.app"  → ejecuta stop.command  (apaga todo, incluida la BBDD)
#
# Solo usa herramientas integradas de macOS (qlmanage, sips, iconutil, osacompile,
# codesign). Doble-clic o `./scripts/make-desktop-launchers.command`.

set -euo pipefail

# Raíz del repo = carpeta padre de este script (resolviendo symlinks).
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"; SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
SCRIPT_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
SENDA="$(cd "$SCRIPT_DIR/.." && pwd)"
DESK="$HOME/Desktop"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cd "$WORK"

# Iconos de marca: verde (Iniciar) con la "S" de Senda, rojo (Detener) con el cuadro de stop.
cat > start.svg <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024">
<rect width="1024" height="1024" rx="220" fill="#059669"/>
<text x="512" y="710" font-family="Helvetica Neue, Helvetica, Arial" font-size="640" font-weight="bold" fill="#ffffff" text-anchor="middle">S</text>
</svg>
SVG
cat > stop.svg <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024">
<rect width="1024" height="1024" rx="220" fill="#dc2626"/>
<rect x="312" y="312" width="400" height="400" rx="64" fill="#ffffff"/>
</svg>
SVG

make_icns() {
  local name="$1"
  qlmanage -t -s 1024 -o . "$name.svg" >/dev/null 2>&1
  local base="$name.svg.png" set="$name.iconset"
  mkdir -p "$set"
  sips -z 16 16   "$base" --out "$set/icon_16x16.png"      >/dev/null
  sips -z 32 32   "$base" --out "$set/icon_16x16@2x.png"   >/dev/null
  sips -z 32 32   "$base" --out "$set/icon_32x32.png"      >/dev/null
  sips -z 64 64   "$base" --out "$set/icon_32x32@2x.png"   >/dev/null
  sips -z 128 128 "$base" --out "$set/icon_128x128.png"    >/dev/null
  sips -z 256 256 "$base" --out "$set/icon_128x128@2x.png" >/dev/null
  sips -z 256 256 "$base" --out "$set/icon_256x256.png"    >/dev/null
  sips -z 512 512 "$base" --out "$set/icon_256x256@2x.png" >/dev/null
  sips -z 512 512 "$base" --out "$set/icon_512x512.png"    >/dev/null
  cp "$base"      "$set/icon_512x512@2x.png"
  iconutil -c icns "$set" -o "$name.icns"
}

build_app() {
  local label="$1" target="$2" icns="$3"
  local app="$DESK/$label.app"
  rm -rf "$app"
  printf 'on run\n  tell application "Terminal"\n    activate\n    do script "%s"\n  end tell\nend run\n' "'$target'" > app.applescript
  osacompile -o "$app" app.applescript
  cp "$icns" "$app/Contents/Resources/applet.icns"
  codesign --force -s - "$app" >/dev/null 2>&1 || true   # re-firma ad-hoc tras cambiar el icono
  touch "$app"
}

make_icns start
make_icns stop

# Elimina posibles accesos antiguos (symlinks .command) y crea las apps con icono.
rm -f "$DESK/Senda — Iniciar.command" "$DESK/Senda — Detener.command"
build_app "Senda — Iniciar" "$SENDA/start.command" "$WORK/start.icns"
build_app "Senda — Detener" "$SENDA/stop.command"  "$WORK/stop.icns"

killall Finder >/dev/null 2>&1 || true   # refresca el icono en el Escritorio

echo "✓ Accesos creados en el Escritorio: «Senda — Iniciar» y «Senda — Detener»."
sleep 1
