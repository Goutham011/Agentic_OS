# Agentic OS - Android Voice Assistant
## Work Completed Today (Nov 26, 2026)

### Completed Fixes

#### 1. Fixed AgentForegroundService Crash
**Problem:** Service crashed on launch with `Dispatchers.Main` in a foreground service context.

**Root Cause:** `AgentForegroundService` used `Dispatchers.Main` for coroutine scope initialization and in `toggleHotword()`. Services have no Main looper, causing immediate crash.

**Fix Applied:**
- Line 84: Changed `Dispatchers.Main` to `Dispatchers.Default`
- `toggleHotword()` method: Changed `Dispatchers.Main` to `Dispatchers.Default`

**Files Modified:**
- `app/src/main/java/com/agenticos/agent/service/AgentForegroundService.kt`

#### 2. APK Size Reduction
**Problem:** APK was 153 MB, limiting device compatibility.

**Fixes Applied:**
- Debug builds: Enabled `isShrinkResources = true` (minify disabled for debug)
- Release builds: Enabled `isMinifyEnabled = true` with ProGuard rules + `isShrinkResources = true`
- Removed unused Porcupine dependency (`ai.picoviece:porcupine-android:3.0.1`) - wake word code is commented out
- Java 17 compilation (required by build configuration)

**Files Modified:**
- `app/build.gradle.kts`

#### 3. VoiceManager Coroutine Scope Leak Fix
**Problem:** TTS processor coroutine was not properly cancelled on shutdown.

**Fix Applied:**
- Added `ttsProcessorJob: Job?` property to store coroutine reference
- `startTTSProcessor()` now stores the Job in `ttsProcessorJob`
- `shutdown()` now cancels `ttsProcessorJob` and sets it to null

**Files Modified:**
- `app/src/main/java/com/agenticos/agent/voice/VoiceManager.kt`

#### 4. AndroidManifest.xml Fix
**Problem:** Invalid permission declaration in foreground service.

**Issue:** `android:permission="android.permission.BIND_FOREGROUND_SERVICE"` is a system-level permission that cannot be declared in app manifest.

**Fix Applied:** Removed the invalid permission attribute from service declaration.

**Files Modified:**
- `app/src/main/AndroidManifest.xml`

---

### Pending Items

#### High Priority - Build Verification
- [ ] Install Java 17 (currently installed but system Java is broken on host)
- [ ] Rebuild APK with all fixes applied
- [ ] Verify APK size reduction
- [ ] Test on device to confirm crash is fixed

#### Medium Priority - Component Audit
- [ ] Full audit of `LLMManager.kt` for coroutine scope leaks (currently only partial coverage)
- [ ] Audit `AutomationEngine.kt` for resource leaks
- [ ] Audit `PluginHost.kt` for lifecycle management issues
- [ ] Review all managers for similar scope leak patterns

#### Low Priority - Optional Improvements
- [ ] Consider adding `androidComponents` block for native ABI filtering (requires NDK setup)
- [ ] Evaluate if MediaPipe native libraries can be stripped further
- [ ] Review ProGuard rules for aggressive shrinking without breaking functionality

---

### Technical Details

#### Environment Status
- Java 17: Installed at `/opt/homebrew/opt/openjdk@17` but system java is broken
- Android SDK: `/Users/gouthamsai/Library/Android/sdk`
- Gradle: 8.6
- Kotlin: 1.9.22

#### Key Files Reviewed
- `AgenticOSApplication.kt` - 7 lines, minimal Hilt Application, no issues
- `MainActivity.kt` - Initialization flow verified, no issues
- `AndroidManifest.xml` - Fixed invalid permission
- `build.gradle.kts` - Verified build configuration
- `VoiceManager.kt` - TTS scope leak fixed
- `AgentForegroundService.kt` - Dispatchers.Main crash fixed

#### APK Metrics
- Pre-fix debug APK: 153 MB at `app/build/outputs/apk/debug/app-debug.apk`
- Target: <100 MB for broader device compatibility

---

### Next Steps
1. Fix system Java to enable local APK rebuild
2. Verify all fixes work together
3. Document any additional issues discovered during testing