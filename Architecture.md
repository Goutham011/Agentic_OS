# Agentic_OS — System Architecture

## High-Level Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                    ANDROID APP PROCESS                                │
│  ┌─────────────────┐  ┌─────────────────────┐  ┌────────────────────────────────┐   │
│  │  MainActivity   │  │   AgentCore         │  │   PluginHost (Dynamic Modules) │   │
│  │  (Onboarding +  │◄─┤  (Foreground        │  │  ┌─────────┐ ┌─────────────┐   │   │
│  │   Main UI)      │  │   Service)          │  │  │ Media   │ │ Comms       │...│   │
│  └────────┬────────┘  └──────────┬──────────┘  │  │ Intel   │ │ Hub         │   │   │
│           │                      │             │  └─────────┘ └─────────────┘   │   │
│           │         ┌────────────┼────────────┐  │  ┌─────────┐ ┌─────────────┐  │   │
│           │         ▼            ▼            ▼  │  │ Smart   │ │ Device      │   │   │
│           │  ┌──────────────────────────────────┐ │  │ Home    │ │ Maintenance │   │   │
│           │  │        CAPABILITY REGISTRY        │ │  └─────────┘ └─────────────┘   │   │
│           │  │  (Plugin Discovery & Tool Union)  │ │                                 │   │
│           │  └──────────────────────────────────┘ │                                 │   │
│           │                    │                  │                                 │   │
│           │         ┌──────────┼──────────┐      │                                 │   │
│           │         ▼          ▼          ▼      ▼                                 │   │
│           │  ┌────────────────────────────────────────────────────────────────┐   │   │
│           │  │                    AGENT CORE LOOP                              │   │   │
│           │  │  VoiceListener → ContextEngine → LLMManager → ActionExecutor   │   │   │
│           │  │         (STT)          (Fusion)      (Local/Cloud)    (Tools)  │   │   │
│           │  └────────────────────────────────────────────────────────────────┘   │   │
│           └─────────────────────────────────────────────────────────────────────────┘   │
│                                    │                                                     │
│                                    ▼                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │                         SYSTEM-LEVEL SERVICES (Separate Processes)               │   │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────────┐  │   │
│  │  │ Accessibility    │  │ Notification     │  │ Location / Geofence /        │  │   │
│  │  │ Service          │  │ Listener Service │  │ SensorHub                    │  │   │
│  │  │ (Screen Read +   │  │ (Calls, SMS,     │  │ (Triggers, Location,         │  │   │
│  │  │  UI Interaction) │  │  App Notifs)     │  │  Activity Recognition)       │  │   │
│  │  └──────────────────┘  └──────────────────┘  └──────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. AgentCore (Foreground Service)
**Responsibility:** The "brain" — keeps agent alive, runs core loop, coordinates plugins.
- `Service` with `FOREGROUND_SERVICE_TYPE_MANIFEST|MICROPHONE|LOCATION|DATA_SYNC` + persistent notification
- **Core Loop:** `VoiceListener → ContextEngine → LLMManager → ActionExecutor → VoiceResponder`
- **ContextEngine** fuses: screen content, notifications, location, sensors, calendar, knowledge graph → ~500 token summary for LLM
- Manages `WorkManager` for periodic background checks (indexing, triggers, maintenance)
- Lifecycle: starts on boot (`RECEIVE_BOOT_COMPLETED`), restarts on crash, survives Doze via foreground

### 2. PluginHost & CapabilityRegistry
**Responsibility:** Dynamic feature modules loaded on-demand. Base APK < 30 MB.
- **CapabilityPlugin Interface:**
  ```kotlin
  interface CapabilityPlugin {
      val id: String
      val requiredPermissions: Set<String>
      val tools: List<Tool>
      val triggers: List<TriggerType>
      val settingsUI: @Composable () -> Unit
      fun onEnable(context: Context)
      fun onDisable(context: Context)
  }
  ```
- **Built-in Plugins (Phase 1):**
  - `MediaIntelligencePlugin` — local music indexer, player, smart playlists, cross-source queue
  - `CommunicationHubPlugin` — unified inbox, quick-reply, contact timeline, spam filter, scheduled messages
  - `AutomationBuilderPlugin` — visual flow editor, template gallery, NL → rule, debugger
  - `ProactiveIntelligencePlugin` — meeting prep, focus sessions, travel mode, expense capture
- **Phase 2 Plugins:** SmartHome, Health, Finance, DevTools, KnowledgeGraph
- Plugins distributed as **Dynamic Feature Modules** (Play Feature Delivery) or sideloaded `.apk` modules

### 3. ContextEngine (Multi-Modal Fusion)
**Responsibility:** Fuse all signals into structured context for LLM.
```
Input Sources:
  ├─ ScreenReader (AccessibilityService) → visible text + clickable tree
  ├─ NotificationListener → calls, SMS, app alerts (filtered)
  ├─ LocationProvider → geofence state, activity (walking/driving/still)
  ├─ SensorHub → step count, heart rate (Wear OS), battery, network
  ├─ CalendarProvider → next event, free/busy, attendees
  ├─ KnowledgeGraph → relevant entities/relations for current context
  └─ PluginContext → plugin-specific state (media queue, inbox unread count)

Output: Structured Context (JSON, ~500 tokens)
  {
    "screen": {"package": "com.whatsapp", "text": "John: Hey...", "actions": ["reply", "call"]},
    "notifications": [{"app": "Gmail", "title": "3 new", "priority": "high"}],
    "location": {"geofence": "work", "activity": "still", "confidence": 0.92},
    "calendar": {"next": "Standup in 15m", "attendees": ["Sarah", "Mike"]},
    "knowledge": {"entities": ["John (contact)", "Standup (event)"]},
    "media": {"queue": ["LoFi Girl", "Podcast Ep 42"], "playing": false}
  }
```

### 4. LLMManager (Hybrid Inference)
**Responsibility:** On-device reasoning + cloud fallback for web queries.
- **Tier 1 — RuleBasedLLMManager** (prototype, deterministic): keyword matching → tool calls. Zero latency, 100% offline.
- **Tier 2 — MediaPipeLLMManager** (production): Gemma 2B / Phi-3 Mini / SmolLM via MediaPipe `LlmInference`. Quantized (4-bit), ~500MB model, runs on GPU/NPU.
- **Tier 3 — Cloud Fallback** (opt-in): When `needsInternet=true` → scrape Google via Jsoup → summarize locally. No cloud LLM.
- **Prompt Template** (injected every turn):
  ```
  SYSTEM: You are Agentic_OS. Current context: {{context_json}}. Available tools: {{tool_schemas}}.
  RULES: 1) Respond ONLY with JSON: {"tool": "...", "params": {...}, "speak": "..."}
         2) If needs_internet → use web_search/summarize_url
         3) For destructive actions → set requiresConfirmation=true
         4) Keep speak < 200 chars, TTS-friendly
  USER: {{voice_text}}
  ```

### 5. ActionExecutor (Safe Execution Sandbox)
**Responsibility:** Execute tool calls with safety, audit, rollback.
- **Pre-execution:**
  - Permission check (runtime + special)
  - Dry-run preview for destructive actions: "I'll send SMS to Mom: 'On my way'. Confirm?"
  - User confirmation (configurable per tool: never / sensitive / always)
- **Execution:** Tool.run(params) → ToolResult
- **Post-execution:**
  - Verify result (SMS sent? WiFi toggled? Alarm created?)
  - Rollback where possible (WiFi/BT/riner revert in 5 min if user says "undo")
  - Audit log: encrypted Room entry {timestamp, tool, params, result, userConfirmed}
- **Follow-up:** If result.needsFollowUp → re-inject into LLM loop

### 6. ScreenReader (AccessibilityService)
**Responsibility:** Read screen content + perform UI actions.
- `onAccessibilityEvent()` → `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_VIEW_CLICKED`, `TYPE_VIEW_SCROLLED`, `TYPE_VIEW_TEXT_CHANGED`
- Extracts `AccessibilityNodeInfo` tree → flattens to:
  - `fullText`: concatenated visible text (for LLM context)
  - `clickableElements`: [{className, text, contentDescription, bounds, clickable, enabled}]
  - `editTexts`: [{className, hint, text, bounds, focused}]
- **ML Kit OCR Fallback** for custom views (canvas, games, secure keyboards)
- **Action Execution:** `performAction(click, scroll, set_text, focus)` via node traversal + text matching
- Communicates with AgentCore via `LocalBroadcastManager` (screen changes) + `Messenger` (action requests)

### 7. NotificationListener (NotificationListenerService)
**Responsibility:** Capture + normalize all notifications.
- `onNotificationPosted()` → extracts: package, title, text, bigText, category, sender, timestamp, actions
- **Smart Categorization:** call, SMS, messaging (WhatsApp/Telegram/Signal), email, system, promo, OTP
- **Sensitive App Filter:** banking, auth, password managers excluded by default (user configurable)
- Forwards to: ContextEngine (context), AutomationEngine (triggers), CommunicationHubPlugin (unified inbox)
- `onListenerConnected()` → replay missed notifications on service restart

### 8. AutomationEngine (Rule Evaluation)
**Responsibility:** Evaluate user-defined rules + built-in templates.
- **Rule Schema** (Room entity):
  ```kotlin
  data class AutomationRule(
      val trigger: TriggerSpec,      // TIME, LOCATION_ENTER/EXIT, MISSED_CALL, NOTIFICATION, BATTERY, WIFI, SENSOR, CALENDAR
      val conditions: List<Condition>, // AND/OR logic: battery>20%, isWeekday, wifiSSID=="Home"
      val actions: List<ActionSpec>,   // tool + params (supports {{variable}} interpolation)
      val enabled: Boolean
  )
  ```
- **Evaluation Modes:**
  - **Instant:** Broadcast receivers (missed call, WiFi change, screen on/off) → immediate eval
  - **Periodic:** WorkManager (every 15 min, charging+WiFi) → time, battery, location, calendar
  - **Geofence:** `GeofencingClient` → enter/exit → instant eval
- **Debugger:** "Test Rule" simulates trigger, shows step-by-step execution log

### 9. KnowledgeGraph (Phase 2)
**Responsibility:** Semantic memory for entities, relations, observations.
- **Room Schema:**
  ```kotlin
  @Entity tableName="kg_entity"    (id, type, name, propertiesJson, embedding BLOB)
  @Entity tableName="kg_relation"  (fromId, toId, type, confidence)
  @Entity tableName="kg_observation" (entityId, key, value, timestamp, source)
  ```
- **Embedding Index:** ONNX model (MiniLM-L6) → vector similarity search via brute-force (small) or FAISS (large)
- **LLM Tool:** `query_knowledge_graph(query: string) → {entities, relations, observations}`
- **Sources:** Contacts, Calendar, Files, Messages, Notifications, User corrections
- **Privacy:** Entirely on-device, user can inspect/delete any node, export JSON

### 10. MediaIntelligencePlugin (Phase 1)
**Responsibility:** Unified local media experience.
- **MediaIndexerWorker** (WorkManager, periodic, charging+WiFi):
  - Queries `MediaStore.Audio/Video/Images` + `DocumentsContract` roots (Downloads, Telegram, WhatsApp, Syncthing)
  - Extracts metadata via `MediaMetadataRetriever` (title, artist, album, duration, genre, year)
  - Stores in Room: `MediaFile(id, path, mimeType, title, artist, album, duration, folder, dateAdded, playCount, lastPlayed, embedding?)`
- **LocalPlayer:** `MediaController` + `MediaSessionCompat` → controls local files + external players (MPV, VLC, Musicolet, Plex)
- **Smart Playlists:** "Recent additions", "Unheard > 30 days", "Workout (tempo > 120)", "By folder/artist/genre"
- **Cross-Source Queue:** `MediaSource` sealed class: `LocalFile`, `YouTube`, `RSS`, `Jellyfin` → single queue

### 11. CommunicationHubPlugin (Phase 1)
**Responsibility:** Unified messaging across apps.
- **UnifiedInbox:** NotificationListener captures all messaging notifications → chronological list grouped by contact
- **QuickReply:** AccessibilityService injects reply into focused input field of WhatsApp/Telegram/Signal/SMS/Email
- **ContactTimeline:** Aggregates CallLog + SMS + NotificationListener cache + ContactsContract → per-contact history
- **SmartFilters:** OTP auto-delete (regex + ML Kit Entity Extraction), promo silence, priority contacts
- **ScheduledMessages:** WorkManager + AlarmManager → send via SMS/NotificationListener UI injection

### 12. VoiceRecognitionManager (Multi-Tier STT Pipeline)
**Responsibility:** Near-perfect voice recognition with hybrid on-device/cloud/offline pipeline.

#### Architecture
```
[Mic] → [Audio Preprocessing] → [VAD] → [Wake Word: Porcupine] 
                                     │
                                     ▼
                            [STT Engine Selection]
                            ┌─────────┼─────────┐
                            ▼         ▼         ▼
                       Cloud STT  System STT  Offline STT
                    (Google API) (SpeechRec) (Whisper.cpp)
                            │         │         │
                            └─────────┼─────────┘
                                      ▼
                              [Confidence Scoring]
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
               > 0.85            0.60–0.85          < 0.60
             Accept + LLM       Retry w/          Ask repeat
             fuzzy correction   different tier
```

#### 1. Audio Preprocessing (Before STT)
**Critical for accuracy.** Raw mic audio is processed through:
- **Noise Suppression** — WebRTC NS (removes AC, traffic, fan noise)
- **Acoustic Echo Cancellation** — WebRTC AEC (removes TTS playback from mic)
- **Automatic Gain Control** — WebRTC AGC (normalizes quiet/loud speakers)
- **Voice Activity Detection** — MediaPipe VAD (only sends speech segments)
- **Beamforming** — Multi-mic directional pickup (if available)

#### 2. Wake Word Detection: Porcupine (Picovoice)
| Feature | Porcupine | Google Hotword | Custom TFLite |
|---------|-----------|----------------|---------------|
| Accuracy | 99%+ | 95% | 80-90% |
| CPU | < 10% | Variable | High |
| Offline | ✅ | ❌ | ✅ |
| Custom words | 3 free | ❌ | ✅ |
| Latency | 50ms | 200ms+ | 100ms+ |

**Implementation:** "Hey Agent" (configurable), free tier: 3 custom words. Runs in dedicated audio thread.

#### 3. Multi-Tier STT Engine (Never Fail)
**Tier 1: Google Cloud Speech-to-Text (Best Quality)**
- 95-98% accuracy on clean audio
- Streaming (real-time partial results)
- **Phrase hints** — bias decoder toward tool vocabulary (boost=20)
- **Word hints** — boost specific words (contacts, aliases)
- **Model:** `command_and_search` (optimized for short commands)
- **Enhanced:** `useEnhanced=true` for latest model
- Cost: $0.006/min (after $300 free credit)

**Tier 2: Android SpeechRecognizer (System Hybrid)**
- Uses Google's engine by default (cloud)
- Falls back to on-device engine (Samsung, Pixel)
- Free, no API key
- Good fallback when cloud unavailable

**Tier 3: Whisper.cpp (Offline)**
- OpenAI Whisper base/small model (4-bit quantized)
- 90-95% accuracy
- Runs on CPU/NPU, ~400MB model
- Fully offline — no internet needed

**Switching Logic:**
```kotlin
suspend fun recognize(audio: ByteArray, context: RecognitionContext): RecognitionResult {
    // Try Cloud first (best quality)
    if (networkAvailable && userPrefersCloud) {
        val cloud = googleCloudSTT.recognize(audio, context)
        if (cloud.confidence > 0.85) return cloud
    }
    // Try System recognizer
    val system = systemRecognizer.recognize(audio, context)
    if (system.confidence > 0.70) return system
    // Fallback to offline Whisper
    return whisperCpp.recognize(audio, context)
}
```

#### 4. Vocabulary Biasing (Context-Aware Decoding)
**Single biggest accuracy improvement.** Pass tool vocabulary as phrase hints:

```kotlin
val phraseHints = listOf(
    "open", "launch", "play", "pause", "skip",
    "alarm", "timer", "set", "remind",
    "text", "message", "call", "dial",
    "wifi", "bluetooth", "silent", "vibrate",
    "weather", "news", "search", "summarize"
) + getUserContacts() + getUserAliases() + getCustomVocabulary()

// Google Cloud STT request:
speechContexts = [{ phrases: phraseHints, boost: 20 }]
```

**Result:** Command accuracy jumps from ~80% → ~95%.

#### 5. Personalization (Voice Profile)
| Technique | How It Works | Benefit |
|-----------|-------------|---------|
| **Voice Enrollment** | User reads 20-30 phrases onboarding → speaker embedding | 5-10% accuracy boost |
| **Online Adaptation** | Store corrections → fine-tune acoustic model | Continuous improvement |
| **Contact Names** | Import contacts → add to phrase hints | "Call Mom" recognized |
| **Custom Vocabulary** | User-defined aliases ("Acme Corp" → "work") | Domain-specific accuracy |

**Onboarding Flow:** Read 20 phrases → build 192-d speaker embedding → store encrypted in Room.

#### 6. Confidence Scoring & Retry Logic
```kotlin
suspend fun recognizeWithRetry(audio: ByteArray, maxRetries = 2): RecognitionResult {
    repeat(maxRetries + 1) { attempt ->
        val result = recognizeOnce(audio, attempt)
        when {
            result.confidence > 0.85 -> return result
            result.confidence > 0.60 && attempt < maxRetries -> delay(100) // retry
            else -> return result.copy(needsRepeat = true)
        }
    }
}
```

#### 7. LLM Fuzzy Correction (Post-STT)
STT output → LLM corrects homophones → tool call.

| Raw STT | Corrected | Tool |
|---------|-----------|------|
| "set a larm for seven" | "set alarm for 7" | set_alarm |
| "open you tube" | "open youtube" | open_app |
| "play lofy girl" | "play lofi girl" | play_media |
| "text mom im on my wy" | "text mom I'm on my way" | send_sms |

```kotlin
suspend fun correctWithLLM(rawText: String): CorrectedCommand {
    val prompt = """
        SYSTEM: Correct STT errors. Available tools: ${tools.map { it.name }}
        USER (raw): "$rawText"
        Respond: {"corrected": "...", "tool": "...", "confidence": 0.0-1.0}
    """
    return llmManager.infer(prompt).toCorrectedCommand()
}
```

#### TTS (Unchanged)
- `TextToSpeech` (queue, interruption, rate/pitch/language)
- Supports interruption ("Stop" mid-sentence)
- System voices + optional cloud voices

#### Hotword → Command Flow
```
Porcupine detects "Hey Agent"
       │
       ▼
AudioPreprocessor + VAD active
       │
       ▼
Stream audio to STT (Cloud → System → Offline)
       │
       ▼
Confidence > 0.85 → LLM fuzzy correction → AgentCore loop
       │
       ▼
Low confidence → "Could you repeat that?"
```

## Data Flows

### Voice Command (Local Tool)
```
User: "Set alarm for 7 AM"
  │
  ▼
VoiceManager (STT) → "Set alarm for 7 AM"
  │
  ▼
ContextEngine → builds context (screen, notifications, location, calendar, kg)
  │
  ▼
LLMManager (MediaPipe) → {tool: "set_alarm", params: {time: "07:00"}, speak: "Alarm set for 7 AM"}
  │
  ▼
ActionExecutor → Permission check → Dry-run preview (if needed) → Tool.run()
  │
  ▼
ToolResult(success=true, summary="Alarm set for 07:00")
  │
  ▼
VoiceManager (TTS) → "Alarm set for 7 AM"
  │
  ▼
AuditLog → encrypted Room entry
```

### Web Query (Internet Required)
```
User: "What's the news?"
  │
  ▼
VoiceManager (STT) → "What's the news?"
  │
  ▼
LLMManager → detects needs_internet → {tool: "web_search", params: {query: "news today"}, speak: "Searching..."}
  │
  ▼
ActionExecutor → WebSearchTool (Jsoup scrapes Google) → top 5 results
  │
  ▼
LLMManager (local) → summarizes results → {tool: null, speak: "Top stories: ..."}
  │
  ▼
VoiceManager (TTS) → reads summary
```

### Automation Trigger (Missed Call → SMS)
```
Missed call → NotificationListener → broadcast
  │
  ▼
AutomationEngine (instant eval) → matches rule "Missed Call → SMS + Log"
  │
  ▼
ActionExecutor → SendSmsTool(params: {to: "{{caller}}", message: "I'll call back"})
  │
  ▼
WebhookTool(params: {url: "https://sheets.googleapis.com/...", payload: {caller, time}})
  │
  ▼
AuditLog + User notification (optional)
```

## Storage Architecture

| Data | Store | Retention | Encryption |
|------|-------|-----------|------------|
| Automation rules | Room (SQLite) | User-controlled | SQLCipher |
| Conversation memory | Room + Vector embeddings | 30 days default | SQLCipher |
| Knowledge Graph | Room (entities/relations/observations) + ONNX embeddings | Permanent | SQLCipher |
| Media Index | Room (MediaFile) | Permanent | SQLCipher |
| Voice recordings | Encrypted file cache | Delete after processing | AES-256 |
| LLM model weights | App-specific storage | Persistent (updateable) | N/A (read-only) |
| Audit Log | Room | 90 days | SQLCipher |
| Plugin modules | Dynamic feature modules | On-demand | Play integrity / APK signing |

## Permission Matrix

| Capability | Permission | Type | Auto-Requested | Plugin |
|------------|------------|------|----------------|--------|
| Send SMS | `SEND_SMS` | Runtime | ✅ | Comms |
| Read SMS | `READ_SMS` | Runtime | ✅ | Comms |
| Make Calls | `CALL_PHONE` | Runtime | ✅ | Comms |
| Read Call Log | `READ_CALL_LOG` | Runtime | ✅ | Comms |
| Contacts | `READ_CONTACTS` | Runtime | ✅ | Comms |
| Location (precise) | `ACCESS_FINE_LOCATION` | Runtime | ✅ | Core/Automation |
| Location (background) | `ACCESS_BACKGROUND_LOCATION` | Runtime | ✅ | Automation |
| Microphone | `RECORD_AUDIO` | Runtime | ✅ | Core/Voice |
| Camera | `CAMERA` | Runtime | ✅ | Proactive (receipt OCR) |
| Notifications | `POST_NOTIFICATIONS` | Runtime | ✅ | Core |
| Calendar | `READ_CALENDAR` | Runtime | ✅ | Proactive |
| Exact Alarms | `SCHEDULE_EXACT_ALARM` | Runtime | ✅ | Core/Automation |
| Media Files | `READ_MEDIA_AUDIO/VIDEO/IMAGES` | Runtime | ✅ | Media |
| **Accessibility** | `BIND_ACCESSIBILITY_SERVICE` | Special | ❌ (Settings) | Core/Comms |
| **Notification Listener** | `BIND_NOTIFICATION_LISTENER_SERVICE` | Special | ❌ (Settings) | Core/Comms |
| **Usage Stats** | `PACKAGE_USAGE_STATS` | Special | ❌ (Settings) | Automation/Device |
| **Overlay** | `SYSTEM_ALERT_WINDOW` | Special | ❌ (Settings) | Core (floating mic) |

## Tech Stack (Updated)

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Language | Kotlin 1.9+ | Modern, coroutines, null safety |
| UI | Jetpack Compose (Material 3) | Declarative, Canvas for Automation Builder |
| DI | Hilt | Standard, compile-time validation |
| Database | Room + KSP + SQLCipher | Type-safe, reactive, encrypted |
| Background | WorkManager + Foreground Service | Battery-aware, survives Doze |
| Dynamic Modules | Play Feature Delivery / Dynamic Feature Modules | Plugin architecture, base APK < 30 MB |
| Networking | Retrofit + OkHttp + Jsoup | Mature, interceptors, scraping |
| On-device LLM | MediaPipe LLM Inference (Gemma 2B / Phi-3 Mini) | On-device, GPU/NPU, quantized |
| STT | SpeechRecognizer (system) | No cloud dependency |
| TTS | TextToSpeech (system) | Offline, multi-language |
| Wake Word | Porcupine (Picovoice) | Low CPU, accurate, free tier |
| OCR | ML Kit Text Recognition | On-device, accurate |
| Embeddings | ONNX Runtime (MiniLM-L6) | Knowledge Graph semantic search |
| Build | Gradle KTS + Version Catalog + KSP | Type-safe, maintainable, fast |

## Security & Privacy

- **All LLM inference on-device** (no cloud API keys in core)
- **Web search** uses Google HTML scrape (Jsoup) or Custom Search API (user-provided key) → summarize locally
- **Room database encrypted** with SQLCipher (user passphrase derived from biometric/device credential)
- **No analytics, no crash reporting** to third parties (opt-in Firebase Crashlytics only)
- **Explicit consent screen** before enabling each special permission
- **Data export / delete** on request (GDPR / CCPA ready) — JSON export of all Room tables
- **Audit log** for every tool execution — user reviewable, exportable
- **Per-app monitoring consent** — user explicitly enables NotificationListener per package
- **No call audio recording** — only notification metadata (caller, duration, missed/answered)

## Plugin Development Guide (For Contributors)

```kotlin
// 1. Create dynamic feature module: :plugins:smart-home
// 2. Implement CapabilityPlugin
class SmartHomePlugin : CapabilityPlugin {
    override val id = "smart-home"
    override val requiredPermissions = setOf("INTERNET")
    override val tools = listOf(WebhookTool, MQTTTool, HomeAssistantTool)
    override val triggers = listOf(TriggerType.WEBHOOK, TriggerType.MQTT_MESSAGE)
    override val settingsUI = @Composable { SmartHomeSettingsScreen() }
    override fun onEnable(context: Context) { /* register webhooks */ }
    override fun onDisable(context: Context) { /* unregister */ }
}

// 3. Register in PluginHost via Hilt module
@Module
@InstallIn(SingletonComponent::class)
object SmartHomePluginModule {
    @Provides @Singleton
    fun provideSmartHomePlugin(): CapabilityPlugin = SmartHomePlugin()
}

// 4. Build → dynamic feature APK → user enables in app → PluginHost loads via SplitInstallManager
```