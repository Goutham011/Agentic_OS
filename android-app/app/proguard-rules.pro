# ==========================================================
# R8 / ProGuard rules - Agentic OS release build
# ==========================================================

# App code: services, workers and accessibility classes are
# instantiated by the Android framework via reflection - keep intact
-keep class com.agenticos.agent.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keepclassmembers class * { @dagger.hilt.android.components.*; @dagger.hilt.android.scopes.*; }
-keep class * extends androidx.hilt.work.HiltWorker { *; }

# Room (entities, DAOs and generated impl)
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Dao interface * { *; }
-keepclassmembers class androidx.room.** { *; }

# SQLCipher
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# Porcupine (native bridge loaded via reflection)
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# kotlinx.serialization (models serialized at runtime)
-keepclassmembers class com.agenticos.agent.models.** { *; }

# Compose (standard consumer rules bundled; suppress spurious warnings)
-dontwarn androidx.compose.runtime.**

# General
-dontwarn java.lang.invoke.**
