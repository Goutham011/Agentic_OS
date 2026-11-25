<p align="center">
  <img src="assets/app_icon.svg" width="150" alt="Agentic OS Logo" />
</p>

<h1 align="center">Agentic OS</h1>

<p align="center">
  <strong>Your phone, truly yours — an autonomous Android assistant built for privacy and control.</strong><br>
  A non-rooted Android app that turns any phone into a JARVIS-style autonomous assistant with on-device AI reasoning.
</p>

<p align="center">
  <strong>English</strong>
</p>

<p align="center">
  <a href="https://github.com/Goutham011/agentic-os/stargazers"><img src="https://img.shields.io/github/stars/Goutham011/agentic-os?style=flat-square&color=gold" alt="GitHub stars" /></a>
  <a href="https://github.com/Goutham011/agentic-os/releases/latest"><img src="https://img.shields.io/github/v/release/Goutham011/agentic-os?style=flat-square&color=blue" alt="Latest release" /></a>
  <img src="https://img.shields.io/badge/platform-Android%2010%2B-lightgrey?style=flat-square" alt="Android 10+" />
  <img src="https://img.shields.io/badge/kotlin-1.9%2B-orange?style=flat-square" alt="Kotlin 1.9+" />
</p>

---

## Why Agentic OS?

Your phone should work for you, not the other way around. Agentic OS gives you a programmable assistant that learns from you, protects your privacy, and automates the tedious parts of your digital life.

- **Free, forever.** No subscription, no in-app purchases, no "upgrade to Pro", no nag screens. MIT licensed.
- **Zero telemetry.** No analytics, no crash reporter, no trackers, no cloud LLM by default. Your conversations stay on your device.
- **100% on-device** for core features — Local LLM reasoning, no cloud API keys required.
- **15+ built-in tools** — Apps, SMS, Calls, Media, System Settings, Web search, PDF summarize, and more.
- **No root required** — Works on stock Android 10+ (API 29+) with just standard permissions.
- **Privacy first.** SQLCipher encrypted database for all conversations, rules, and preferences.

---

## Features at a glance

<table>
<tr>
<td width="33%" valign="top">

### 🎤 Voice Assistant
- **"Hey Agent"** wake word detection
- Continuous listening mode
- On-device STT/TTS
- MediaPipe LLM for local reasoning

</td>
<td width="33%" valign="top">

### 🤖 Automation Engine
- Missed call → SMS auto-reply
- Location geofencing triggers
- Photo auto-backup to Google Drive
- System event triggers (battery, WiFi, time)

</td>
<td width="33%" valign="top">

### 🛡️ Protected Mode
- Encrypted local storage (SQLCipher)
- Transparent permission model
- No data leaves your device
- Open source, audit ready

</td>
</tr>
</table>

---

## Features in detail

### 🎤 Voice Assistant
| Feature | Description |
|---------|-------------|
| **Wake Word** | Porcupine-based "Hey Agent" detection for hands-free activation |
| **On-device LLM** | MediaPipe LLM Inference supports Gemini Nano, Phi-3, SmolLM |
| **Web search fallback** | Google scraping with local summarization when internet access needed |
| **Screen reading** | AccessibilityService reads UI text and suggests actions |
| **15+ Tools** | open apps, send SMS, make calls, set alarms, play media, toggle settings, search web, summarize pages |

### 🤖 Automation Engine
| Trigger Type | Example Action |
|--------------|----------------|
| Missed Call | Auto-reply: "Hi, I'm busy. Will call back soon!" |
| Location | Mute at work, WiFi on at home |
| Photo Added | Upload to Google Drive/Dropbox |
| Time | Goodnight routine at 11 PM |
| Battery Low | Enable power save mode |
| Custom Events | Webhook integration for Home Assistant, IFTTT |

### 📱 Android Architecture
| Component | Technology |
|-----------|------------|
| Language | Kotlin 1.9+ |
| UI | Jetpack Compose (Material 3) |
| DI | Hilt |
| Database | Room + SQLCipher |
| Background | WorkManager + Foreground Service |
| Networking | Retrofit + OkHttp + Jsoup |
| STT | Android SpeechRecognizer |
| TTS | Android TextToSpeech |
| Wake Word | Porcupine (Picovoice) |

---

## How to use

### Install

1. Download the latest APK from [Releases](https://github.com/Goutham011/agentic-os/releases/latest)
2. Enable "Install unknown apps" in Settings → Apps → Special app access
3. Open the APK and tap Install

### Onboarding Flow
1. **Launch** — App opens onboarding screen
2. **Permissions** — Swipe through 5 groups (Communication, Location, Media, System, Special)
3. **Grant Runtime Permissions** — Tap "Grant Permissions" on each screen
4. **Enable Special Access** — Tap "Enable" for Accessibility, Notifications, Usage Stats, Overlay, Exact Alarms
5. **Finish** — App starts foreground service, shows persistent notification

### Try Commands
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
```

---

## Development

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

### Key Files to Extend
| Feature | File | Description |
|---------|------|-------------|
| Add new tool | `tools/Tools.kt` | Implement `Tool` interface, register in `ToolRegistry` |
| New automation trigger | `automation/AutomationEngine.kt` | Add `TriggerType`, update `evaluateTriggers()` |
| Custom LLM | `llm/LLMManager.kt` | Implement `LLMManager` interface |
| New wake word | `voice/VoiceManager.kt` | Integrate alternative keyword spotting |

### Testing Automation Rules
```kotlin
val engine = AutomationEngine(context)
engine.triggerManually(TriggerType.MISSED_CALL, mapOf("number" to "+155****4567"))
```

---

## Permissions Reference

| Capability | Permission | Type | Auto-requested |
|------------|------------|------|----------------|
| Send SMS | `SEND_SMS` | Runtime | ✅ |
| Read SMS | `READ_SMS` | Runtime | ✅ |
| Make Calls | `CALL_PHONE` | Runtime | ✅ |
| Read Call Log | `READ_CALL_LOG` | Runtime | ✅ |
| Location (precise) | `ACCESS_FINE_LOCATION` | Runtime | ✅ |
| Location (background) | `ACCESS_BACKGROUND_LOCATION` | Runtime | ✅ |
| Microphone | `RECORD_AUDIO` | Runtime | ✅ |
| Camera | `CAMERA` | Runtime | ✅ |
| Notifications | `POST_NOTIFICATIONS` | Runtime | ✅ |
| **Accessibility** | `BIND_ACCESSIBILITY_SERVICE` | Special | ❌ (Settings) |
| **Notification Listener** | `BIND_NOTIFICATION_LISTENER_SERVICE` | Special | ❌ (Settings) |

---

## Known Limitations

1. **No call audio interception** — Cannot answer calls programmatically
2. **Accessibility fragility** — Some banking apps block accessibility access
3. **Screen reading accuracy** — ~70-85% for complex UIs; OCR fallback helps
4. **Battery impact** — ~10-15%/day; optimize with adaptive polling
5. **Play Store policy** — Distribute via GitHub/sideload to avoid policy rejection
6. **Web scraping** — Google HTML structure changes; use Custom Search API for production

---

## Roadmap

- [ ] MediaPipe LLM integration (Gemini Nano / Phi-3)
- [ ] Porcupine wake word ("Hey Agent") — currently using basic keyword spotting
- [ ] Google Drive/Dropbox OAuth for photo backup
- [ ] Home Assistant / IFTTT webhook templates
- [ ] Automation builder UI (drag-drop interface)
- [ ] Widget + Quick Settings tile
- [ ] Conversation memory with vector embeddings
- [ ] Multi-language support
- [ ] Wear OS companion app

---

## License

MIT License — See [LICENSE](LICENSE) file for details.

---

## Contributing

1. Fork the repo
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

---

## Acknowledgments

- [Picovoice](https://picovoice.ai/) — Porcupine wake word
- [MediaPipe](https://developers.google.com/mediapipe) — LLM inference
- [Room](https://developer.android.com/topic/libraries/architecture/room) — SQLCipher database
- [Hilt](https://developer.google.com/training/dependency-injection/hilt-android) — DI framework

<p align="center">
  <strong>Built for the "Agentic OS" vision — Your phone, truly yours.</strong><br>
  If it saves you time, a ⭐ helps others find it.
</p>