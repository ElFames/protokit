![ProtoKit Banner](https://github.com/user-attachments/assets/16223ca8-5221-4993-9e80-1a7a0b8686c5)

**ProtoKit** is a lightweight, pragmatic gRPC client for Kotlin Multiplatform, designed with a "code-first" philosophy. It generates clean, human-readable Kotlin code from your `.proto` files, giving you full control without the overhead of complex runtimes.

---

## 📊 Support Matrix

### Platforms
| Platform | Status | Transport Implementation |
| :--- | :--- | :--- |
| **Android** | ✅ Supported | **Native gRPC-Java** |
| **iOS** | ✅ Supported | **Native gRPC-Swift** |
| **JVM (Desktop)** | ✅ Supported | **Native gRPC-Java** |
| **Web** | 🚧 Planned | - |

### ✅ Features
- **Unary Calls**: Full support for the standard gRPC request-response flow.
- **Native Stack Integration**: Uses the official gRPC stack on Android/Desktop (gRPC-Java) and iOS (gRPC-Swift) for maximum performance and reliability.
- **Proto3 Data Types**: Includes `string`, `int32`, `int64`, `bool`, `float`, `double`, `bytes`, nested messages, `enums`, `repeated`, `map`, `oneof`, and `any` fields.
- **Robust Code Generation**: Powered by the official `protoc` compiler and `kotlinpoet` for clean, predictably generated code.
- **Cross-File Imports**: Messages from one `.proto` file can be used in another, and the plugin will resolve them correctly.

### ❌ Not Supported
- **gRPC Streaming**: Not yet supported (Work in progress for native transports).
- **Interceptors**: No support for client-side interceptors at this time.

---

## 💡 Design Philosophy

ProtoKit is built on three core principles:

1.  **Explicitness over Magic**: The generated code is straightforward and easy to debug. What you see is what you get.
2.  **Platform Optimization**: We use native gRPC implementations on all platforms to leverage system-level optimizations and connectivity awareness.
3.  **Minimalism**: A small, focused runtime (`protokit-sdk`) keeps your app lightweight, while still using the most robust transport protocols available.

---

## 🛠️ How to Use

### 1. Add Dependencies

#### For iOS (in Xcode)
Add the `gRPC-swift` package dependency to your Xcode project. **Important: ProtoKit currently supports gRPC-Swift version 1.x.** ProtoKit will automatically configure the generated transport to use it.

#### For Gradle (in your KMP module)
In your module's `build.gradle.kts`, apply the plugin and add the runtime SDK. 

**Important:** To ensure the iOS native bridge works correctly, you **must** use `api()` instead of `implementation()` for the SDK. This allows the generated gRPC models and response types to be visible to the iOS platform. Additionally, you **must** `export()` the library in your iOS framework configuration.

```kotlin
plugins {
    id("com.fames.protokit.plugin") version "0.1.2"
}

kotlin {
    // 1. Mandatory: Add the SDK as an 'api' dependency for iOS bridge support
    sourceSets {
        commonMain.dependencies {
            api("com.fames.protokit:protokit-sdk:0.1.1")
        }
    }

    // 2. Mandatory: Export the library in your iOS framework targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            export("com.fames.protokit:protokit-sdk:0.1.1")
        }
    }
}
```

The plugin will automatically handle the necessary native dependencies for Android and iOS.

### 2. Place Your `.proto` Files
Put your `.proto` files in `src/commonMain/protos`. The plugin will automatically find and process them.

### 3. Build Your Project
Run a Gradle build. The plugin will automatically generate all the necessary Kotlin and Swift code.

---

## 🚀 Quick Start

```kotlin
// 1. Configure the base client
val client = ProtoClient(
    baseUrl = "https://api.your-service.com"
)

// 2. Instantiate the auto-generated service client
val userService = UserServiceClient(client)

// 3. Make the call asynchronously
suspend fun fetchUser() {
    userService.getUser(GetUserRequest(user_id = "75"))
        .onSuccess { user ->
            println("User received: ${user.display_name}")
        }
        .onFailure { error ->
            println("gRPC Error: ${error.status} - ${error.message}")
        }
}
```

---

## ⚙️ How It Works

ProtoKit uses a robust, build-time code generation process to create clean, type-safe Kotlin clients from your `.proto` files. It bridges the gap between Kotlin Multiplatform and the native gRPC ecosystems of each platform.

For a more detailed explanation, see the [**ARCHITECTURE.md**](Architecture.md) document.
