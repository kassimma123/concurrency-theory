#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
source "$DIR/env.sh"

mkdir -p "$DIR/out"
echo "[INFO] Kompilacja BoundedBufferLab.java (JDK 25)..."
javac --release 25 -d "$DIR/out" "$DIR/src/BoundedBufferLab.java"
echo "[SUCCESS] Skompilowano do katalogu out/."
