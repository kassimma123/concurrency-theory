#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
source "$DIR/env.sh"

"$DIR/compile.sh"
echo ""
echo "[INFO] Uruchamianie BoundedBufferLab..."
java -cp "$DIR/out" BoundedBufferLab
