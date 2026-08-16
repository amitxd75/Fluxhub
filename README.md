# FluxHub

<p align="center">
  <img src="artworks/banner.jpg" alt="FluxHub Banner" width="100%">
</p>

<p align="center">
  <b>Modern AI Conversational Client Powered by the Liquid Glass Design Language</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-24%2B-green?logo=android" alt="Android 24+">
  <img src="https://img.shields.io/badge/Kotlin-2.1-purple?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Latest-blue?logo=jetpackcompose" alt="Compose">
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="GPL-3.0 License">
</p>

---

## ✨ Features

### 🎨 Liquid Glass UI
- Frosted glass and translucent layered interface inspired by Liquid Glass aesthetics
- Real-time background blur, chromatic aberration, lens distortion, and dynamic vibrancy highlights
- Smooth 60fps+ rendering performance with hardware-accelerated Compose layers

### 💬 Intelligent Conversations
- Streaming responses with real-time token tracking
- Multi-turn conversations with full context management
- Visualized AI reasoning and thinking processes (with duration timer)

### 🤖 Multi-Provider & Model Support
- Compatible with OpenAI standard API format
- Support for DeepSeek, Claude, Gemini, GPT-4o, and self-hosted endpoints
- Seamless in-chat model switching and provider management

### 📝 Rich Markdown Rendering
- Multi-language syntax highlighting
- One-tap code block copying and collapsible code snippets
- Full support for tables, lists, blockquotes, and embedded images (Vision)

### 📱 Native Android Experience
- 100% Kotlin + Jetpack Compose
- Material Design 3 foundation
- Adaptive Dark / Light theme and customizable wallpaper & glass tint

---

## 📸 Feature Overview

| Module | Description |
|--------|-------------|
| 🏠 Home | Activity statistics, quick prompt chips, featured personas, and recent chats |
| 💬 Chat | Glass message bubbles, thinking process viewer, message branching, and editing |
| ⚙️ Settings | API provider management, assistant presets, wallpaper styling, and backup/restore |
| 🏝️ Dynamic Island | Real-time generation status indicator with token counters and elapsed timer |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug (or newer)
- JDK 21
- Android SDK 36 (API Level 36)

### Build Instructions

```bash
# 1. Clone the repository
git clone https://github.com/HenryZ-0302/Fluxhub.git
cd Fluxhub

# 2. Build Debug APK
./gradlew assembleDebug

# 3. Install to connected device
./gradlew installDebug
```

### Configuration

1. Open the application and navigate to **Settings** -> **Provider Management**.
2. Tap **+** to add your API provider:
   - **Base URL** (e.g., `https://api.openai.com/v1`)
   - **API Key** (e.g., `sk-...`)
3. Select your desired model from the top bar in the chat screen.
4. Start chatting!

---

## 🛠️ Technology Stack

| Library / Tool | Purpose |
|----------------|---------|
| Kotlin | Core programming language |
| Jetpack Compose | Declarative UI toolkit |
| Material 3 | Design components & typography |
| Room | Local SQLite database persistence |
| DataStore | Reactive preferences storage |
| OkHttp SSE | Real-time Server-Sent Events streaming |
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Liquid Glass backdrop rendering |

---

## 📁 Project Architecture

```
app/src/main/java/com/liquidglass/fluxhub/
├── chat/           # Chat, Home, Settings screens and ViewModel
│   ├── ChatScreen.kt
│   ├── HomeScreen.kt
│   ├── SettingsScreen.kt
│   ├── AssistantListScreen.kt
│   ├── ProviderListScreen.kt
│   ├── DisplaySettingsScreen.kt
│   ├── DynamicIslandSettingsScreen.kt
│   ├── MainScreen.kt
│   └── ChatViewModel.kt
├── components/     # Reusable Liquid Glass UI components
├── data/           # Data layer (Room DAOs, Entities, Settings, Personas)
├── ui/components/  # Specialized UI components (Markdown, Message bubbles, Tables)
│   ├── message/    # Message and thinking process components
│   └── richtext/   # Markdown parser and syntax highlighter
└── utils/          # Gesture and file helpers
```

---

## 📄 License

This project is licensed under the **GPL-3.0 License**.

The Liquid Glass effect engine is based on [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), licensed under Apache-2.0.

---

## 🙏 Acknowledgements

- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) - Liquid Glass rendering engine
- [RikkaHub](https://github.com/rikkahub/rikkahub) - Architecture and message branch inspiration
- [Lucide Icons](https://lucide.dev/) - Icon set
