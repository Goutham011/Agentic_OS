# Agentic_OS — Agent Specification

## Agent Identity
**Name:** Agentic_OS (user-renamable)
**Role:** Personal phone assistant / automation engine
**Personality:** Concise, proactive, privacy-first, technical but accessible
**Voice:** Neutral, configurable (system TTS voices)

## Capabilities Matrix

| Category | Capability | Tool | Local/Cloud | Confirmation |
|----------|------------|------|-------------|--------------|
| **Apps** | Open any installed app | `open_app` | Local | Never |
| **Apps** | List installed apps | `list_apps` | Local | Never |
| **Communication** | Send SMS | `send_sms` | Local | Configurable |
| **Communication** | Read recent SMS | `read_sms` | Local | Configurable |
| **Communication** | Make call | `make_call` | Local | Always |
| **Communication** | Read call log | `read_call_log` | Local | Configurable |
| **Communication** | Auto-reply to missed call | `automation:missed_call_reply` | Local | Once at setup |
| **Alarms** | Set alarm | `set_alarm` | Local | Never |
| **Alarms** | List/manage alarms | `list_alarms` | Local | Never |
| **Media** | Play YouTube / YouTube Music | `play_media` | Local (Intent) | Never |
| **Media** | Play local music | `play_local_media` | Local | Never |
| **Media** | Control playback | `media_control` | Local | Never |
| **Web** | Search Google | `web_search` | Cloud (scrape) | Never |
| **Web** | Summarize URL | `summarize_url` | Cloud (scrape) + Local LLM | Never |
| **System** | Toggle WiFi | `toggle_wifi` | Local | Configurable |
| **System** | Toggle Bluetooth | `toggle_bluetooth` | Local | Configurable |
| **System** | Toggle ringer mode | `set_ringer_mode` | Local | Configurable |
| **System** | Set timer | `set_timer` | Local | Never |
| **Location** | Get current location | `get_location` | Local | Configurable |
| **Location** | Geofence trigger | `automation:location` | Local | Once at setup |
| **Automation** | Backup photos | `automation:backup_photos` | Local (Drive/Dropbox API) | Once at setup |
| **Smart Home** | Trigger webhook | `automation:webhook` | Cloud | Once at setup |

## Tool Definitions (JSON Schema)

### open_app
```json
{
  "name": "open_app",
  "description": "Launch an installed application",
  "parameters": {
    "type": "object",
    "properties": {
      "package_name": {"type": "string", "description": "Exact package name (e.g., com.google.android.youtube)"},
      "activity_name": {"type": "string", "description": "Optional specific activity"}
    },
    "required": ["package_name"]
  }
}
```

### send_sms
```json
{
  "name": "send_sms",
  "description": "Send an SMS message",
  "parameters": {
    "type": "object",
    "properties": {
      "phone_number": {"type": "string", "pattern": "^[+]?[0-9\\s-]{10,}$"},
      "message": {"type": "string", "maxLength": 1600}
    },
    "required": ["phone_number", "message"]
  }
}
```

### make_call
```json
{
  "name": "make_call",
  "description": "Initiate a phone call",
  "parameters": {
    "type": "object",
    "properties": {
      "phone_number": {"type": "string", "pattern": "^[+]?[0-9\\s-]{10,}$"}
    },
    "required": ["phone_number"]
  }
}
```

### set_alarm
```json
{
  "name": "set_alarm",
  "description": "Create a one-time or repeating alarm",
  "parameters": {
    "type": "object",
    "properties": {
      "time": {"type": "string", "format": "HH:mm", "description": "24-hour format"},
      "label": {"type": "string"},
      "repeat": {"type": "string", "enum": ["none", "daily", "weekdays", "weekends", "custom"]},
      "days": {"type": "array", "items": {"type": "integer", "minimum": 1, "maximum": 7}}
    },
    "required": ["time"]
  }
}
```

### play_media
```json
{
  "name": "play_media",
  "description": "Play media via YouTube/YouTube Music or local player",
  "parameters": {
    "type": "object",
    "properties": {
      "query": {"type": "string", "description": "Search query (song, artist, playlist)"},
      "source": {"type": "string", "enum": ["youtube", "youtube_music", "local", "spotify"], "default": "youtube"}
    },
    "required": ["query"]
  }
}
```

### web_search
```json
{
  "name": "web_search",
  "description": "Search Google and return top results",
  "parameters": {
    "type": "object",
    "properties": {
      "query": {"type": "string"},
      "num_results": {"type": "integer", "default": 5, "maximum": 10}
    },
    "required": ["query"]
  }
}
```

### summarize_url
```json
{
  "name": "summarize_url",
  "description": "Fetch and summarize a web page",
  "parameters": {
    "type": "object",
    "properties": {
      "url": {"type": "string", "format": "uri"},
      "max_length": {"type": "integer", "default": 200}
    },
    "required": ["url"]
  }
}
```

### automation:missed_call_reply
```json
{
  "name": "automation:missed_call_reply",
  "description": "Configure auto-reply for missed calls",
  "parameters": {
    "type": "object",
    "properties": {
      "enabled": {"type": "boolean"},
      "message": {"type": "string", "default": "I missed your call. I'll call you back soon."},
      "log_to_sheet": {"type": "boolean", "default": false},
      "sheet_url": {"type": "string", "format": "uri"}
    },
    "required": ["enabled"]
  }
}
```

### automation:location
```json
{
  "name": "automation:location",
  "description": "Configure location-based triggers",
  "parameters": {
    "type": "object",
    "properties": {
      "triggers": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "name": {"type": "string"},
            "latitude": {"type": "number"},
            "longitude": {"type": "number"},
            "radius_meters": {"type": "integer", "default": 100},
            "on_enter": {"$ref": "#/definitions/ActionList"},
            "on_exit": {"$ref": "#/definitions/ActionList"}
          },
          "required": ["name", "latitude", "longitude"]
        }
      }
    },
    "required": ["triggers"]
  },
  "definitions": {
    "ActionList": {
      "type": "array",
      "items": {"$ref": "#/definitions/Action"}
    },
    "Action": {
      "type": "object",
      "properties": {
        "tool": {"type": "string"},
        "params": {"type": "object"}
      },
      "required": ["tool"]
    }
  }
}
```

## LLM Prompt Templates

### System Prompt (Injected Every Turn)
```
You are Agentic_OS, a personal phone assistant running entirely on-device.
Current time: {{current_time}}
Battery: {{battery_level}}%
Location: {{location_status}}
Active automation rules: {{automation_count}}

AVAILABLE TOOLS:
{{tool_schemas}}

RULES:
1. Respond ONLY with valid JSON: {"tool": "...", "params": {...}, "speak": "..."}
2. If user asks for internet-dependent info (news, weather, search), use web_search or summarize_url.
3. If user asks for phone action, use the appropriate tool.
4. Never hallucinate tool parameters. If unsure, ask for clarification in "speak".
5. Keep "speak" concise (< 200 chars). Use TTS-friendly phrasing.
6. For multi-step tasks, chain tools: respond with first tool, system returns result, you respond with next.
```

### User Input Template
```json
{
  "user_text": "{{stt_result}}",
  "context": {
    "screen_content": "{{accessibility_text}}",
    "active_notifications": "{{notification_summary}}",
    "recent_commands": "{{last_3_commands}}"
  }
}
```

### Tool Result Injection
```json
{
  "tool_result": {
    "tool": "{{tool_name}}",
    "success": true,
    "data": {{result_data}},
    "error": null
  }
}
```

## Agent Loop (Pseudocode)

```kotlin
while (serviceRunning) {
    // 1. Listen for voice (hotword + command)
    val userText = voiceManager.listenForCommand()
    
    // 2. Build prompt with context
    val prompt = buildPrompt(userText, getScreenContext(), getNotifications())
    
    // 3. Local LLM inference
    val response = llmManager.infer(prompt)
    
    // 4. Parse tool call
    val toolCall = parseToolCall(response)
    
    // 5. Execute if valid
    if (toolCall != null) {
        val result = actionExecutor.execute(toolCall)
        
        // 6. If result needs follow-up, re-inject and loop
        if (result.needsFollowUp) {
            val followUpPrompt = buildFollowUpPrompt(toolCall, result)
            val followUpResponse = llmManager.infer(followUpPrompt)
            // ... handle follow-up
        }
        
        // 7. Speak response
        voiceManager.speak(response.speak ?: result.summary)
    }
    
    // 8. Check automation triggers (parallel via WorkManager)
}
```

## Automation Rule Schema (Room Entity)

```kotlin
@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val trigger: TriggerSpec,           // @Embedded
    val actions: List<ActionSpec>,      // @Embedded, typeConverter
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TriggerSpec(
    val type: TriggerType,              // TIME, LOCATION_ENTER, LOCATION_EXIT, MISSED_CALL, NOTIFICATION, BATTERY, WIFI
    val cronExpression: String?,        // for TIME
    val latitude: Double?,              // for LOCATION
    val longitude: Double?,
    val radiusMeters: Int?,
    val notificationPackage: String?,   // for NOTIFICATION
    val notificationTextContains: String?,
    val batteryThreshold: Int?,         // for BATTERY
    val wifiSsid: String?               // for WIFI
)

data class ActionSpec(
    val tool: String,                   // tool name from registry
    val paramsJson: String              // JSON params for the tool
)

enum class TriggerType { TIME, LOCATION_ENTER, LOCATION_EXIT, MISSED_CALL, NOTIFICATION, BATTERY_LOW, WIFI_CONNECTED, WIFI_DISCONNECTED }
```

## Built-in Automation Templates (Shipped with App)

| Template | Trigger | Actions |
|----------|---------|---------|
| "Missed Call → SMS + Log" | Missed call | `send_sms` to caller + `webhook` to Google Sheets |
| "Home → Silent + WiFi On" | Location enter (home) | `set_ringer_mode(silent)`, `toggle_wifi(on)` |
| "Work → WiFi Off" | Location exit (work) | `toggle_wifi(off)` |
| "Photo Backup" | New photo taken (MediaStore observer) | `backup_photos` to Google Drive |
| "Low Battery → Power Save" | Battery < 20% | `set_ringer_mode(vibrate)`, `toggle_wifi(off)`, `toggle_bluetooth(off)` |
| "Night Mode" | Time 22:00 | `set_ringer_mode(silent)`, `toggle_bluetooth(off)` |
| "Morning Briefing" | Time 07:00 | `web_search("news")` → `summarize` → `speak` |

## Memory Model

### Short-term (Conversation)
- Last 10 turns in context window
- Auto-summarized when > 8 turns

### Long-term (Vector Store)
- User preferences ("I prefer YouTube Music", "Work address is X")
- Frequent contacts
- App usage patterns
- Stored as embeddings (ONNX / MediaPipe) in Room + FAISS-like index
- Query: `SELECT * FROM memory WHERE embedding SIMILAR TO query_embedding LIMIT 5`

### Privacy Controls
- User can view / delete any memory entry
- "Forget everything" button in settings
- No cloud sync unless explicitly enabled

## Voice Interaction Design

### Multi-Tier Voice Recognition Pipeline (Near-Perfect Accuracy)

**Architecture:**
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

### 1. Audio Preprocessing (Before STT — Critical for Accuracy)
Raw mic audio passes through a processing chain before recognition:

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Noise Suppression** | WebRTC NS | Removes AC, traffic, fan, keyboard noise |
| **Acoustic Echo Cancellation** | WebRTC AEC | Removes TTS/music playback from mic |
| **Automatic Gain Control** | WebRTC AGC | Normalizes quiet/loud speakers |
| **Voice Activity Detection** | MediaPipe VAD | Only sends speech segments to STT |
| **Beamforming** | Multi-mic API | Directional pickup (if device supports) |

**Implementation:** `AudioPreprocessor` class wraps WebRTC audio effects via JNI. Processes 10ms frames at 16kHz.

### 2. Wake Word Detection: Porcupine (Picovoice)
| Feature | Porcupine | Google Hotword | Custom TFLite |
|---------|-----------|----------------|---------------|
| Accuracy | 99%+ | 95% | 80-90% |
| CPU | < 10% | Variable | High |
| Offline | ✅ | ❌ | ✅ |
| Custom words | 3 free | ❌ | ✅ |
| Latency | 50ms | 200ms+ | 100ms+ |

**Default:** "Hey Agent" (configurable). Free tier: 3 custom words. Runs in dedicated audio thread.

**Fallback:** Long-press power button / floating button / widget.

### 3. Multi-Tier STT Engine (Never Fail)

**Tier 1: Google Cloud Speech-to-Text (Best Quality)**
- 95-98% accuracy on clean audio
- Streaming (real-time partial results)
- **Phrase hints** — bias decoder toward tool vocabulary (boost=20)
- **Word hints** — boost contacts, aliases, custom vocabulary
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
    if (networkAvailable && userPrefersCloud) {
        val cloud = googleCloudSTT.recognize(audio, context)
        if (cloud.confidence > 0.85) return cloud
    }
    val system = systemRecognizer.recognize(audio, context)
    if (system.confidence > 0.70) return system
    return whisperCpp.recognize(audio, context)
}
```

### 4. Vocabulary Biasing (Context-Aware Decoding)
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

### 5. Personalization (Voice Profile)
| Technique | How It Works | Benefit |
|-----------|-------------|---------|
| **Voice Enrollment** | User reads 20-30 phrases onboarding → speaker embedding | 5-10% accuracy boost |
| **Online Adaptation** | Store corrections → fine-tune acoustic model | Continuous improvement |
| **Contact Names** | Import contacts → add to phrase hints | "Call Mom" recognized |
| **Custom Vocabulary** | User-defined aliases ("Acme Corp" → "work") | Domain-specific accuracy |

**Onboarding Flow:** Read 20 phrases → build 192-d speaker embedding → store encrypted in Room.

### 6. Confidence Scoring & Retry Logic
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

### 7. LLM Fuzzy Correction (Post-STT)
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

### STT Modes
1. **Command mode** (after hotword): Streaming, endpoint detection, ~2s max
2. **Dictation mode** (explicit "type this"): Continuous until "stop typing"

### TTS Behavior
- Interruptible (user can say "stop" mid-response)
- Queue multiple utterances
- Respect system volume / Do Not Disturb
- System voices + optional cloud voices (user choice)

## Error Handling & Fallbacks

| Scenario | Behavior |
|----------|----------|
| LLM fails to parse | "I didn't understand. Try rephrasing." |
| Tool permission denied | "I need permission to [action]. Grant in settings?" → deep link |
| Tool execution fails | Retry once, then "Failed to [action]. Error: {{message}}" |
| Accessibility service dead | Persistent notification "Agent needs Accessibility permission" → deep link |
| No internet for web search | "I can't search right now. Try again later." |
| Battery saver active | Reduce polling frequency, defer non-critical automations |
| STT low confidence | "Could you repeat that?" → retry with different STT tier |
| Wake word false positive | VAD confirms speech before STT; ignore if < 0.5s |

## Onboarding Flow

1. **Welcome** → Explain what the app does (3 screens)
2. **Permissions** → Single screen with grouped toggles:
   - Communication (SMS, Calls, Contacts)
   - Location (Triggers, Geofencing)
   - Media & Files (Photos, Music)
   - System (Alarms, WiFi, Bluetooth)
   - Microphone (Voice)
   Each shows rationale; "Grant All" button requests sequentially
3. **Special Permissions** → Guided flow for each:
   - Accessibility: "Enable to read screen & tap buttons" → Settings intent
   - Notifications: "Enable to auto-reply to calls & read alerts" → Settings intent
   - Usage Stats: "Enable to learn your patterns" → Settings intent
   - Overlay: "Enable floating mic button" → Settings intent
4. **Hotword Setup** → Record "Hey Agent" 3× (optional)
5. **Automation Tour** → Show 3 templates, one-tap enable
6. **Done** → Launch main UI

## Developer Notes for Prototype

1. **LLM Integration Point**: `LLMManager` interface has two implementations:
   - `LocalLLMManager` (MediaPipe) — production
   - `RuleBasedLLMManager` — prototype fallback (keyword matching → tool calls)
   Swap via Hilt module.

2. **Web Search**: Use `Jsoup` to scrape Google HTML (no API key) for MVP.
   Rate limit: 1 req/2s. Parse `<div class="g">` results.
   For production: Google Custom Search JSON API.

3. **Testing Without Device**: 
   - Accessibility service: mock `AccessibilityEvent` in unit tests
   - Notifications: `NotificationListenerService` test harness
   - Location: mock `FusedLocationProviderClient`

4. **Demo Script for Recruiters**:
   - "Hey Agent, open YouTube" → app launches
   - "Hey Agent, play LoFi Girl on YouTube" → video plays
   - "Hey Agent, set alarm for 7 AM" → alarm created
   - "Hey Agent, text Mom I'm on my way" → SMS sent (with confirmation)
   - "Hey Agent, what's the news?" → web search → summary spoken
   - Show automation: miss a call → auto-SMS appears
   - Show location: walk to "home" geofence → phone silences

5. **APK Size Target**: < 50 MB (model ~20 MB, rest ~15 MB, assets ~5 MB)

## Open Questions for You

1. **LLM Model**: Do you have a preference? (Gemini Nano requires Play Services + supported device; Phi-3/SmolLM work everywhere via MediaPipe)
2. **Smart Home**: Which ecosystem? (Home Assistant, IFTTT, Tuya, custom MQTT?)
3. **Photo Backup**: Google Drive API (OAuth) or Dropbox? Both need cloud credentials.
4. **Spreadsheet Logging**: Google Sheets API (OAuth) or local CSV export?
5. **Wake Word**: Use Porcupine (free tier: 3 custom words) or train custom TFLite?
6. **UI Framework**: Jetpack Compose (modern) or XML (familiar)? I used Compose in architecture but can adapt.
7. **Min SDK**: Android 8 (API 26), 10 (29), or 12 (31)? Lower = more devices, higher = better APIs.
8. **Distribution**: Play Store (strict on accessibility) or GitHub/sideload (freer)?