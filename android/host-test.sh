#!/bin/sh
# Runs the app's JVM unit tests, including NativeEngineTest against a desktop build of the native library.
# Needs CMake, a C++ compiler and a JDK; ANDROID_HOME or local.properties must point at an Android SDK.
set -e
cd "$(dirname "$0")"
build="${HOST_BUILD_DIR:-build/host-jni}"
cmake -S app/src/main/cpp -B "$build" -DCMAKE_BUILD_TYPE=Release
cmake --build "$build" --target quackulous
./gradlew testDebugUnitTest -PhostLibDir="$(cd "$build" && pwd)" "$@"
