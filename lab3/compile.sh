#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
source "$DIR/env.sh"

echo "[INFO] Using Java: $(which java || true)"
java -version 2>&1 | head -n 2
echo "[INFO] Using Javac: $(which javac || true)"
javac -version

mkdir -p "$DIR/out"
echo "[INFO] Compiling sources..."
javac --release 25 -d "$DIR/out" \
    "$DIR/src/BoundedBuffer.java" \
    "$DIR/src/BoundedBufferLab.java" \
    "$DIR/src/BoundedBufferTest.java" \
    "$DIR/src/PerformanceExperiment.java"

echo "[SUCCESS] Kompilacja zakonczona sukcesem do katalogu out/."
