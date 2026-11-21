# Naira Assistant for Android

Naira Assistant is an Android personal assistant with chat, voice conversations, on-device wake-word detection, device actions, and personal organizers. It combines cloud AI and speech services with Android system features and local storage.

This guide describes the implementation in this repository. Provider model availability, service pricing, Android permission behavior, and external API requirements can change; check the provider's current documentation before deployment.

## Contents

- [Feature overview](#feature-overview)
- [Getting started](#getting-started)
- [Permissions and privacy](#permissions-and-privacy)
- [Developer setup](#developer-setup)
- [Project structure](#project-structure)
- [Contributor guidelines](#contributor-guidelines)
- [Troubleshooting](#troubleshooting)
- [Official references](#official-references)

## Feature overview

### Assistant and chat

- Chat with a configurable assistant persona, language, and thinking preference.
- Select Gemini, OpenRouter, or Groq for assistant responses. OpenRouter also has a configurable model ID.
- Stream responses and show tool activity and results in chat.
- Attach images to supported chat and image-generation flows.
- Generate images using the configured image service; generated or shared images may use the configured image-storage integration.
- Keep conversation history and memories according to the available settings.

The assistant needs a working network connection and a valid credential for the selected provider. Model access and capabilities depend on the provider and selected model.

### Voice and wake words

- Start a voice conversation from the in-app microphone control.
- Choose Android speech recognition or a supported configured speech provider: OpenAI Realtime, DashScope, Volcengine, MiMo, Step, or DeepSeek.
- Choose assistant reply language and voice/speech settings.
- Run background wake-word listening through a microphone foreground service, with a persistent notification.
- Run wake-word inference on-device using the bundled sherpa-onnx GigaSpeech Zipformer model.

The ONNX module's bundled `keywords.txt` currently contains **“Hey Assistant”** and **“Hello Assistant.”** Some in-app copy also mentions **“Wakeup Assistant,”** but that phrase is not in the bundled ONNX keyword file at the time of writing. The model supports English keyword spotting; Bengali replies do not mean the wake-word model recognizes Bengali phrases.

Background microphone listening requires microphone permission, notification permission where required, and starting the service while Android permits microphone access. Android may stop or restrict background work according to system policy and battery settings.

### Personal organizer

- Create, update, search, list, and delete notes.
- Create and manage to-dos, search them, and mark them done or undone.
- Create, update, search, snooze, complete, and delete reminders.
- Create, list, view, update, and delete calendar events.
- Create, list, update, and delete alarms; set timers and show alarms.
- View the reminder inbox and profile information.
- Save, search, update, merge, and remove assistant memories, including categories, importance, and tags.

Data persistence is feature-specific: the app uses local databases/preferences for some data and Firebase-backed services for account-linked settings, keys, memories, or reminders where configured. An internet connection and a signed-in account are needed for cloud synchronization.

### Device and daily tasks

Depending on Android support, permissions, and enabled services, the assistant can:

- Read device information and current time.
- Find, check, and open installed apps.
- Control flashlight, Bluetooth, Wi-Fi, media playback, and media volume.
- Read the current media status and play or stop a phone-finder alert.
- Compose email and text-message drafts or share text through Android's available apps.
- Use screen-reading and screen-action tools when the user enables the app's Accessibility service.
- Perform supported calculations and terminal-module tasks.
- Find live cricket scores, matches, and scorecards when CricAPI is configured.

Android may show system confirmation, permission, or app-selection screens. App availability and device policies affect which actions can succeed. A composed message is presented for user action; it is not silently sent by the assistant.

### Settings and account

Settings include account/profile details, assistant name/voice/language/relationship, thinking mode, speech recognition and speech engine, AI provider and API keys, OpenRouter model, appearance, permissions, and sensitive assistant-action access. Account screens support sign-in and password reset. Cloud settings are synchronized for a signed-in user when Firebase is configured and reachable.

## Getting started

1. Install and open the app.
2. Sign in or create an account if you want account-linked settings and cloud synchronization.
3. Open **Settings → Providers & API Keys** and select an AI provider. Add that provider's API key. Set an OpenRouter model ID if using OpenRouter.
4. Set up speech recognition and speech output under **Settings → Engine** if you plan to use voice.
5. Grant only the Android permissions needed for the features you want. Voice and background wake-word listening need microphone access; reminders and alarms may need notifications or exact-alarm access; calendar actions need calendar access.
6. Start a voice session from the microphone control. To keep wake-word listening active, enable it from the app while it is visible and keep its foreground notification enabled.
7. Configure CricAPI or image services in settings when using those integrations.

API credentials are user-provided service credentials. Do not put personal keys in source files, screenshots, issue reports, or commits. Provider credentials may incur charges and are governed by the provider's terms and quotas.

## Permissions and privacy

The app declares permissions for the following optional capabilities. Android requests or settings access are needed only when a feature uses them; denial can disable the related feature.

| Permission or access | Used for |
| --- | --- |
| Microphone | Voice input, speech recognition, and wake-word listening |
| Notifications | Foreground voice status, reminders, and alerts |
| Calendar | Reading and writing calendar events |
| Exact alarms | Accurate alarm and reminder scheduling when permitted by Android |
| Photos, video, and audio | Accessing selected media for supported features |
| Bluetooth / nearby devices | Routing voice audio to a Bluetooth headset and supported device actions |
| Wi-Fi state/change | Wi-Fi status or supported Wi-Fi controls |
| Accessibility service | Optional screen text reading and screen actions; must be enabled by the user in Android settings |
| Installed-app visibility | Finding or opening installed apps |
| Wake lock | Keeping a supported active alert or voice operation active |

Wake-word audio is processed locally by the bundled sherpa-onnx model. Voice recognition, chat, image generation, web/search, cricket lookup, and image storage may send the relevant request, transcript, prompt, or media to the selected external provider. Review each service's privacy policy and configure only services you intend to use. The app's cloud sync also sends the settings or records selected for synchronization to Firebase.

## Developer setup

### Requirements

- Android Studio and the Android SDK platform used by this project (compile SDK 37).
- A JDK compatible with the checked-in Gradle wrapper and Android Gradle Plugin. Kotlin/Java compilation targets JVM 17.
- Android 8.0 (API 26) or newer for the configured minimum Android version.
- A Firebase project and app configuration for authentication and cloud-backed features.
- Provider credentials entered through the app for any external AI, speech, image, or cricket integrations you want to exercise.

The version catalog currently pins AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.11.01, and Firebase BOM 35.2.0. Use the checked-in wrapper and version catalog; do not casually upgrade the build toolchain.

### Build and launch

1. Open the repository in Android Studio and let Gradle sync.
2. Configure Firebase for your own development project. Provide the matching `google-services.json` in the app module and enable the authentication methods and database rules required by the features you use. Never commit a production Firebase configuration or service credential unless the repository's release policy explicitly permits it.
3. Choose the `app` run configuration and launch on an Android device or emulator. Microphone, Bluetooth, alarms, Accessibility, and hardware controls require suitable device support; an emulator may not provide them.

From the repository root, the standard wrapper commands are:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugKotlin
```

The app's manifest sets `minSdk = 26` and the current build target to API 37. Platform restrictions become more important for microphone foreground services, notifications, media access, and exact alarms on recent Android versions. Test the relevant path on the Android releases and devices you intend to support.

### External service setup

- **Firebase:** configure Authentication and the Realtime Database / Firestore products used by the enabled app features, then set appropriate security rules. Never use open database rules for production.
- **AI provider:** enable the account and model access with the selected provider; enter the key through app settings.
- **Speech recognition:** choose a provider and add its key where required. Android system recognition does not use an app API key.
- **CricAPI:** add a CricAPI key before asking for live cricket data.
- **Image generation/storage:** configure the corresponding provider credential before using those features.

Availability, account requirements, supported models, quotas, and billing are controlled by each provider and can change independently of this app.

## Project structure

The Android app and its app-specific integration code are under `app/`.

| Area | Responsibility |
| --- | --- |
| `app/src/main/java/com/naira/assistant/presentation/` | Compose screens, navigation, settings, profile, and view models |
| `app/src/main/java/com/naira/assistant/core/sound/` | Voice sessions, speech integration, text-to-speech, and wake-word audio capture |
| `app/src/main/java/com/naira/assistant/core/tools/` | Tool definitions, dispatch, and app-side device/data integrations |
| `app/src/main/java/com/naira/assistant/core/database/` | Local note, to-do, reminder, alarm, and memory persistence |
| `app/src/main/java/com/naira/assistant/core/sync/` and `.../core/service/` | Firebase-backed auth, settings, and synchronization adapters |
| `app/src/main/java/com/naira/assistant/domain/` | App settings, account data, and domain-level types |
| `:ai` | AI provider and agent functionality consumed by the app |
| `:common` | Shared project types and utilities |
| `:search` | Search and web extraction functionality |
| `:speech` | Speech-provider functionality |
| `:onnx` | sherpa-onnx Kotlin API, native libraries, and bundled wake-word model assets |
| `:terminal` | Terminal-related functionality used by app tools |

The exact public APIs and module ownership are defined by each module. Integrate with those APIs rather than copying module implementations into the app.

## Contributor guidelines

- Read the repository's `AGENTS.md` before making project changes.
- Keep app-specific changes inside `app/`. Other modules, root build configuration, version catalogs, wrapper files, and `docs/` are separately owned or protected by the project rules.
- Check the actual implementation and existing module APIs before changing behavior. Prefer a small, focused change and avoid duplicate implementations or dependencies.
- Keep UI state and events flowing through the existing presentation/view-model architecture. Collect state with lifecycle awareness and keep blocking work off the main thread.
- Preserve API 26 compatibility. Check runtime permission, foreground-service, and lifecycle behavior on supported Android versions.
- Do not log or commit passwords, API keys, access tokens, personal content, or user-provided media.
- Update documentation when user-visible features, setup, permissions, or service requirements change.
- Run the relevant app compile task when the environment allows it. Report actual build/test results and any verification that could not be performed.

## Troubleshooting

| Symptom | Checks |
| --- | --- |
| Assistant reports a provider/authentication error | Confirm the selected provider, saved key, model access, account quota, and network connection. |
| Voice input does not start | Grant microphone permission, select a configured speech provider, and verify that the device has a recognition service or network access as applicable. |
| Background wake-word listening does not start | Start it while the app is visible, grant microphone and notification permissions, keep notifications enabled, and check Android battery/background restrictions. |
| Wake word is not detected | Use one of the phrases present in the bundled keyword list, speak clearly, and confirm the ONNX assets are packaged for the device ABI. |
| A device action is unavailable | Check the relevant permission, Accessibility setting, Android version, hardware support, and the target app/system policy. |
| Calendar, reminders, or cloud records do not appear | Check runtime permission, account sign-in, Firebase configuration and database rules, network access, and notification/exact-alarm settings. |
| Cricket or image feature fails | Confirm the relevant provider key, service availability, quota, and network connection. |

## Official references

These are the official references relevant to the integrations and platform behavior used here. Consult the docs for the exact provider and Android version you deploy against.

- [Jetpack Compose documentation](https://developer.android.com/develop/ui/compose)
- [Android foreground service declarations and permissions](https://developer.android.com/develop/background-work/services/fgs/declare)
- [Android foreground service background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Firebase Authentication for Android](https://firebase.google.com/docs/auth/android/start)
- [Firebase Realtime Database for Android](https://firebase.google.com/docs/database/android/start)
- [Firebase Firestore for Android](https://firebase.google.com/docs/firestore/quickstart)
- [sherpa-onnx v2.0.2 Kotlin `KeywordSpotter` API](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.2/sherpa-onnx/kotlin-api/KeywordSpotter.kt)
- [sherpa-onnx keyword spotting and keyword-file format](https://k2-fsa.github.io/sherpa/onnx/kws/index.html)
- [Gemini API documentation](https://ai.google.dev/gemini-api/docs)
- [OpenRouter API documentation](https://openrouter.ai/docs)
- [Groq API documentation](https://console.groq.com/docs)
