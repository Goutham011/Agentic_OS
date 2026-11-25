plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("kotlinx-serialization")
    id("com.google.protobuf") version "0.9.4"
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
        
        // Hilt
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packagingOptions {
        resources {
            excludes += listOf("META-INF/*.kotlin_module")
        }
    }

    // Version Catalog
    val versions by extra(mapOf(
        "kotlin" to "1.9.22",
        "core_ktx" to "1.13.1",
        "lifecycle" to "2.7.0",
        "room" to "2.6.1",
        "hilt" to "2.48.1",
        "workmanager" to "2.9.0",
        "retrofit" to "2.11.0",
        "okhttp" to "4.12.0",
        "jsoup" to "1.17.2",
        "mediapipe_llm" to "0.10.13",
        "mediapipe_vad" to "0.10.13",
        "mlkit_text" to "17.0.0",
        "compose_bom" to "2024.05.00",
        "material3" to "1.2.1",
        "activity_compose" to "1.9.0",
        "porcupine" to "3.0.1",
        "coil" to "2.6.0",
        "google_cloud_speech" to "2.30.0",
        "grpc_okhttp" to "1.62.1",
        "protobuf_gradle" to "0.9.4"
    ))

    val libs by extra(mapOf(
        "core_ktx" to "androidx.core:core-ktx:\${versions.core_ktx}",
        "lifecycle_runtime_ktx" to "androidx.lifecycle:lifecycle-runtime-ktx:\${versions.lifecycle}",
        "lifecycle_viewmodel_compose" to "androidx.lifecycle:lifecycle-viewmodel-compose:\${versions.lifecycle}",
        "room_runtime" to "androidx.room:room-runtime:\${versions.room}",
        "room_ktx" to "androidx.room:room-ktx:\${versions.room}",
        "room_compiler" to "androidx.room:room-compiler:\${versions.room}",
        "hilt_android" to "com.google.dagger:hilt-android:\${versions.hilt}",
        "hilt_compiler" to "com.google.dagger:hilt-compiler:\${versions.hilt}",
        "workmanager" to "androidx.work:work-runtime-ktx:\${versions.workmanager}",
        "retrofit" to "com.squareup.retrofit2:retrofit:\${versions.retrofit}",
        "retrofit_converter_json" to "com.squareup.retrofit2:converter-kotlinx-serialization:\${versions.retrofit}",
        "retrofit_converter_scalars" to "com.squareup.retrofit2:converter-scalars:\${versions.retrofit}",
        "okhttp" to "com.squareup.okhttp3:okhttp:\${versions.okhttp}",
        "okhttp_logging" to "com.squareup.okhttp3:logging-interceptor:\${versions.okhttp}",
        "jsoup" to "org.jsoup:jsoup:\${versions.jsoup}",
        "mediapipe_llm" to "com.google.mediapipe:tasks-genai:\${versions.mediapipe_llm}",
        "mediapipe_vad" to "com.google.mediapipe:tasks-audio:\${versions.mediapipe_vad}",
        "mlkit_text" to "com.google.mlkit:text-recognition:\${versions.mlkit_text}",
        "compose_bom" to "androidx.compose:compose-bom:\${versions.compose_bom}",
        "material3" to "androidx.compose.material3:material3:\${versions.material3}",
        "activity_compose" to "androidx.activity:activity-compose:\${versions.activity_compose}",
        "porcupine" to "ai.picovoice:porcupine-android:\${versions.porcupine}",
        "coil" to "io.coil-kt:coil-compose:\${versions.coil}",
        "google_cloud_speech" to "com.google.cloud:google-cloud-speech:\${versions.google_cloud_speech}",
        "grpc_okhttp" to "io.grpc:grpc-okhttp:\${versions.grpc_okhttp}",
        "protobuf_gradle" to "com.google.protobuf:protobuf-gradle-plugin:\${versions.protobuf_gradle}"
    ))

    dependencies {
        val libs by extra
        
        // Core
        implementation(libs.core_ktx.get())
        implementation(libs.lifecycle_runtime_ktx.get())
        implementation(libs.lifecycle_viewmodel_compose.get())
        
        // Room
        implementation(libs.room_runtime.get())
        implementation(libs.room_ktx.get())
        ksp(libs.room_compiler.get())
        
        // Hilt
        implementation(libs.hilt_android.get())
        ksp(libs.hilt_compiler.get())
        
        // WorkManager
        implementation(libs.workmanager.get())
        
        // Networking
        implementation(libs.retrofit.get())
        implementation(libs.retrofit_converter_json.get())
        implementation(libs.retrofit_converter_scalars.get())
        implementation(libs.okhttp.get())
        implementation(libs.okhttp_logging.get())
        implementation(libs.jsoup.get())
        
        // MediaPipe LLM
        implementation(libs.mediapipe_llm.get())
        
        // MediaPipe Audio (VAD)
        implementation(libs.mediapipe_vad.get())
        
        // ML Kit Text Recognition
        implementation(libs.mlkit_text.get())
        
        // Compose
        implementation(platform(libs.compose_bom.get()))
        implementation(libs.material3.get())
        implementation(libs.activity_compose.get())
        
        // Porcupine (Wake Word)
        implementation(libs.porcupine.get())
        
        // Google Cloud Speech-to-Text
        implementation(libs.google_cloud_speech.get())
        implementation(libs.grpc_okhttp.get())
        
        // Coil (Image loading)
        implementation(libs.coil.get())
        
        // Serialization
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        
        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
        
        // Testing
        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.1.5")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
        androidTestImplementation("com.google.dagger:hilt-android-testing:\${versions.hilt}")
        kspAndroidTest("com.google.dagger:hilt-compiler:\${versions.hilt}")
    }
}

// Protobuf configuration for Google Cloud Speech
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.12"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.1"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.62.1"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}