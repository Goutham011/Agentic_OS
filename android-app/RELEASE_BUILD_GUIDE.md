# Release Build Guide

## Step 1: Generate Release Keystore

Run on your development machine (requires JDK):
```bash
# Navigate to project root
cd /Users/gouthamsai/opencode_1/Agentic_OS/android-app

# Generate keystore (run this in terminal)
keytool -genkey -v -keystore release.keystore -alias release \
  -keyalg RSA -keysize 2048 -validity 10000
```

**When prompted, remember:**
- Store password: Create a secure password
- Key password: Can be same as store password
- Key CN: Your name or company name
- Organizaton unit: Your role
- Organization: Your company
- City, State, Country: Your location

## Step 2: Create gradle.properties

Create or update `gradle.properties` in your project root:
```bash
# Edit gradle.properties
echo "KEYSTORE_PASSWORD=your_store_password" >> gradle.properties
echo "KEY_PASSWORD=your_key_password" >> gradle.properties
```

Or create `/Users/gouthamsai/opencode_1/Agentic_OS/android-app/gradle.properties`:
```properties
# Release keystore passwords
KEYSTORE_PASSWORD=your_secure_password_here
KEY_PASSWORD=your_key_password_here
RELEASE_STORE_PASSWORD=your_secure_password_here
RELEASE_KEY_ALIAS=release
RELEASE_KEY_PASSWORD=your_key_password_here
```

## Step 3: Verify Build Configuration

Check the build.gradle.kts is correctly configured:
```bash
# Verify signing config exists
grep -A 5 "signingConfigs" android-app/app/build.gradle.kts
```

## Step 4: Build Release APK

```bash
cd /Users/gouthamsai/opencode_1/Agentic_OS/android-app

# Clean previous builds
./gradlew clean

# Build release
./gradlew assembleRelease

# APK location:
echo "Release APK: app/build/outputs/apk/release/app-release.apk"
```

## Step 5: Verify APK

```bash
# Check APK integrity
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk

# Check for errors
if [ $? -eq 0 ]; then
  echo "✅ APK signature verified"
else
  echo "❌ Signature verification failed"
fi
```

## Step 6: Test on Physical Devices

**Required devices:**
- Pixel (Stock Android - baseline)
- Samsung (One UI - different permission handling)
- Xiaomi (HyperOS - MIUI security)
- OnePlus (OxygenOS - close to stock)

**Test checklist:**
```markdown
## Device Testing Checklist

### ✅ Pixel (Stock)
- [ ] App installs without Play Protect blocking
- [ ] All permissions granted on first run
- [ ] Accessibility Service starts
- [ ] Voice commands work
- [ ] Automation engine runs
- [ ] Background service persists

### ✅ Samsung (One UI)
- [ ] App installs
- [ ] Notification access granted
- [ ] Accessibility prompt shown correctly
- [ ] No app killed by battery optimization
- [ ] Doze mode compatibility

### ✅ Xiaomi (HyperOS)
- [ ] App installed despite MIUI security
- [ ] Permissions granted from Security Center
- [ ] Autostart enabled
- [ ] Battery saver compatible
- [ ] No crashes on boot

### ✅ OnePlus (OxygenOS)
- [ ] App works as expected
- [ ] No unexpected permission prompts
- [ ] Background behavior stable
```

## Step 7: Manual Testing Commands

```bash
# Install release build
adb install -r app/build/outputs/apk/release/app-release.apk

# Check if app runs
adb shell am start -n com.agenticos.agent/.MainActivity

# Verify services
adb shell dumpsys activity services | grep -i "agentic"
```

## Step 8: Prepare Release Notes

Create release notes in `RELEASE_NOTES.md`:
```markdown
# v1.0.0 - Production Release

## Security
- All secrets externalized to environment variables
- SQLCipher database passphrase derived from device fingerprint
- Minification and shrinking enabled
- Network security config with no cleartext traffic

## Permissions
- All permissions documented with rationale
- User consent flow completed during onboarding
- Accessibility service clearly described for users

## Verified on
- Pixel 8 (Android 14)
- Samsung S23 (One UI 6)
- Xiaomi 13 (HyperOS 2)
- OnePlus 11 (OxygenOS 13)
```

## Production Ready Checklist

| Item | Status | Notes |
|------|--------|-------|
| 🔐 Keystore generated | ☐ | Run keytool command |
| 🔑 Passwords in gradle.properties | ☐ | Never commit actual passwords |
| 🧪 APK builds successfully | ☐ | Run assembleRelease |
| ✅ Play Protect warning documented | ✓ | Added to README |
| 📱 Tested on 4+ device types | ☐ | Required for quality |
| 🛡️ ProGuard rules verified | ✓ | Already comprehensive |
| 📄 Release notes prepared | ☐ | Create RELEASE_NOTES.md |
| 🚀 Distribution channels set | ✓ | GitHub Releases configured |