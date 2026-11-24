# Nexa AI — Native Android Client for OpenCode

**Nexa AI** (`com.nexa.ai`) is a native Kotlin + Jetpack Compose Android client for a self-hosted **OpenCode** AI coding agent exposed over HTTPS (default gateway: `https://opencode-production-ef5d07d3.up.railway.app/v1`).

Nexa AI never calls a third-party LLM API directly, never fabricates responses, never simulates token streaming, and never embeds secrets in source code or build artifacts. Every session, message, agent, provider, and tool execution comes directly from your OpenCode gateway.

---

## Important Verification & Build Status Note

> **Honest status from the Arena build sandbox:**
> - This sandbox has **no JDK (`javac`)**, **no Android SDK**, and **outbound network access is restricted** (`services.gradle.org`, `dl.google.com`, `maven.google.com`, `repo.maven.apache.org`, and the Railway host `*.up.railway.app` are unreachable from inside the sandbox).
> - Consequently, **no Gradle build (`./gradlew assembleDebug`), JVM unit test run (`./gradlew testDebugUnitTest`), live Railway `GET /health` check, or compiled `app-debug.apk` binary has been executed inside this sandbox**.
> - To compensate as far as possible offline, every one of the **80 Kotlin files**, **157 string resources**, XML layouts/drawables, `AndroidManifest.xml`, and `gradle/libs.versions.toml` entries was audited line-by-line and checked with `python3 tools/check_project.py` (brace balance, package/path alignment, duplicate declarations, internal import & cross-package symbol resolution, Compose `by` delegation imports, Android resource/manifest validation, Gradle catalog keys, and secret scanning).
> - Build and test instructions for Android Studio, local CLI, and GitHub Actions (`.github/workflows/android.yml`, which builds and uploads `app-debug.apk` automatically on push) are provided below.

---

## Implemented Capabilities

### Authentication & Security
- **Real gateway login**: `POST /auth/login` with `{ "username", "password" }` → `{ "access_token", "token_type", "expires_in" }` (accepts both `snake_case` and `camelCase` fields, and normalizes 10-digit Unix-second or 13-digit millisecond `expires_at` values; defaults to 7-day expiry when omitted).
- **Android Keystore AES-256/GCM encryption**: Access tokens are encrypted with a hardware-backed/non-exportable `AndroidKeyStore` key (`nexa_ai_token_key`) using a fresh random IV per write. DataStore (`nexa_secure_token`) stores only `ciphertext + IV + expiresAt + username`.
- **Zero password persistence**: Passwords are never written to Room, SharedPreferences, DataStore, logs, or crash messages.
- **Centralized `AuthInterceptor` + `SessionInvalidator`**:
  - Attaches `Authorization: Bearer <token>` to all authenticated endpoints (never to `/auth/login`).
  - Suppresses expired tokens before they hit the network.
  - Any HTTP `401` on a protected endpoint emits a throttled invalidation event that clears the stored token and returns the UI cleanly to `LoginScreen`.
- **Transport security**: `android:usesCleartextTraffic="false"` in `AndroidManifest.xml`, `android:allowBackup="false"`, strict `BaseUrlNormalizer` validation requiring `https://` for all remote hosts, and OkHttp `HttpLoggingInterceptor` capped at `BASIC` in debug (`NONE` in release) with `Authorization` explicitly redacted.

### Conversations, Polling & Tool Calls
- **End-to-end session & message lifecycle**:
  1. Verifies authentication; restores the last active session on launch.
  2. Creates a conversation via `POST /sessions` (`{"title": "..."}`) when starting a new chat and uses the returned `sessionId` as the permanent conversation ID.
  3. Writes an optimistic local user bubble to Room immediately, then sends `POST /sessions/{id}/messages` (`{"message", "agent", "model"?: {"providerID", "id"}}`).
  4. Polls `GET /sessions/active` and `GET /sessions/{id}/messages?limit=50&order=asc` every **1.5 s** via `SessionPoller`.
  5. Persists each message snapshot into Room so assistant text and tool-call cards update live as the backend reports progress.
  6. Stops polling once the session is no longer active **and** the new assistant reply has arrived (with 3 idle grace polls for late-settling writes, exponential backoff up to 15 s on transient 5xx/429/network errors, bailout after 6 consecutive failures, and a 10-minute max wait).
  7. Supports mid-run cancellation via `POST /sessions/{id}/interrupt` when tapping **Stop**.
- **Accurate tool-call cards (`ToolCallCard`)**: Displays queued, running, completed, and failed states strictly from backend `parts` (`state.status`), with expandable/scrollable input and output panels and timestamps.
- **Markdown & code rendering (`MarkdownParser` + `SyntaxHighlighter`)**: Dependency-free renderer for headings, bold/italic/strikethrough, links, bulleted & numbered lists, block quotes, GFM tables, and fenced code blocks with syntax highlighting (Kotlin, Java, Python, JS/TS, Go, Rust, Swift, C/C++, SQL, Bash, JSON, YAML, XML/HTML, CSS), collapsible long blocks, and one-tap copy buttons.

### Screens & UX
- **Login (`presentation/auth`)**: Editable prefilled gateway URL (`https://opencode-production-ef5d07d3.up.railway.app/v1`), username, password visibility toggle, input validation, loading state, and clear HTTP error feedback.
- **Chat (`presentation/chat`)**: Multiline pill composer, inline `@agent` chip + `@`-mention quick agent switcher popup (driven by `GET /agents`), duplicate-send single-flight lock, **Stop** button while running, elapsed working banner with active tool count, auto-scroll that yields on manual scroll-up + scroll-to-bottom button, pull/resume sync, and offline banner.
- **History Drawer (`presentation/history`)**: Modal drawer on phones and permanent 320 dp rail on wide screens (`>= 840 dp`), search filter, session creation, session switching, and server+local deletion (`DELETE /sessions/{id}`) with confirmation dialog.
- **Agent Selection (`presentation/agents`)**: Lists agents from `GET /agents` with mode, model, built-in badge, and description; defaults to `build`.
- **Settings (`presentation/settings`)**: Signed-in username, editable API server URL, live **Test connection** (`GET /health` with latency in ms), agent shortcut, optional model override populated from `GET /providers` (or automatic server default), System / Light / Dark theme + Material You dynamic color toggle (Android 12+), refresh history, clear local cache, sign out, and app version.
- **About (`presentation/about`)**: Live server reachability check, technology stack, and an explicit list of supported vs. unsupported features.

### Explicitly Not Included in v1.0
In accordance with the OpenCode gateway's current REST surface, Nexa AI **does not** implement or claim support for:
- Image uploads
- File attachments
- Voice input / voice mode
- WebSocket / SSE token-by-token streaming (uses truthful polling instead)

---

## Architecture & Project Structure

Nexa AI follows **MVVM + Clean Architecture** with **Hilt**, **Coroutines/Flow**, **Retrofit + kotlinx.serialization**, **Room**, and **Jetpack Compose (Material 3)**:

```text
app/src/main/java/com/nexa/ai/
├── core/
│   ├── AppConstants.kt             # Non-secret defaults & timeouts
│   ├── clock/                      # Injectable Clock for deterministic expiry & polling tests
│   ├── crypto/                     # TokenCipher & KeystoreTokenCipher (AES-256/GCM)
│   ├── dispatcher/                 # Injectable CoroutineDispatcher provider
│   ├── error/                      # AppError taxonomy (401/403/429/5xx/offline/timeout/validation)
│   ├── markdown/                   # MarkdownParser & SyntaxHighlighter
│   ├── network/                    # BaseUrlNormalizer, BaseUrlHolder, BaseUrlInterceptor,
│   │                               # AuthInterceptor, SessionInvalidator, ConnectivityObserver
│   └── result/                     # Explicit AppResult<T> wrapper
├── data/
│   ├── local/
│   │   ├── SecureTokenStore.kt     # Encrypted token persistence in DataStore
│   │   ├── db/                     # Room NexaDatabase v1 (sessions, messages, tool_calls, drafts)
│   │   ├── mapper/                 # Room entity <-> domain mappers
│   │   └── prefs/                  # Non-secret preferences & offline catalog cache
│   ├── remote/
│   │   ├── api/NexaApi.kt          # All 12 OpenCode gateway REST endpoints
│   │   ├── dto/ApiDtos.kt          # Login, session creation & send-message DTOs
│   │   └── parser/RemoteParser.kt  # Tolerant JSON envelope & OpenCode parts decoder
│   └── repository/                 # Auth, Session, SessionPoller, Agent, Settings, Health impls
├── di/                             # Hilt modules (AppModule, NetworkModule, DatabaseModule, RepositoryModule)
├── domain/
│   ├── model/                      # ChatSession, ChatMessage, ToolCall, AgentInfo, ProviderInfo, AuthState
│   ├── polling/                    # PollingConfig, BackoffPolicy, PollResult
│   ├── repository/                 # Clean domain repository contracts
│   └── usecase/                    # Auth & Chat use cases
└── presentation/
    ├── auth/                       # LoginScreen & LoginViewModel
    ├── chat/                       # ChatScreen, ChatViewModel, Composer, MessageBubble,
    │                               # ToolCallCard, MarkdownText
    ├── history/                    # ConversationHistoryDrawer
    ├── agents/                     # AgentSelectionScreen & AgentSelectionViewModel
    ├── settings/                   # SettingsScreen & SettingsViewModel
    ├── about/                      # AboutScreen & AboutViewModel
    ├── main/                       # MainScreen (adaptive drawer/rail) & MainViewModel
    ├── navigation/                 # Compose Navigation routes
    ├── components/                 # Shared Material 3 components, logo & formatters
    └── theme/                      # Indigo/Violet/Cyan Light & Dark palettes + Material You
```

---

## REST Endpoints Used

All requests are rooted at the configurable gateway base URL (default `https://opencode-production-ef5d07d3.up.railway.app/v1`):

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/health` | Server reachability & latency check |
| `POST` | `/auth/login` | Exchange `{username, password}` for `{access_token, token_type, expires_in}` |
| `GET` | `/sessions` | List conversations |
| `POST` | `/sessions` | Create conversation (`{"title": "..."}`) |
| `GET` | `/sessions/active` | Check currently running sessions during polling |
| `GET` | `/sessions/{id}` | Fetch single conversation metadata |
| `DELETE` | `/sessions/{id}` | Delete conversation on server and in Room |
| `GET` | `/sessions/{id}/messages?limit=50&order=asc` | Load ordered messages & OpenCode `parts` |
| `POST` | `/sessions/{id}/messages` | Send user prompt with selected `agent` and optional `model` |
| `POST` | `/sessions/{id}/interrupt` | Stop an in-progress agent run |
| `GET` | `/agents` | Discover available agents (defaults to `build`) |
| `GET` | `/providers` | Discover optional model providers & models |

---

## Building, Testing & Installing Locally

### Prerequisites
- **Android Studio Ladybug (2024.2.1+)** or newer
- **JDK 17** (`temurin-17` or Android Studio's bundled JBR 17)
- **Android SDK Platform 35** (`compileSdk = 35`, `minSdk = 26` — Android 8.0+)
- Internet access on first Gradle sync to download Gradle `8.9` and Maven/Google dependencies

### 1. Run Static Checks (no SDK required)
```bash
python3 tools/check_project.py
```

### 2. Run JVM Unit Tests
Covers URL normalization/HTTPS enforcement, snake/camel login decoding, tolerant OpenCode JSON envelope & tool-part parsing, `AuthInterceptor` header/401/expiry rules (`MockWebServer`), `SessionPoller` terminal states & backoff (`MockWebServer`), Markdown & syntax highlighting, and `ChatViewModel` state transitions:

```bash
./gradlew testDebugUnitTest
```

### 3. Build Installable Debug APK
```bash
./gradlew assembleDebug
```
The installable APK is written to:
```text
app/build/outputs/apk/debug/app-debug.apk
```
Install onto a connected Android 8.0+ phone or emulator via ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Automated Cloud Build (GitHub Actions)
`.github/workflows/android.yml` runs `python3 tools/check_project.py`, `./gradlew testDebugUnitTest`, and `./gradlew assembleDebug` on `ubuntu-latest` with JDK 17 and uploads `nexa-ai-debug-apk` (`app-debug.apk`) as a downloadable workflow artifact.
