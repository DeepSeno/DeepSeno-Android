# DeepSeno Android

> Mobile companion for the DeepSeno voice-powered "second brain" desktop app.

Capture audio, photos, video, and text memos on your phone. Sync seamlessly to your desktop over your local network for AI-powered processing. No cloud required — everything stays on your devices.

## Features

- 🎙️ **Voice Recording** — One-tap recording with on-device live transcription (English & Chinese)
- 📸 **Photo & Video Capture** — Snap photos, batch multi-photo, or record video clips
- 📝 **Text Memos** — Quick text notes that sync with your knowledge base
- 🤖 **AI Assistant** — Chat with your AI to query your entire knowledge base
- 📊 **Daily & Weekly Briefings** — AI-generated summaries of your recordings
- 🔍 **Full-Text Search** — Search across all transcripts and notes
- 🔒 **Local-First** — All data stays on your local network, no cloud dependency
- 🔐 **End-to-End Encryption** — Relay mode with ECDH + AES-256-GCM for remote access

## Tech Stack

- **UI**: Jetpack Compose + Material 3
- **Language**: Kotlin
- **DI**: Hilt
- **Database**: Room
- **Networking**: OkHttp + Retrofit
- **Audio**: MediaRecorder + SpeechRecognizer (on-device)
- **Camera**: CameraX + ML Kit Barcode Scanning
- **P2P**: WebRTC DataChannel (future)

## Getting Started

### Prerequisites

- JDK 17
- Android SDK (API 26+)
- Android device or emulator running Android 8.0+

### Environment Setup

```bash
# Copy the example environment file
cp .env.example .env

# Edit .env with your configuration
# - Signing keystore (base64 encoded)
# - Relay server URL (optional)
```

See [`.env.example`](.env.example) for all configuration options.

### Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires .env configuration)
./gradlew assembleRelease
```

### Install

```bash
# Install debug build on connected device
./gradlew installDebug
```

## Architecture

```
app/src/main/java/com/deepseno/app/
├── di/                     # Hilt dependency injection modules
├── data/
│   ├── local/              # Room database, DAOs, entities
│   └── remote/             # Retrofit API, data models
├── service/                # Core services
│   ├── AudioRecorder       # MediaRecorder (M4A 16kHz mono)
│   ├── LiveTranscriber     # SpeechRecognizer (on-device)
│   ├── ApiClient           # OkHttp REST client with Bearer auth
│   ├── WebSocketManager    # Real-time server events
│   ├── AudioStreamer       # WebSocket PCM audio streaming
│   ├── CaptureQueue        # Room-backed upload queue with retry
│   ├── SseClient           # Streaming chat responses
│   ├── ConnectionManager   # LAN / Relay / P2P connection orchestration
│   ├── CacheManager        # Offline cache sync
│   └── relay/              # ECDH + AES-256-GCM encrypted relay tunnel
├── ui/
│   ├── screen/             # Compose screens (capture, sources, chat, briefing, settings)
│   ├── viewmodel/          # ViewModels
│   ├── theme/              # Material3 dark theme
│   ├── navigation/         # Navigation graph
│   └── util/               # Utility composables
└── i18n/                   # English + Chinese strings
```

## Configuration

The app uses a `.env` file (not committed) for sensitive configuration:

| Variable | Description |
|----------|-------------|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded release keystore (.jks) |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias name |
| `SIGNING_KEY_PASSWORD` | Key password |
| `RELAY_SERVER_BASE_URL` | Relay server URL for remote connections |

All signing configuration can also be set via system environment variables.

## Build Flavors

5 product flavors for different distribution channels:

| Flavor | Channel |
|--------|---------|
| `official` | Official / Google Play |
| `xiaomi` | Xiaomi App Store |
| `huawei` | Huawei AppGallery |
| `oppo` | OPPO Software Store |
| `vivo` | vivo App Store |

## License

[License to be determined]
