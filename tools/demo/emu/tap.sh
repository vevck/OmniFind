#!/bin/bash
# tap.sh <serial> <visible text>: taps the centre of the element with exactly that text, read from a fresh dump.
# Refuses anything mentioning a download, and never acts on an old dump.
E=$1; T=$2; N=${3:-1}   # N: which occurrence, from the top
case "$T" in "Download the model"|*"download"*) echo "refused: '$T'"; exit 2;; esac
for attempt in 1 2 3 4 5; do
  adb -s $E shell rm -f /sdcard/ui.xml
  adb -s $E shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  XML=$(adb -s $E exec-out cat /sdcard/ui.xml 2>/dev/null)
  [ -n "$XML" ] && break
  sleep 1
done
[ -n "$XML" ] || { echo "no fresh dump"; exit 3; }
POS=$(printf '%s' "$XML" | python3 -I -c "
import sys, re
xml = sys.stdin.read(); t = sys.argv[1]; want = int(sys.argv[2]); seen = 0
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    if f'text=\"{t}\"' in n or f'content-desc=\"{t}\"' in n:
        seen += 1
        if seen < want: continue
        x0, y0, x1, y1 = map(int, re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', n).groups())
        print((x0 + x1) // 2, (y0 + y1) // 2); break
" "$T" "$N")
[ -n "$POS" ] || { echo "not on screen: '$T'"; exit 1; }
adb -s $E shell input tap $POS && echo "tapped '$T' at $POS"
