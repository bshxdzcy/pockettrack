#!/bin/bash
set -euo pipefail
ADB="${ADB:-adb}"
args=()
if [ -n "${ANDROID_SERIAL:-}" ]; then args=(-s "$ANDROID_SERIAL"); fi
"$ADB" "${args[@]}" shell am force-stop dev.pockettrack
"$ADB" "${args[@]}" reverse --remove tcp:49671
printf 'Phone control stopped. No system settings were changed.\n'
