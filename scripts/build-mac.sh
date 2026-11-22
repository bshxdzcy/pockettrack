#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/PocketTrack.app/Contents/MacOS
xcrun swiftc -swift-version 5 -O mac/main.swift -o build/PocketTrack.app/Contents/MacOS/PocketTrack -framework AppKit -framework Network -framework ApplicationServices -framework Security
cp mac/Info.plist build/PocketTrack.app/Contents/Info.plist
codesign --force --sign - --identifier dev.pockettrack.mac build/PocketTrack.app
printf 'Built %s/build/PocketTrack.app\n' "$PWD"
