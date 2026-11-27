# R8 rules for Agentic OS
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.database.sqlcipher.SupportFactory
-keep class * extends androidx.room.RoomDatabase
-keep @interface room.Entity
-keep @interface room.Dao
-keep class * extends androidx.hilt.workflow.WorkerManager
-keep class com.agenticos.agent.Hilt_AgenticOSApplication { *; }
-keep public class com.agenticos.agent.** { public *; }
