plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

android {
    namespace = "com.agenticos.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.agenticos.agent"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.keystore")
            storePassword = (findProperty("KEYSTORE_PASSWORD") as? String) ?: System.getenv("KEYSTORE_PASSWORD")
            keyAlias = (findProperty("RELEASE_KEY_ALIAS") as? String) ?: "release"
            keyPassword = (findProperty("KEY_PASSWORD") as? String) ?: System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += listOf("META-INF/*.kotlin_module")
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Room (kapt generates AgenticDatabase_Impl for Kotlin sources)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    // SQLCipher for encrypted database
    implementation("net.zetetic:android-database-sqlcipher:4.5.0")

    // Hilt - moved kapt before implementation
    val hilt_version = "2.48.1"
    kapt("com.google.dagger:hilt-compiler:$hilt_version")
    implementation("com.google.dagger:hilt-android:$hilt_version")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Networking
    val retrofit_version = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofit_version")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:$retrofit_version")
    implementation("com.squareup.retrofit2:converter-scalars:$retrofit_version")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")

    // MediaPipe LLM
    implementation("com.google.mediapipe:tasks-genai:0.10.13")

    // MediaPipe Audio (VAD)
    implementation("com.google.mediapipe:tasks-audio:0.10.13")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Material Components
    implementation("com.google.android.material:material:1.11.0")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended:1.6.7")

    // Compose - Use BOM to manage versions
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Porcupine (Wake Word)
    implementation("ai.picovoice:porcupine-android:3.0.1")

    // Coil (Image loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:$hilt_version")
}

// Hilt Gradle ID for annotation processing
kapt {
    correctErrorTypes = true
}
