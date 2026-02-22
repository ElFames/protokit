# ProtoKit Architecture

This document describes the high-level architecture of ProtoKit, focusing on its two-part design: a build-time code generation plugin and a lightweight runtime SDK.

ProtoKit is designed to be **explicit**, **predictable**, and **minimal**.

---

## Core Components

ProtoKit is composed of two primary modules:

-   **`protokit-plugin`**: A Gradle plugin responsible for build-time code generation.
-   **`protokit-sdk`**: A lightweight, multiplatform runtime library for executing gRPC calls.

This separation ensures that all the heavy lifting of parsing and code creation happens at compile time, leaving the runtime SDK small and efficient.

---

## Build-Time: The `protokit-plugin`

The plugin's sole responsibility is to generate clean, human-readable Kotlin code. It orchestrates a robust, multi-step process:

### 1. Proto Parsing (`protoc`)

The plugin leverages the official Google Protobuf plugin (`com.google.protobuf`, version `0.9.6`) to invoke the `protoc` compiler (`4.33.5`). This is the industry-standard tool for parsing `.proto` files.

### 2. Descriptor Set Generation

Instead of generating Java code, `protoc` is configured to output a `FileDescriptorSet` file (`descriptor.pb`). This binary file contains a complete, validated model of all the messages, services, and fields defined in the `.proto` files, with all cross-file imports already resolved. Our plugin ensures this by explicitly configuring the `generateDebugProto` task to produce this descriptor.

### 3. Kotlin Code Generation (`kotlinpoet`)

Our custom Gradle task, `generateProtoKitCode`, reads the `FileDescriptorSet`. Using this as the source of truth, it generates the corresponding Kotlin `data class`es (for messages) and service clients (interfaces and implementations) using the `kotlinpoet` (`2.2.0`) library. This guarantees type safety and results in clean, idiomatic code.

### 4. Plugin Configuration and Lifecycle

Correctly integrating with a Kotlin Multiplatform (KMP) project that has an Android target requires careful handling of the Gradle lifecycle:

-   **Source Set Configuration**: The plugin must wait for both the `org.jetbrains.kotlin.multiplatform` and `com.android.application` plugins to be applied before it can safely configure the Android `sourceSets`. It does this by nesting `plugins.withId` blocks. This ensures our configuration (`android.sourceSets.main.proto.srcDir(...)`) is applied *after* the KMP plugin has finished its own setup, preventing our changes from being overwritten.

-   **Task Dependency**: The `generateProtoKitCode` task is explicitly configured with `dependsOn(generateDebugProto)`. This is done inside a `project.afterEvaluate` block to ensure the `generateDebugProto` task (which is created by the Android plugin based on build variants) exists before we try to reference it.

This entire process happens at build time, with no impact on runtime performance.

---

## Run-Time: The `protokit-sdk`

The SDK provides the minimal, essential components needed to make gRPC calls from a Kotlin Multiplatform project.

-   **`ProtoClient`**: The main entry point. It manages the underlying HTTP transport and provides a simple, clean API for making unary calls.
-   **Transports**: Platform-specific implementations handle the details of gRPC-over-HTTP/2 for Android and iOS.
-   **`ProtoReader` / `ProtoWriter`**: Simple, efficient internal classes for encoding and decoding the generated message classes using a tag-based `while` loop.

---

## Call Flow (Unary gRPC)

1.  A developer calls a method on a **generated service client** (e.g., `userService.getUser(...)`).
2.  The generated method implementation delegates to the core `ProtoClient.unary()` function, passing the method path, the request object, and the encoder/decoder functions.
3.  The request object's **generated `encode()` method** is called, which uses `ProtoWriter` to serialize the data into a `ByteArray` using type-specific methods (`writeString`, `writeEnum`, etc.).
4.  The `ProtoClient` sends the request via the platform-specific **transport** (e.g., OkHttp on Android).
5.  The transport returns a `ByteArray` response body.
6.  The `ProtoClient` calls the **generated `decode()` method** on the response message's companion object, which uses `ProtoReader` to parse the bytes into a new `data class` instance.
7.  The final `Response<T>` object, containing the deserialized data and any gRPC metadata, is returned to the caller.

This flow is deterministic and contains no reflection or other runtime "magic."

---

## Design Principles

-   **Explicit over Implicit**: The plugin generates code you can read and debug. The runtime does what it's told.
-   **Small, Auditable Codebase**: Both the plugin and the SDK are kept as small and focused as possible.
-   **No Runtime Reflection**: All serialization logic is generated at compile time for maximum performance.
-   **Minimal Dependencies**: ProtoKit relies on established, high-quality libraries (`protoc`, `kotlinpoet`, `okhttp`) but avoids introducing a large dependency tree.
