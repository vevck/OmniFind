#!/bin/bash
# search.sh <serial> <name> <query words…>: types a query in OmniFind's search box, waits for meaning results,
# hides the keyboard, saves a screenshot as q/<name>.png and prints the results' file names.
E=$1; NAME=$2; shift 2; Q="$*"
D=$(dirname "$0")
adb -s $E shell input tap 672 455; sleep 0.5
adb -s $E shell input keyevent KEYCODE_MOVE_END
adb -s $E shell input keyevent $(printf 'KEYCODE_DEL %.0s' $(seq 1 70))
adb -s $E shell input text "$(printf '%s' "$Q" | sed 's/ /%s/g')"
sleep 5
adb -s $E shell input keyevent KEYCODE_BACK; sleep 1.5
adb -s $E exec-out screencap -p > "$D/q/$NAME.png"
echo "\"$Q\" →"; "$D/texts.sh" $E '\.(pdf|docx|md|txt|html|jpg|png)$|^Weaker matches$|Nothing found' | head -6 | sed 's/^/   /'
