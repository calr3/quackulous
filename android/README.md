# Quackulous for Android

An Android app around the Quackle engine, set up for Lexulous: an 8-tile rack, 40 points for playing 7 tiles and 50
for all 8, and the Lexulous board and tiles. You enter a position and it ranks your moves, either instantly by score
plus leave value, or by simulating the game a couple of turns ahead.

No dictionary ships with the app. On first launch it asks for a word list (a text file, one word per line) and
builds its dictionary from that on the phone. Lists that put definitions after the word work too, since only the
first word on each line is used. Official lists Quackle knows, such as Collins Scrabble Words 2024, are recognised
and named.

## Building

Needs a JDK (17 or later), the Android SDK with platform 35, NDK 27.3.13750724 and CMake 3.31.6. Point
`local.properties` at the SDK (`sdk.dir=/path/to/sdk`), or set `ANDROID_HOME`, then:

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # onto a connected phone with USB debugging on
```

The engine is compiled for `arm64-v8a` (phones) and `x86_64` (emulators). The C++ comes from `../engineapi` (a
Qt-free layer over libquackle) and the rest of the repository; data files come from `../data` at build time.

## How it fits together

- `app/src/main/cpp/quackulous_jni.cpp`: the JNI bridge over `engineapi/engine.h`. Results cross as JSON.
- `engine/QuackleEngine.kt`: the Kotlin API. Quackle keeps its state in one global, so every call runs on a single
  engine thread.
- `data/AssetInstaller.kt`: unpacks the engine's data (alphabet, strategy files) to app storage on first launch and
  after updates. The dictionary built from the user's word list lives beside it in `lexica/user.*`.
- `ui/`: Jetpack Compose screens. `AppViewModel` holds the board, rack and results, and saves the board and rack.

Leave values: if trained Lexulous values are installed at `../data/strategy/lexulous_csw24/superleaves` (see
`../test/leavetraining`), the app uses them. Otherwise it falls back to Quackle's Collins Scrabble values.

## Tests

```sh
./host-test.sh
```

This builds the native library for the desktop and runs the JVM unit tests. They cover the board logic, and they
drive the real engine through the same JNI bridge and Kotlin wrapper the phone uses: building a dictionary,
analysing, simulating, and rejecting impossible positions. `../engineapi/test/run_tests.py` checks that dictionaries
built this way are byte-identical to Quackle's own.

Nothing here runs the app on a device or emulator. The interface has only been checked by compiling it.
