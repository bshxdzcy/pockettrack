#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK directory}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or newer}"
BT="$ANDROID_HOME/build-tools/36.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p build/android/classes build/android/dex
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR" -d build/android/classes android/src/dev/pockettrack/*.java
"$JAVA_HOME/bin/jar" cf build/android/classes.jar -C build/android/classes .
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --output build/android/dex build/android/classes.jar
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest android/AndroidManifest.xml -o build/android/unsigned.apk
(cd build/android/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/android/unsigned.apk build/android/aligned.apk
if [ ! -f build/debug.keystore ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore build/debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=PocketTrack Development'
fi
"$BT/apksigner" sign --ks build/debug.keystore --ks-pass pass:android --key-pass pass:android --out build/PocketTrack.apk build/android/aligned.apk
"$BT/apksigner" verify build/PocketTrack.apk
printf 'Built %s/build/PocketTrack.apk\n' "$PWD"
