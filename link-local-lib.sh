#!/usr/bin/env sh
# ============================================================
#  link-local-lib.sh
#  Enlaza node_modules/enrollment-library -> dist de la lib local.
#  Cross-platform: Mac/Linux (ln -s) y Windows Git Bash (mklink /J via cmd).
#  Uso:  npm run link:local-lib   (o:  sh scripts/link-local-lib.sh)
# ============================================================
set -eu

# Carpeta del script y raiz del API (un nivel arriba de /scripts)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
API_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Dist de la lib (repo hermano). Ajusta si el folder interno cambia.
LIB_DIST_REL="$API_DIR/../ib-mx-enrollment-lib/dist/libs/ib-mx-enrollment-lib"

# Resolver a ruta absoluta y validar que exista
if ! LIB_DIST="$(cd "$LIB_DIST_REL" 2>/dev/null && pwd)"; then
  echo "[ERROR] No existe el dist de la lib:"
  echo "        $LIB_DIST_REL"
  echo "        Corre  npm run build:lib  en la lib primero."
  exit 1
fi

LINK="$API_DIR/node_modules/enrollment-library"

case "$(uname -s)" in
  Darwin | Linux)
    # macOS / Linux: symlink nativo
    rm -rf "$LINK"
    ln -s "$LIB_DIST" "$LINK"
    ;;
  MINGW* | MSYS* | CYGWIN*)
    # Windows Git Bash: junction via cmd, sin que Git Bash mangle las rutas
    LINK_WIN="$(cygpath -w "$LINK")"
    LIB_DIST_WIN="$(cygpath -w "$LIB_DIST")"
    # OJO: usar rmdir (no rm -rf) para borrar un junction sin recursar al target
    MSYS_NO_PATHCONV=1 cmd //c "if exist \"$LINK_WIN\" rmdir /s /q \"$LINK_WIN\""
    MSYS_NO_PATHCONV=1 cmd //c "mklink /J \"$LINK_WIN\" \"$LIB_DIST_WIN\""
    ;;
  *)
    echo "[ERROR] SO no soportado: $(uname -s)"
    exit 1
    ;;
esac

echo ""
echo "[OK] enrollment-library -> $LIB_DIST"
