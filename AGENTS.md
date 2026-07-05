# DeepSeno Android — Project Context

## Project Family
- This repository (`deepseno-android`) is the DeepSeno Android project.
- Sibling repositories under `/Users/daiqiang/Project`:
  - `deepseno-ios` — DeepSeno iOS project.
  - `deepseno` — DeepSeno desktop/PC client project.
  - `voicebrain-web` — DeepSeno backend/web project; it may not exist in every local checkout.
- `AGENTS.md` is the canonical assistant guidance file for this repository. Legacy `CLAUDE.md` content, if it appears, should be merged here and removed.

## Project
DeepSeno Android companion app for the voice-powered "second brain" desktop app. Captures audio, photos, video, text memos and syncs to desktop via local network.

## Tech Stack
- Jetpack Compose + Material 3
- Kotlin + Hilt (DI)
- Room (local database)
- MediaRecorder (audio recording)
- SpeechRecognizer (on-device live transcription)
- OkHttp + Retrofit (networking)
- CameraX + ML Kit (photo/QR)

## Architecture
```
ui/screen/
├── capture/ — CaptureScreen, RecordButton, TextMemoDialog, MediaPickerSheet
├── sources/ — SourcesScreen, SourceCard, SourceDetailScreen
├── chat/ — ChatScreen, MessageBubble, SessionListSheet
├── briefing/ — BriefingScreen, TodoListSection, ExtractedItemsSection
├── settings/ — SettingsScreen, PairingScreen, QueueManagementScreen
└── common/ — ConnectionBadge, StatusBadge, EmptyStateView

ui/viewmodel/
├── AppState — Connection, WebSocket, notifications
├── CaptureViewModel — Recording, LiveTranscriber, toast
├── ChatViewModel, SourcesViewModel, BriefingViewModel, SettingsViewModel

service/
├── AudioRecorder — MediaRecorder (M4A 16kHz mono)
├── LiveTranscriber — SpeechRecognizer (on-device, zh/en)
├── WebSocketManager — Real-time server events
├── ApiClient — OkHttp REST client with Bearer auth
├── CaptureQueue — Room-backed upload queue with retry
├── SseClient — Streaming chat responses
├── AudioStreamer — WebSocket audio streaming
├── TranscriptionNotificationManager — Local notifications
└── RecordingForegroundService — Recording indicator

ui/theme/
├── Color.kt — BgPrimary, AccentGreen, TextTertiary, etc.
├── DeepSenoTheme.kt — Material3 dark theme
└── Type.kt — Monospace typography

i18n/
└── Strings.kt — English + Chinese, CompositionLocal
```

## Build & Run

### Prerequisites
- JDK 17 (e.g. Homebrew `openjdk@17`)
- Android SDK (API 26+)

### Environment Setup
```bash
# Copy and fill in your configuration
cp .env.example .env
```

See `.env.example` for required variables (signing keys, relay server URL).

### Debug Build
```bash
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

### Release Build
```bash
./gradlew assembleRelease

# Output APKs (5 flavors):
# app/build/outputs/apk/official/release/app-official-release.apk
# app/build/outputs/apk/xiaomi/release/app-xiaomi-release.apk
# app/build/outputs/apk/huawei/release/app-huawei-release.apk
# app/build/outputs/apk/oppo/release/app-oppo-release.apk
# app/build/outputs/apk/vivo/release/app-vivo-release.apk
```

## Signing
Signing configuration is read from environment variables (set via `.env` file or system environment). See `.env.example` for details.

## Multi-Channel Flavors
5 flavors for different app stores: `official`, `xiaomi`, `huawei`, `oppo`, `vivo`

## Database
- Room database `deepseno.db`
- `fallbackToDestructiveMigration()` — schema changes reset DB
- Tables: `capture_items`, `cached_recordings`, `cached_briefings`

## Important Notes
- Min SDK 26, Target SDK 35
- LiveTranscriber uses `Locale.getDefault().language` for Chinese detection
- Upload sends MIME type based on file extension (mimeType helper in ApiClient)
- Filenames URL-encoded in X-Filename header
- Bookmarks sent via X-Bookmarks header as JSON array
- SpeechRecognizer.createOnDeviceSpeechRecognizer requires API 31+, falls back to cloud
