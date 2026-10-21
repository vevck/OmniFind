#!/bin/bash
# TC-10: checks a release APK before it goes to testers. Usage: tools/release/verify_apk.sh [dist/OmniFind.apk]
# Signature (v2+), Android 9 (API 28) to the target API, arm64 only, 16 KB page alignment of the APK and of every
# native library (Android 15+ devices can run with 16 KB pages), and the launcher icon in every density bucket.
set -euo pipefail
cd "$(dirname "$0")/../.."
APK="${1:-dist/OmniFind.apk}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
NDK=$(ls -d "$SDK"/ndk/* | sort -V | tail -1)
READELF=$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/llvm-readelf | head -1)
fail() { echo "FAIL: $*"; exit 1; }

"$BT/apksigner" verify "$APK" >/dev/null || fail "signature"
echo "ok   signature verifies"
BADGING=$("$BT/aapt2" dump badging "$APK")
MIN=$(echo "$BADGING" | sed -n "s/^minSdkVersion:'\([0-9]*\)'.*/\1/p")
TARGET=$(echo "$BADGING" | sed -n "s/^targetSdkVersion:'\([0-9]*\)'.*/\1/p")
[ "$MIN" = "28" ] || fail "minSdk is $MIN, not 28 (Android 9)"
echo "ok   Android 9 (API $MIN) to API $TARGET"
echo "$BADGING" | grep -q "native-code: 'arm64-v8a'" || fail "not arm64-only"
echo "ok   arm64-v8a"
"$BT/zipalign" -c -P 16 4 "$APK" || fail "APK not 16 KB aligned"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
unzip -q "$APK" 'lib/*' -d "$TMP"
for so in "$TMP"/lib/*/*.so; do
  ALIGN=$("$READELF" -lW "$so" | awk '/LOAD/ {print $NF}' | sort -u)
  for a in $ALIGN; do [ $((a)) -ge 16384 ] || fail "$(basename "$so") LOAD alignment $a < 16 KB"; done
done
echo "ok   16 KB pages: APK and $(ls "$TMP"/lib/*/*.so | wc -l | tr -d ' ') native libraries"
for d in 120 160 240 320 480 640; do echo "$BADGING" | grep -q "application-icon-$d:" || fail "no icon at $d dpi"; done
echo "ok   launcher icon at ldpi, mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi"
echo "$BADGING" | sed -n "s/^package: name='\([^']*\)' versionCode='\([0-9]*\)' versionName='\([^']*\)'.*/ok   \1 \3 (build \2)/p"
