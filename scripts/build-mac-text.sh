#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/PocketTrackText.app/Contents/MacOS
xcrun swiftc -swift-version 5 -O mac-text/TextCrypto.swift mac-text/main.swift -o build/PocketTrackText.app/Contents/MacOS/PocketTrackText -framework AppKit -framework CoreBluetooth -framework CryptoKit
cp mac-text/Info.plist build/PocketTrackText.app/Contents/Info.plist
codesign --force --sign - --identifier dev.pockettrack.text build/PocketTrackText.app
printf 'Built %s/build/PocketTrackText.app\n' "$PWD"
