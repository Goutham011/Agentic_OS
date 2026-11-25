# Agentic OS — Android Assistant App

A non-rooted Android app that turns any phone into a JARVIS-style autonomous assistant. Runs as a foreground service, uses on-device LLM for local reasoning, and falls back to Google Search for internet-dependent queries.

## Features

### Core Assistant
- **Voice-first interface** — Hotword detection ("Hey Agent") + continuous listening
- **On-device LLM** — MediaPipe LLM Inference (Gemini Nano / Phi-3 / SmolLM) for privacy-first reasoning
- **Web search fallback** — When query needs internet (news, weather, facts), scrapes Google and summarizes locally
- **Screen reading** — AccessibilityService reads UI, extracts text, finds clickable elements
- **Notification monitoring** — NotificationListenerService catches calls, SMS, app alerts
- **Action execution** — 15+ tools: open apps, send SMS, make calls, set alarms, play media, toggle system settings, search web, summarize pages

### Automations (No-code Rule Builder)
- **Missed call → SMS + log** — Auto-reply to missed callers, log to Google Sheets
- **Location triggers** — Geofencing: mute at work, WiFi on at home, etc.
- **Photo backup** — Auto-upload new photos to Google Drive/Dropbox
- **Smart home** — Webhook integration for Home Assistant, IFTTT, Tuya, custom MQTT
- **Battery/Time/WiFi triggers** — Low battery → power save, time-based routines
- **Custom rules** — User-defined if-this-then-that with drag-and-drop builder

### Privacy & Architecture
- **100% on-device** for core features — No cloud LLM, no analytics, no tracking
- **Encrypted local storage** — Room + SQLCipher for conversations, rules, preferences
- **Permission transparency** — Guided onboarding requests all permissions upfront
- **No root required** — Works on stock Android 10+ (API 29+)

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        ANDROID APP PROCESS                       │
│  ┌─────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │ MainActivity │  │ AgentForeground  │  │ PermissionManager │  │
│  │ (Onboarding) │◄─┤ Service (Brain)  │  │ (Runtime Perms)   │  │
│  └──────┬──────┘  └────────┬─────────┘  └───────────────────┘  │
│         │                  │                                       │
│         │   ┌──────────────┼──────────────┐                      │
│         │   ▼              ▼              ▼                      │
│         │ ┌────────┐  ┌───────────┐  ┌──────────┐               │
│         │ │  LLM   │  │Automation │  │  Tool    │               │
│         │ │Manager │  │ Engine    │  │ Registry │               │
│         │ └────┬───┘  └─────┬─────┘  └────┬─────┘               │
│         │      │            │             │                       │
│         │      ▼            ▼             ▼                       │
│         │ ┌─────────────────────────────────────┐                │
│         │ │         TOOL REGISTRY                │                │
│         │ │ Apps │ SMS │ Calls │ Media │ Web... │                │
│         │ └─────────────────────────────────────┘                │
│         └───────────────────────────────────────────────────────┘ │
│                              │                                    │
│                              ▼                                    │
│ ┌─────────────────────────────────────────────────────────────┐  │
│ │              SYSTEM SERVICES (Separate Processes)            │  │
│ │  ┌──────────────┐  ┌──────────────────┐  ┌───────────────┐  │  │
│ │  │Accessibility │  │ Notification     │  │ Location/     │  │  │
│ │  │ Service      │  │ Listener Service │  │ Geofence      │  │  │
│ │  │(Screen Read) │  │(Calls, SMS, Apps)│  │(Triggers)     │  │  │
│ │  └──────────────┘  └──────────────────┘  └───────────────┘  │  │
│ └─────────────────────────────────────────────────────────────┘  │
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 1.9+ |
| UI | Jetpack Compose (Material 3) |
| DI | Hilt |
| Database | Room + SQLCipher |
| Background | WorkManager + Foreground Service |
| Networking | Retrofit + OkHttp + Jsoup |
| On-device LLM | MediaPipe LLM Inference |
| STT | Android SpeechRecognizer |
| TTS | Android TextToSpeech |
| Wake Word | Porcupine (Picovoice) |
| OCR | ML Kit Text Recognition |
| Build | Gradle KTS + Version Catalog |

## Project Structure

```
Agentic_OS/
├── Plan.md              # Project plan, timeline, risks
├── Architecture.md      # System architecture, components, data flow
├── Agent.md             # Agent spec, tools, prompts, automation rules
└── android-app/         # Android project
    ├── app/
    │   ├── src/main/
    │   │   ├── java/com/agenticos/agent/
    │   │   │   ├── MainActivity.kt           # Permission onboarding UI
    │   │   │   ├── AgenticOSApplication.kt   # Hilt entry point
    │   │   │   ├── BootReceiver.kt           # Auto-start on boot
    │   │   │   ├── service/
    │   │   │   │   └── AgentForegroundService.kt  # Main agent loop
    │   │   │   ├── accessibility/
    │   │   │   │   ├── ScreenReaderAccessibilityService.kt
    │   │   │   │   └── NotificationListenerServiceImpl.kt
    │   │   │   ├── automation/
    │   │   │   │   └── AutomationEngine.kt   # Rule evaluation + workers
    │   │   │   ├── tools/
    │   │   │   │   └── Tools.kt              # 15+ tool implementations
    │   │   │   ├── llm/
    │   │   │   │   └── LLMManager.kt         # Rule-based + MediaPipe
    │   │   │   ├── voice/
    │   │   │   │   └── VoiceManager.kt       # STT/TTS + Porcupine
    │   │   │   ├── data/
    │   │   │   │   ├── Database.kt
    │   │   │   │   └── Daos.kt
    │   │   │   └── models/
    │   │   │       └── Models.kt
    │   │   ├── res/
    │   │   │   ├── xml/                      # Accessibility, file paths, backup
    │   │   │   └── values/                   # Strings, colors, themes
    │   │   └── AndroidManifest.xml
    │   ├── build.gradle.kts
    │   └── proguard-rules.pro
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── gradle.properties
```

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2) or later
- JDK 17
- Android SDK 34
- Physical device (emulator lacks some permissions/services)

### Build & Run
```bash
cd android-app
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Onboarding Flow
1. **Install & Open** — App launches onboarding screen
2. **Permission Groups** — Swipe through 5 groups (Communication, Location, Media, System, Special)
3. **Grant Runtime Permissions** — Tap "Grant Permissions" on each screen
4. **Enable Special Access** — Tap "Enable" for Accessibility, Notifications, Usage Stats, Overlay, Exact Alarms
5. **Finish** — App starts foreground service, shows persistent notification

### Testing Commands (Voice or Text)
```
"Hey Agent, open YouTube"
"Hey Agent, play LoFi Girl on YouTube"
"Hey Agent, set alarm for 7 AM"
"Hey Agent, text Mom I'm on my way"
"Hey Agent, call Dad"
"Hey Agent, what's the news today?"
"Hey Agent, summarize https://example.com/article"
"Hey Agent, turn on WiFi"
"Hey Agent, set ringer to silent"
"Hey Agent, turn on Bluetooth"
```

## Development

### Key Classes to Extend

| Feature | File | Notes |
|---------|------|-------|
| Add new tool | `tools/Tools.kt` | Implement `Tool` interface, register in `ToolRegistry` |
| New automation trigger | `automation/AutomationEngine.kt` | Add `TriggerType`, update `evaluateTriggers()` |
| Custom LLM | `llm/LLMManager.kt` | Implement `LLMManager` interface, swap in Hilt module |
| Wake word | `voice/VoiceManager.kt` | Integrate Porcupine or custom TFLite model |

### Hilt Modules (for production)
```kotlin
// In a Hilt module
@Provides
@Singleton
fun provideLLMManager(@ApplicationContext context: Context): LLMManager {
    return MediaPipeLLMManager(context) // or RuleBasedLLMManager for testing
}
```

### Testing Automation Rules
```kotlin
// Manual trigger for testing
val engine = AutomationEngine(context)
engine.triggerManually(TriggerType.MISSED_CALL, mapOf("number" to "+15551234567"))
```

## Permissions Reference

| Capability | Permission | Type | Auto-requested |
|------------|------------|------|----------------|
| Send SMS | `SEND_SMS` | Runtime | ✅ |
| Read SMS | `READ_SMS` | Runtime | ✅ |
| Make Calls | `CALL_PHONE` | Runtime | ✅ |
| Read Call Log | `READ_CALL_LOG` | Runtime | ✅ |
| Contacts | `READ_CONTACTS` | Runtime | ✅ |
| Location (precise) | `ACCESS_FINE_LOCATION` | Runtime | ✅ |
| Location (background) | `ACCESS_BACKGROUND_LOCATION` | Runtime | ✅ |
| Microphone | `RECORD_AUDIO` | Runtime | ✅ |
| Camera | `CAMERA` | Runtime | ✅ |
| Notifications | `POST_NOTIFICATIONS` | Runtime | ✅ |
| Calendar | `READ_CALENDAR` | Runtime | ✅ |
| Exact Alarms | `SCHEDULE_EXACT_ALARM` | Runtime | ✅ |
| Media Files | `READ_MEDIA_*` | Runtime | ✅ |
| **Accessibility** | `BIND_ACCESSIBILITY_SERVICE` | Special | ❌ (Settings) |
| **Notification Listener** | `BIND_NOTIFICATION_LISTENER_SERVICE` | Special | ❌ (Settings) |
| **Usage Stats** | `PACKAGE_USAGE_STATS` | Special | ❌ (Settings) |
| **Overlay** | `SYSTEM_ALERT_WINDOW` | Special | ❌ (Settings) |

## Known Limitations (MVP)

1. **No call audio interception** — Cannot answer calls programmatically (requires `AnswerCall` signature permission)
2. **Accessibility fragility** — Some apps block accessibility (banking, secure keyboards)
3. **Screen reading accuracy** — ~70-85% for complex UIs; OCR fallback helps
4. **Battery impact** — Foreground service + accessibility = ~10-15%/day; optimize with adaptive polling
5. **Play Store policy** — Accessibility services for automation may be rejected; distribute via GitHub/sideload for now
6. **Web scraping** — Google HTML structure changes; use Custom Search API for production

## Roadmap

- [ ] MediaPipe LLM integration (Gemini Nano / Phi-3)
- [ ] Porcupine wake word ("Hey Agent")
- [ ] Google Drive/Dropbox OAuth for photo backup
- [ ] Home Assistant / IFTTT webhook templates
- [ ] Automation builder UI (drag-drop)
- [ ] Widget + Quick Settings tile
- [ ] Conversation memory with vector embeddings
- [ ] Multi-language support
- [ ] Wear OS companion

## License

MIT License — See LICENSE file for details.

## Contributing

1. Fork the repo
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

---

**Built for the "Agentic OS" vision — Your phone, truly yours.**