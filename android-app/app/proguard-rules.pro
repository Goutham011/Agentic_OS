# Agentic OS ProGuard Rules

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep class * extends dagger.hilt.android.HiltApplication { *; }
-keep class * extends dagger.hilt.android.HiltComponent { *; }
-keep class * extends dagger.hilt.android.HiltViewModel { *; }

# Keep Room entities and DAOs
-keep class com.agenticos.agent.models.** { *; }
-keep class com.agenticos.agent.data.** { *; }

# Keep Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep MediaPipe LLM classes
-keep class com.google.mediapipe.tasks.genai.** { *; }

# Keep ML Kit Text Recognition
-keep class com.google.mlkit.text.** { *; }

# Keep Porcupine
-keep class ai.picovoice.porcupine.** { *; }

# Keep Jsoup
-keep class org.jsoup.** { *; }

# Keep Retrofit/OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep Hilt/AssistedInject
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }

# Keep AndroidX
-keep class androidx.** { *; }

# Keep our service classes
-keep class com.agenticos.agent.service.** { *; }
-keep class com.agenticos.agent.accessibility.** { *; }
-keep class com.agenticos.agent.automation.** { *; }
-keep class com.agenticos.agent.tools.** { *; }
-keep class com.agenticos.agent.llm.** { *; }
-keep class com.agenticos.agent.voice.** { *; }
-keep class com.agenticos.agent.MainActivity { *; }
-keep class com.agenticos.agent.BootReceiver { *; }
-keep class com.agenticos.agent.AgenticOSApplication { *; }

# Keep Parcelable/Serializable
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Don't warn about missing classes
-dontwarn org.jsoup.**
-dontwarn com.google.mediapipe.**
-dontwarn ai.picovoice.porcupine.**
-dontwarn kotlinx.serialization.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.coroutines.**