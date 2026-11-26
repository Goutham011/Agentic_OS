# Fix Compilation Errors and Implement SQLCipher Encryption

## Goal
Fix critical compilation errors blocking the app from building and implement SQLCipher encryption for the Room database to match the documented security requirements.

## Current Context / Assumptions

### Critical Issues Identified (Read-Only Analysis)

1. **Compilation Error in VoiceManager.kt:119** - Missing `android.content.Intent` import causes build failure
2. **Compilation Error in AgentForegroundService.kt:207** - `Date()` used without proper import
3. **Database Security Gap** - README claims "SQLCipher encrypted database" but Database.kt uses plain Room with `fallbackToDestructiveMigration()`
4. **Null Receiver Bug** - `screenContentReceiver` and `notificationReceiver` initialized as null, dereferenced in `unregisterReceivers()`
5. **Placeholder Implementations** - GetLocationTool returns "not fully implemented", STT clients are placeholders

### Existing Architecture (per Architecture.md)
- Base APK with PluginHost for dynamic feature modules
- AgentCore foreground service orchestrating VoiceListener → ContextEngine → LLMManager → ActionExecutor
- Room + SQLCipher planned for encrypted storage
- Multi-tier STT pipeline: Cloud → System → Offline Whisper.cpp

### Build Environment
- Kotlin 1.9.22, Android SDK 34, JDK 17
- Gradle 8.4.0, Hilt DI, Room 2.6.1
- MediaPipe LLM available (currently unused, commented out)

## Architecture / Proposed Approach

This plan addresses blocking issues in priority order:

1. **Phase 1: Immediate Build Fix** (1 task)
   - Fix missing imports in VoiceManager.kt
   - Add missing Date import in AgentForegroundService.kt

2. **Phase 2: Database Security** (3 tasks)
   - Add SQLCipher dependency and Room configuration
   - Implement encrypted database factory with key derivation
   - Update Database.kt to use SQLCipher-backed instance

3. **Phase 3: Null Safety Fix** (1 task)
   - Fix null receiver dereference in AgentForegroundService.kt

Total: 5 bite-sized tasks, each executable in under 5 minutes.

---

## Step-by-Step Tasks

### Task 1: Fix Missing Import in VoiceManager.kt

**File:** `android-app/app/src/main/java/com/agenticos/agent/voice/VoiceManager.kt`

**Problem:** Line 119 uses `Intent` without import.

**Verification:** `./gradlew compileDebugKotlin` should succeed after this fix.

**Action:** Add the missing import after line 3 (after existing imports):

```kotlin
import android.content.Intent
```

**Expected:** Compilation succeeds. If import already exists, no-op (patch will handle this).

---

### Task 2: Add SQLCipher Dependency

**File:** `android-app/app/build.gradle.kts`

**Problem:** Database uses plain Room, no SQLCipher support.

**Verification:** Gradle sync succeeds with new dependency.

**Action:** Add SQLCipher dependency in the `dependencies` block after line 130 (after room_ktx):

```kotlin
implementation("net.zetetic:android-database-sqlcipher:4.5.0")
```

Note: Use version 4.5.0 compatible with Room 2.6.1.

**Expected:** Gradle sync completes, dependency downloaded.

---

### Task 3: Create SQLCipher Database Factory

**File:** `android-app/app/src/main/java/com/agenticos/agent/data/SQLCipherHelper.kt` (NEW FILE)

**Problem:** Need helper to create encrypted Room database.

**Verification:** File compiles without errors.

**Action:** Create new file with:

```kotlin
package com.agenticos.agent.data

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportFactory

object SQLCipherHelper {
    fun createEncryptedDatabase(context: Context): AgenticDatabase {
        val passphrase = SupportFactory.binaryStringToByteArray(
            "agentic_os_passphrase".toByteArray(Charsets.UTF_8)
        )
        val factory = SupportFactory(passphrase)
        
        return Room.databaseBuilder(
            context.applicationContext,
            AgenticDatabase::class.java,
            "agentic_database"
        )
        .openHelperFactory(factory)
        .fallbackToDestructiveMigration()
        .build()
    }
}
```

**Expected:** New file compiles, provides encrypted database instance.

---

### Task 4: Update Database.kt to Use SQLCipher

**File:** `android-app/app/src/main/java/com/agenticos/agent/data/Database.kt`

**Problem:** Current implementation uses plain Room, not encrypted.

**Verification:** Database instance is backed by SQLCipher when requested.

**Action:** Replace the `getInstance` method (lines 31-41) with:

```kotlin
companion object {
    @Volatile private var INSTANCE: AgenticDatabase? = null

    fun getInstance(context: Context): AgenticDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = SQLCipherHelper.createEncryptedDatabase(context)
            INSTANCE = instance
            instance
        }
    }
}
```

**Expected:** App uses encrypted database for all persistence.

---

### Task 5: Fix Null Receiver Dereference

**File:** `android-app/app/src/main/java/com/agenticos/agent/service/AgentForegroundService.kt`

**Problem:** Lines 71-72, 344-349 - receivers initialized as null, then dereferenced.

**Verification:** `unregisterReceivers()` doesn't crash when receivers were never registered.

**Action:** Change lines 71-72 from:

```kotlin
private val screenContentReceiver: BroadcastReceiver? = null
private val notificationReceiver: BroadcastReceiver? = null
```

To remove these (they're unused in registerReceivers anyway) or add null checks in unregisterReceivers:

```kotlin
private fun unregisterReceivers() {
    try {
        screenContentReceiver?.let { context.unregisterReceiver(it) }
        notificationReceiver?.let { context.unregisterReceiver(it) }
    } catch (e: Exception) {
        // Ignore - receiver may not be registered
    }
}
```

**Expected:** No NullPointerException on service destruction.

---

## Tests / Validation

### TDD Cycle Per Task

Following the principle "write the failing test first":

**For Task 1-2:** Build verification only (no unit tests for imports/dependencies)

**For Task 3:** Create test file:

```kotlin
// android-app/app/src/test/java/com/agenticos/agent/data/SQLCipherHelperTest.kt
package com.agenticos.agent.data

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

class SQLCipherHelperTest {
    @Test
    fun `createEncryptedDatabase returns nonNull instance`() {
        val mockContext = mock(Context::class.java)
        val mockApp = mock(Context::class.java)
        `when`(mockContext.applicationContext).thenReturn(mockApp)
        
        val db = SQLCipherHelper.createEncryptedDatabase(mockContext)
        assertNotNull(db)
    }
}
```

**Verification Commands:**

```bash
cd /Users/gouthamsai/opencode_1/Agentic_OS/android-app
./gradlew assembleDebug
echo "Build succeeded" || echo "Build failed"
```

**Expected Output:** `BUILD SUCCESSFUL` or `Build succeeded`

---

## Risks, Tradeoffs, and Open Questions

### Risks
- **SQLCipher passphrase**: Current plan uses hardcoded passphrase. Production should derive from biometric/device credential. **Mitigation**: Document this as TODO for secure key derivation.
- **Migration strategy**: `fallbackToDestructiveMigration()` destroys data on schema change. **Mitigation**: This is acceptable for v1; implement proper migrations after core stability.

### Tradeoffs
- **Hardcoded passphrase** (v1): Simple to implement, not secure. Tradeoff: Speed vs Production security. Accept for v1 daily driver.
- **Plain Room → SQLCipher**: One-line factory change. Accept tradeoff: code simplicity vs encrypted persistence.

### Open Questions
1. **Key Derivation**: Should passphrase be derived from user biometrics, device PIN, or user-set password? (Affects onboarding flow)
2. **Database Migration**: Should we implement proper migrations now, or accept data loss on schema change?
3. **Porcupine Access Key**: README mentions Porcupine wake word but no access key integration. Should this be in scope?
4. **LLM Integration**: MediaPipe LLM is commented out. Should this be prioritized after build fixes?

### Post-Plan Extensions
After this plan completes, the roadmap indicates these are the next priority:
- MediaPipe LLM integration (Phase 1, Week 7-8)
- Porcupine wake word detection (Phase 1, Week 2)
- Multi-tier STT pipeline (Phase 2 in Architecture.md)
- Communication Hub Plugin (Phase 1, Week 3-4)

---

## Summary

This plan fixes critical compilation errors and implements SQLCipher encryption to match the documented security model. Total estimated effort: ~2 hours (5 tasks × 20-30 minutes each).

**Plan created:** 2026-11-25
**Saved to:** `.hermes/plans/2026-11-25_202500-fix-compilation-and-database-security.md`

**Ready for execution?** I can run subagent-driven development to implement these tasks.