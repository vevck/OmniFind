#!/bin/bash
# Puts llama.cpp at the commit DigUp pins (tag b11461) in app/src/main/cpp/llama.cpp. The build applies
# patches/llama.cpp-no-logits.patch itself (app/src/main/cpp/llama-patch.cmake).
set -euo pipefail
cd "$(dirname "$0")/.."
TAG=b11461
COMMIT=4d756bc72bf00a4aacf410ae15a2d315f3db400d
DIR=app/src/main/cpp/llama.cpp
git submodule update --init "$DIR"
if [[ "$(git -C "$DIR" rev-parse HEAD)" != "$COMMIT" ]]; then
  git -C "$DIR" fetch --quiet --depth 1 origin tag "$TAG"
  git -C "$DIR" -c advice.detachedHead=false checkout --quiet --force "$COMMIT"
fi
echo "llama.cpp at $(git -C "$DIR" rev-parse --short HEAD) ($TAG)"
