#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME}"
mkdir -p build/text-tests
"$JAVA_HOME/bin/javac" -d build/text-tests android/src/dev/pockettrack/TextEnvelope.java android/src/dev/pockettrack/EdgeExit.java android/src/dev/pockettrack/DraftAfterSend.java tests/TextEnvelopeTest.java tests/DraftAfterSendTest.java
"$JAVA_HOME/bin/java" -cp build/text-tests DraftAfterSendTest
"$JAVA_HOME/bin/java" -cp build/text-tests TextEnvelopeTest build/text-tests/packet.txt
cp tests/text_crypto_main.swift build/text-tests/main.swift
xcrun swiftc mac-text/TextCrypto.swift build/text-tests/main.swift -o build/text-tests/verify
build/text-tests/verify build/text-tests/packet.txt
