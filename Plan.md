# Agentic_OS — Project Plan

## Vision
A non-rooted Android app that turns any phone into a JARVIS-style autonomous assistant. The app runs as a foreground service, reads the screen and notifications via `AccessibilityService`, executes user-approved automations, and uses a small on-device LLM for local tasks. Internet-dependent tasks are delegated to Google Search via text-to-speech fallback. No cloud LLM required for core functionality.

**Core Differentiator:** Unlike Google Assistant/Bixby, Agentic_OS reaches *inside* third-party apps via AccessibilityService + NotificationListener — enabling unified communication, local media intelligence, and cross-app automations that no stock assistant can do.

## Scope

### Phase 1: Daily Driver (Weeks 1–8)
**Goal:** Replace 5+ single-purpose apps (media player, SMS manager, automation tool, quick-reply, file cleaner) with one reliable assistant.

| Week | Deliverable |
|------|-------------|
| 1–2  | **Media Intelligence Plugin** — MediaStore indexer (Worker), Room library (`MediaFile` entity), local player with queue, folder browser, smart playlists (recency, folder, play count). `MediaController` integration for external players (MPV, VLC, Musicolet). |
| 3–4  | **Communication Hub Plugin** — Unified inbox (SMS + WhatsApp + Telegram + Signal + Email notifications), quick-reply via AccessibilityService, contact timeline (calls + messages + files per person), spam/OTP auto-filter, scheduled messages. |
| 5–6  | **Visual Automation Builder** — Compose Canvas node editor (Trigger → Condition → Action → Delay → Branch), template gallery (50+ community rules), natural language → rule (LLM), debugger (simulate trigger, step log). |
| 7–8  | **Proactive Intelligence v1** — Meeting prep (calendar + contacts + recent comms), Focus sessions (DND + buffer + summary), Travel mode (flight detection → offline maps + roaming off + boarding pass), Expense capture (receipt OCR → Sheets/Notion/CSV). |

### Phase 2: Personal OS (Weeks 9–16)
**Goal:** Become the user's external brain — remembering, connecting, and acting on their digital life.

| Week | Deliverable |
|------|-------------|
| 9–10 | **Knowledge Graph** — Entity/Relation/Observation Room schema, embedding index (ONNX), LLM tool `query_knowledge_graph`, semantic search ("photos from Tokyo trip", "PDF Sarah sent last week"). |
| 11–12 | **Plugin System** — Dynamic feature modules (Smart Home, Health, Finance, Dev Tools). CapabilityPlugin interface. Base APK < 30 MB. Modules downloaded on-demand. |
| 13–14 | **Device Maintenance Suite** — Storage janitor (duplicates, old caches, WhatsApp sent media), Battery health (80% limit via Settings injection), App hibernation (UsageStats + Accessibility force-stop), Permission auditor (monthly report + one-tap revoke). |
| 15–16 | **Wear OS + Desktop Companion** — Phone = brain, Watch/PC = I/O nodes. Voice from watch, notifications to desktop, clipboard sync, remote media control. |

### Phase 3: Platform (Post-MVP)
- Community automation marketplace (`.agentic` JSON import/export, ratings, curated collections)
- Local LLM fine-tuning on user data (federated, on-device, opt-in)
- Enterprise: MDM integration, team automation sharing, audit logs

### Out of Scope (All Phases)
- Root-level system replacement
- Full call audio interception (impossible without root)
- Cloud LLM dependency for core features
- Cross-device orchestration beyond Wear/desktop companion

## Success Metrics

### Phase 1 (Daily Driver)
| Metric | Target |
|--------|--------|
| Day-7 retention | > 40% |
| Automations created per user | > 3 |
| Media plays per session | > 2 |
| Quick-replies sent per day | > 5 |
| Crash-free sessions | > 99.5% |
| Battery impact | < 10%/day (adaptive polling) |

### Phase 2 (Personal OS)
| Metric | Target |
|--------|--------|
| Knowledge graph queries per day | > 10 |
| Plugin modules enabled per user | > 2 |
| Proactive suggestions accepted | > 30% |
| Wear/desktop companion daily active | > 25% |

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Accessibility service killed by Doze | Agent stops working | Foreground service + adaptive polling + WorkManager heartbeat + `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` |
| Play Store rejection (accessibility abuse) | Cannot distribute via Play | Frame as "personal automation assistant for accessibility & productivity"; distribute via GitHub/F-Droid/website for v1 |
| Battery drain | User uninstalls | Aggressive power management: charging-only workers, adaptive wake intervals, sensor batching |
| Screen reading inaccuracy | Commands fail | ML Kit OCR fallback, confidence thresholds before acting, user correction loop |
| Action reliability (UI injection) | Taps miss, wrong field filled | Dry-run preview, rollback where possible (WiFi/BT), audit log, confirmation for destructive actions |
| Privacy/legal (interception) | Compliance risk | Explicit consent per app, data processing disclosure, no call audio recording, GDPR/CCPA export/delete |
| Fragmentation (OEM restrictions) | Features break on Xiaomi/Samsung/etc. | Per-OEM workarounds module, community-reported fixes, graceful degradation |
| MediaStore scanning performance | Slow startup, battery hit | Incremental indexing, charging+WiFi only, Room paging, background priority |

## Team & Timeline Assumptions
- **1 senior Android dev** (you) + **1 part-time designer** (for Automation Builder UI)
- 2-week sprints, 6 productive hours/day
- Physical test devices: Pixel (stock), Samsung (One UI), Xiaomi (HyperOS), OnePlus (OxygenOS)
- CI: GitHub Actions → internal distribution (Firebase App Distribution)

## Open Decisions (Need Your Input)
1. **Primary persona** — Power user / Professional / Privacy advocate / Accessibility / Creative? (Changes priority order)
2. **LLM model** — Gemini Nano (Play Services, limited devices) vs Phi-3/SmolLM (MediaPipe, universal) vs Gemma 2B (MediaPipe, best quality)?
3. **Smart home targets** — Home Assistant, IFTTT, Tuya, MQTT, Matter? (Determines webhook templates)
4. **Photo backup** — Google Drive API (OAuth) vs Dropbox vs Syncthing/rsync to NAS?
5. **Wake word** — Porcupine (free tier: 3 words, needs access key) vs custom TFLite (train yourself, no key)?
6. **Distribution** — Play Store (strict) vs GitHub/F-Droid/website (free) vs both (separate flavors)?
7. **Min SDK** — API 29 (Android 10) vs API 31 (Android 12, better APIs)? API 29 = 95% devices, API 31 = 80% but easier geofencing/background.