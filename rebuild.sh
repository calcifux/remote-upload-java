#!/usr/bin/env bash
#
# rebuild.sh - borra todo, limpia todo y corre todo de nuevo, desde cero.
# Vuelca TODO (consola + maven completo, con stack traces) a rebuild.log en la raiz.
#
# Por defecto corre 'install' = MISMO lifecycle que el CI (tests + jacoco report +
# jacoco-check), asi lo que pasa local pasa en GitHub Actions y no te sorprende.
# Para iterar rapido sin el gate de cobertura, usa -t (solo 'test').
#
# Uso:
#   ./rebuild.sh                    # clean install de TODOS los modulos (= CI)
#   ./rebuild.sh -t                 # rapido: solo 'test' (sin verify/jacoco-check)
#   ./rebuild.sh remote-upload-s3   # solo ese modulo y sus deps (-pl X -am)
#   ./rebuild.sh -t remote-upload-s3
#
# Que hace:
#   1. Borra los artefactos cacheados del groupId en ~/.m2 (incluye el "not found" cacheado).
#   2. mvn -U clean <goal> SIN -q: el log queda completo (tests fallidos + stack traces).
#      El reactor completo construye core PRIMERO, asi que no hay "core not found".
#   El log (rebuild.log) esta en .gitignore (*.log) -> nunca se commitea.
#
set -euo pipefail

# --- raiz del repo = carpeta de este script (corre desde donde sea) ---
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

M2_REPO="${HOME}/.m2/repository/com/github/calcifux"
LOG="${ROOT}/rebuild.log"

# --- flags / args ---
GOAL="install"
MODULE=""
for arg in "$@"; do
  case "$arg" in
    -t|--test)    GOAL="test" ;;
    -i|--install) GOAL="install" ;;   # default; alias por costumbre
    -h|--help)    sed -n '3,20p' "$0"; exit 0 ;;
    -*)           echo "Flag no reconocido: $arg (usa -h)" >&2; exit 2 ;;
    *)            MODULE="$arg" ;;
  esac
done

# --- cabecera: TRUNCA el log (tee sin -a) y deja contexto util para diagnosticar ---
{
  echo "=== rebuild.sh  $(date '+%Y-%m-%d %H:%M:%S') ==="
  echo "goal=${GOAL}  module=${MODULE:-<todos>}"
  echo ""
  echo "==> JVM en uso:"
  java -version
  echo ""
  echo "==> 1/2  Borrando artefactos cacheados: ${M2_REPO}/remote-upload-*"
} 2>&1 | tee "$LOG"

rm -rf "${M2_REPO}"/remote-upload-* 2>/dev/null || true

echo "==> 2/2  Build desde cero (clean + ${GOAL}, -U refresh, --fail-at-end corre TODO)" 2>&1 | tee -a "$LOG"

# --- maven: --fail-at-end = no se detiene en el primer modulo rojo; corre todos los
#     que pueda y junta TODAS las fallas al final (menos ida y vuelta para diagnosticar).
#     Capturamos SU exit code (no el de tee) via PIPESTATUS. ---
set +e
if [[ -n "$MODULE" ]]; then
  mvn -U --fail-at-end clean "$GOAL" -pl "$MODULE" -am 2>&1 | tee -a "$LOG"
else
  mvn -U --fail-at-end clean "$GOAL" 2>&1 | tee -a "$LOG"
fi
STATUS=${PIPESTATUS[0]}
set -e

# --- resumen final (tambien al log) ---
{
  echo ""
  if [[ "$STATUS" -eq 0 ]]; then
    echo "OK - build verde."
  else
    echo "FALLO - build rojo (exit ${STATUS}). Revisa el log."
  fi
  echo "Log completo: ${LOG}"
} 2>&1 | tee -a "$LOG"

exit "$STATUS"
