#!/usr/bin/env bash
# Usage: ./prepare.sh /path/to/EmOS-with-Emulator3.html
# Splits the 278 MB page into 90 MB chunks in app/src/main/assets (each stays under GitHub's 100 MB limit).
set -e
[ -f "$1" ] || { echo "usage: $0 path/to/EmOS-with-Emulator3.html"; exit 1; }
mkdir -p app/src/main/assets
rm -f app/src/main/assets/emos_*.bin
split -b 90m -d -a 3 --additional-suffix=.bin "$1" app/src/main/assets/emos_
ls -lh app/src/main/assets
