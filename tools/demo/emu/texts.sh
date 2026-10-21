#!/bin/bash
# texts.sh <serial> <regex>: the visible texts matching a pattern, from a fresh dump.
E=$1
adb -s $E shell rm -f /sdcard/ui.xml; adb -s $E shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb -s $E exec-out cat /sdcard/ui.xml | grep -oE "text=\"[^\"]*\"" | sed 's/^text="//; s/"$//' | grep -E "$2"
