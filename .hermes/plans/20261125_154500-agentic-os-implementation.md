# Agentic OS — Build Plan (v2)
**Created:** 2026-11-25 15:45:00 IST
**Last Updated:** 2026-11-25 17:00:00 IST
**Goal:** Build a production-quality Android voice assistant that handles local tasks, communication, automation, and proactive intelligence with a 22MB quantized on-device LLM.

---

## Architecture Overview

```
User
  │
  ▼
┌─────────────────────────────────────────────────────────────┐
│                    Agentic OS (Android)                     │
│                                                             │
│  ┌─────────────┐   ┌─────────────┐   ┌──────────────────┐  │
│  │ Voice Layer │   │ UI Layer    │   │ Automation       │  │
│  │             │   │             │   │                  │  │
│  │ Wake Word   │   │ Main        │   │ Trigger Engine   │  │
│  │ (Porcupine) │   │ Screen      │   │ (Time/Location/  │  │
│  │             │   │ (Compose)   │   │  Notification/   │  │
│  │ VAD         │   │             │   │  WiFi/Battery)   │  │
│  │ (MediaPipe) │   │ Automation  │   │                  │  │
│  │             │   │ Builder UI  │   │ Action Executor  │  │
│  │ STT         │   │             │   │ (WorkManager)    │  │
│  │ (Whisper)   │   │ Settings    │   │                  │  │
│  │             │   │             │   └──────────────────┘  │
│  │ TTS         │   └─────────────┘   ┌──────────────────┐  │
│  │ (Google)    │                     │ Context Engine   │  │
│  └──────┬──────┘                     │                  │  │
│         │                            │ Screen Content   │  │
│  ┌──────▼────────────────────────────▼─────────────────┐  │
│  │                    Agent Core                       │  │
│  │                                                    │  │
│  │  ┌────────────────────────────────────────────┐    │  │
│  │  │          LLM Orchestrator                  │    │  │
│  │  │  Phi-3-mini INT4 (22MB, ONNX Runtime)     │    │  │
│  │  │  + LoRA fine-tune (1GB, optional)         │    │  │
│  │  └────────────────────────────────────────────┘    │  │
│  │                                                    │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌───────────┐  │  │
│  │  │ Tool        │  │ Memory      │  │ Context   │  │  │
│  │  │ Registry    │  │ (Room DB)   │  │ Builder   │  │  │
│  │  │             │  │             │  │           │  │  │
│  │  │ 20+ tools  │  │ Rules       │  │ Location  │  │  │
│  │  │ registered │  │ Conv Memory │  │ Time      │  │  │
│  │  │             │  │ Prefs       │  │ Screen    │  │  │
│  │  └─────────────┘  └─────────────┘  └───────────┘  │  │
│  └────────────────────────────────────────────────────┘  │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │              Tool Implementations                   │  │
│  │                                                     │  │
│  │  Communication    System         Media              │  │
│  │  ───────────      ──────         ─────              │  │
│  │  • send_sms       • open_app     • play_music       │  │
│  │  • read_sms       • list_apps    • control_music    │  │
│  │  • make_call      • set_alarm    • photo_ops        │  │
│  │  • whatsapp_msg   • toggle_wifi  • file_ops         │  │
│  │  • email          • toggle_bt    • shortcut_mgt     │  │
│  │  • auto_reply     • set_dnd      • backup           │  │
│  │                                                     │  │
│  │  Info             Location       Proactive          │  │
│  │  ────             ────────       ─────────          │  │
│  │  • web_search     • get_loc      • daily_briefing   │  │
│  │  • summarize_url  • geofence     • smart_suggest    │  │
│  │  • weather        • activity     • auto_focus       │  │
│  │  • calendar       • battery      • weather_alert    │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │              Persistence                            │  │
│  │  Room + SQLCipher (encrypted)                       │  │
│  │  • AutomationRules   • ConversationMemory           │  │
│  │  • UserPreferences   • AppUsageStats                │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 1.9.24 |
| UI | Jetpack Compose | BOM 2024.09.03 |
| DI | Hilt | 2.51.1 |
| Database | Room + SQLCipher | 2.6.1 + 4.5.4 |
| Background | WorkManager | 2.9.0 |
| Networking | OkHttp + Retrofit | 4.12.0 + 2.9.0 |
| HTML Parsing | Jsoup | 1.17.2 |
| Voice (Wake) | Porcupine | 1.6 |
| Voice (VAD) | MediaPipe VAD | 0.10.14 |
| Voice (STT) | Google Cloud Speech + System STT + Whisper.cpp | 4.15.0 |
| LLM | ONNX Runtime + Phi-3-mini INT4 | 1.17.0 |
| Location | Play Services Location | 21.2.0 |
| Text Recognition | ML Kit | 16.0.0 |
| Build | AGP | 8.3.0 |
| Compile SDK | Android | 34 |
| Min SDK | Android | 26 (Android 8.0) |

---

## APK Size Budget

| Component | Size | Delivery |
|-----------|------|----------|
| Base app (code, resources, Compose) | ~25 MB | Always installed |
| Native libraries (ONNX Runtime, Porcupine) | ~15 MB | Always installed |
| SQLCipher + Room | ~5 MB | Always installed |
| **Base APK total** | **~45 MB** | **Play Store install** |
| Whisper Tiny model (INT4) | ~8 MB | Dynamic Feature (on demand) |
| Phi-3-mini LLM (INT4) | ~22 MB | Play Asset Delivery (on demand) |
| LoRA weights (optional) | ~100 MB | Download on first AI use |
| **Full install (all modules)** | **~185 MB** | After enabling all features |

---

## Feature Inventory

### Phase 0: Critical Missing Components (BLOCKER)

| # | Component | File | Issue |
|---|-----------|------|-------|
| 0.1 | NotificationEvent | `utils/NotificationEvent.kt` | File missing |
| 0.2 | Daos.kt verification | `data/Daos.kt` | Verify Room DAOs compile |
| 0.3 | Bundle import | `accessibility/ScreenReaderAccessibilityService.kt` | Missing `import android.os.Bundle` |
| 0.4 | SupervisorJob import | `automation/AutomationEngine.kt` | Missing import |
| 0.5 | Converters.kt | `utils/Converters.kt` | Room TypeConverters missing |
| 0.6 | AgenticApplication | `AgenticApplication.kt` | Create with @HiltAndroidApp |

### Phase 1: Voice Pipeline (Working End-to-End)

| # | Task | Details |
|---|------|---------|
| 1.1 | PorcupineWrapper — real implementation | Replace stub with Picovoice Porcupine SDK |
| 1.2 | VadDetectorWrapper — real implementation | Replace stub with MediaPipe VoiceActivityDetector |
| 1.3 | CloudSTTClient — Google Cloud Speech | Replace stub. Use `google-cloud-speech` library |
| 1.4 | OfflineSTTClient — Whisper.cpp JNI | JNI wrapper. Load quantized model. |
| 1.5 | VoiceRecognitionManager — orchestration | Wire up 4 STT tiers. Confidence scoring. |
| 1.6 | VoiceManager — hotword + command flow | Implement `startHotwordDetection()`, `listenForCommand()` |
| 1.7 | TTS integration | Use Android TextToSpeech |

### Phase 2: LLM Integration (Phi-3-mini ONNX)

| # | Task | Details |
|---|------|---------|
| 2.1 | ONNX Runtime dependency | Add `com.microsoft.onnxruntime:onnxruntime-android:1.17.0` |
| 2.2 | Model download infrastructure | `ModelManager` class. Download via Play Asset Delivery |
| 2.3 | Tokenizer implementation | `PhiTokenizer` class. Load `tokenizer.json` |
| 2.4 | Inference engine | `OnnxLLMInference` class. Token-by-token generation |
| 2.5 | Prompt template for tool calling | JSON output format for tool execution |
| 2.6 | LLMManager — real implementation | Replace RuleBasedLLMManager with ONNX version |
| 2.7 | Rule-based fallback | Keep as fallback when ONNX unavailable |

### Phase 3: Tool Registry & Extensions (20+ Tools)

| Category | Tools |
|----------|-------|
| Communication | send_sms, read_sms, make_call, send_whatsapp, send_email, auto_reply |
| System | open_app, list_apps, set_alarm, set_timer, toggle_wifi, toggle_bluetooth, set_ringer_mode, set_dnd, create_shortcut |
| Media | play_media, control_music, photo_ops, file_ops, backup |
| Context | web_search, summarize_url, get_location, weather, calendar_ops, contacts_ops, battery_ops, system_info |

### Phase 4: Automation Engine

| # | Task | Details |
|---|------|---------|
| 4.1 | Trigger expansion | Time, Location, WiFi, Bluetooth, Battery, MissedCall, Notification, AppUsage, ScreenOn/Off, HeadphonePlug, Charging, LowStorage |
| 4.2 | Condition chaining | AND/OR grouping for multiple triggers |
| 4.3 | Action library | Speak, ShowNotification, SetDoNotDisturb, LockScreen, RunApp, Wait, etc. |
| 4.4 | Automation persistence | Room database with encrypted storage |

### Phase 5: Proactive Intelligence

| # | Task | Details |
|---|------|---------|
| 5.1 | UsageStatsManager | Track app usage patterns |
| 5.2 | Daily Briefing | 7 AM TTS summary |
| 5.3 | Smart Suggestions | Based on usage patterns |
| 5.4 | Focus Mode | Auto DND during meetings |
| 5.5 | Weather Alerts | Morning weather check |
| 5.6 | Battery Guard | Low battery auto-actions |
| 5.7 | Location Awareness | Arrival/departure triggers |

### Phase 6: UI (Compose)

| Screen | Features |
|--------|----------|
| Main Assistant | Floating mic, waveform, transcription, response cards |
| Onboarding | Permission requests, model download consent |
| Automation Builder | Drag-and-drop interfaces |
| Settings | Permissions, voice, AI, model management |
| Conversation History | Scrollable past interactions |

### Phase 7: Optimization & Production

| # | Task | Details |
|---|------|---------|
| 7.1 | ProGuard/R8 | Enable minification |
| 7.2 | Resource shrinking | Remove unused assets |
| 7.3 | ABI split | Keep only arm64-v8a |
| 7.4 | Dynamic Feature Modules | `:voice_offline`, `:ai_models` |
| 7.5 | SQLCipher | Encrypt database |
| 7.6 | Battery optimization | WorkManager batching |
| 7.7 | App signing | Release keystore with V2+V3 |

---

## Missing Files to Create

1. `utils/NotificationEvent.kt`
2. `utils/Converters.kt` (TypeConverters for TriggerSpec, ActionSpec, List<String>)
3. `AgenticApplication.kt`
4. `di/DatabaseModule.kt`
5. `di/ServiceModule.kt`
6. `di/ManagerModule.kt`
7. `llm/OnnxLLMInference.kt`
8. `llm/PhiTokenizer.kt`
9. `llm/ModelManager.kt`
10. `tools/ToolRegistry.kt`
11. `tools/WhatsappTool.kt`
12. `tools/EmailTool.kt`
13. `tools/MusicControlTool.kt`
14. `tools/CalendarTool.kt`
15. `tools/ContactsTool.kt`
16. `tools/WeatherTool.kt`
17. `tools/BatteryTool.kt`
18. `tools/SystemInfoTool.kt`
19. `automation/TriggerReceiver.kt`
20. `proactive/UsageTracker.kt`
21. `proactive/DailyBriefingWorker.kt`
22. `proactive/FocusModeManager.kt`
23+ UI components

---

## Verification Checklist

| # | Test | Expected Result |
|---|------|----------------|
| 1 | Install APK | 45MB base install |
| 2 | Download models | Whisper (8MB) + Phi-3 (22MB) |
| 3 | Wake word | "Hey Agentic" → chime |
| 4 | Open app | "Open YouTube" → YouTube opens |
| 5 | SMS send | "Send SMS to John saying hello" |
| 6 | Call | "Call Mom" → call initiated |
| 7 | LLM tool call | "Remind me to call Mom at 3pm" |
| 8 | Automation | "When WiFi turns on, open Netflix" |
| 9 | Daily briefing | 7 AM TTS summary |
| 10 | Offline STT | Works without internet |