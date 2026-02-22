![ProtoKit Banner](https://github.com/user-attachments/assets/16223ca8-5221-4993-9e80-1a7a0b8686c5)

**ProtoKit** is a lightweight, pragmatic gRPC client for Kotlin Multiplatform, designed with a "code-first" philosophy. It generates clean, human-readable Kotlin code from your `.proto` files, giving you full control without the overhead of complex runtimes.

---

## 📊 Support Matrix

### Platforms
| Platform | Status |
| :--- | :--- |
| **Android** | ✅ Supported |
| **JVM** | ✅ Supported |
| **iOS** | ✅ Supported |
| **Web** | 🚧 Planned |

### ✅ Features
- **Unary Calls**: Full support for the standard gRPC request-response flow.
- **Proto3 Data Types**: Includes `string`, `int32`, `int64`, `bool`, `float`, `double`, `bytes`, nested messages, `enums`, `repeated`, `map`, `oneof`, and `any` fields.
- **Robust Code Generation**: Powered by the official `protoc` compiler (`4.33.5`) and `kotlinpoet` (`2.2.0`) for clean, predictable code.
- **Cross-File Imports**: Messages from one `.proto` file can be used in another, and the plugin will resolve them correctly.

### ❌ Not Supported
- **gRPC Streaming**: Client, server, or bidirectional streaming is not yet supported.
- **Interceptors**: No support for client-side interceptors at this time.

---

## 💡 Design Philosophy

ProtoKit is built on three core principles:

1.  **Explicitness over Magic**: The generated code is straightforward and easy to debug. What you see is what you get.
2.  **Minimalism**: A small, focused runtime (`protokit-sdk`) keeps your app lightweight.
3.  **Build-Time Power**: All code generation happens at compile time. This means zero reflection and maximum performance at runtime.

---

## 🛠️ How to Use

### 1. Add the Plugin
In your module's `build.gradle.kts`, apply the ProtoKit plugin. It will automatically apply and configure the official Google Protobuf plugin for you.

```kotlin
plugins {
    // The ProtoKit plugin that generates KMP client code
    id("com.fames.protokit.plugin") version "0.1.1"
}
```

### 2. Add the SDK Dependency
Add the `protokit-sdk` to your common source set:

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.fames.protokit:protokit-sdk:0.1.1")
            }
        }
    }
}
```

### 3. Place Your `.proto` Files
Put your `.proto` files in `src/commonMain/protos`. The plugin will automatically find and process them.

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
    val response = userService.getUser(GetUserRequest(user_id = "42"))

    response
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

ProtoKit uses a robust, build-time code generation process to create clean, type-safe Kotlin clients from your `.proto` files.

1.  **Automatic Configuration**: The `com.fames.protokit.plugin` automatically applies and configures the official `com.google.protobuf` plugin.
2.  **Descriptor Generation**: It configures the `protoc` compiler to generate a `descriptor.pb` file, which is a machine-readable model of your proto definitions.
3.  **Kotlin Code Generation**: Our plugin then reads this descriptor file and uses `kotlinpoet` to generate idiomatic Kotlin data classes and service interfaces.

This entire process is designed to work seamlessly within a Kotlin Multiplatform project, correctly handling the complexities of the Android and KMP Gradle plugins.

For a more detailed explanation, see the [**ARCHITECTURE.md**](ARCHITECTURE.md) document.
