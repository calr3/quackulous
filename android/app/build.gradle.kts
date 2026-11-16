plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val quackleData = rootProject.projectDir.resolve("../data")
val generatedAssets = layout.buildDirectory.dir("generated/quackleAssets")

// The engine's data files, from the repository's data directory. No word list ships with the app: the user loads one.
val prepareQuackleAssets by tasks.registering(Sync::class) {
    into(generatedAssets.map { it.dir("quackle") })
    from(quackleData.resolve("alphabets/lexulous.quackle_alphabet")) { into("alphabets") }
    from(quackleData.resolve("strategy/default")) { into("strategy/default") }
    from(quackleData.resolve("strategy/default_english")) {
        into("strategy/default_english")
        exclude("superleaves")
    }
    // Lexulous is played with Collins, so Quackle's Collins leave values are the better fallback.
    from(quackleData.resolve("strategy/csw/superleaves")) { into("strategy/default_english") }
    // Trained Lexulous leave values, once they're installed (see test/leavetraining).
    from(quackleData.resolve("strategy/lexulous_csw24")) { into("strategy/lexulous_user") }
    from(quackleData.resolve("lexica/copyrights.txt")) { into("lexica") }
}

android {
    namespace = "org.quackulous"
    compileSdk = 35
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "org.quackulous"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    sourceSets["main"].assets.srcDir(generatedAssets)

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.named("preBuild") { dependsOn(prepareQuackleAssets) }

// NativeEngineTest loads a desktop build of the native library; host-test.sh builds it and passes its directory.
tasks.withType<Test>().configureEach {
    // A JVM can load a native library into one classloader only, and Robolectric tests use their own.
    forkEvery = 1
    providers.gradleProperty("hostLibDir").orNull?.let {
        systemProperty("java.library.path", it)
        systemProperty("quackulous.hostLibDir", it)
        inputs.file("$it/libquackulous.so").withPropertyName("hostLibrary")
    }
    systemProperty("quackulous.dataDir", quackleData.absolutePath)
    // Robolectric downloads its Android runtime itself; use Maven Central rather than its default mirror.
    systemProperty("robolectric.dependency.repo.url", "https://repo1.maven.org/maven2")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
