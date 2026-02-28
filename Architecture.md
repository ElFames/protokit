# ProtoKit Architecture

This document describes the high-level architecture of ProtoKit, focusing on its two-part design: a build-time code generation plugin and a lightweight runtime SDK.

ProtoKit is designed to be **explicit**, **predictable**, and **minimal**.

---

## Core Components

ProtoKit is composed of two primary modules:

-   **`protokit-plugin`**: A Gradle plugin responsible for build-time code generation for both Kotlin and Swift.
-   **`protokit-sdk`**: A lightweight, multiplatform runtime library for executing gRPC calls.

This separation ensures that all the heavy lifting of parsing and code creation happens at compile time, leaving the runtime SDK small and efficient.

---

## Build-Time: The `protokit-plugin`

The plugin's responsibility is to generate clean, human-readable code for all supported platforms. It orchestrates a robust, multi-step process:

### 1. Proto Parsing (`protoc`)
The plugin leverages the official Google Protobuf plugin (`com.google.protobuf`) to invoke the `protoc` compiler. This is the industry-standard tool for parsing `.proto` files.

### 2. Descriptor Set Generation
Instead of generating Java or Swift code directly, `protoc` is configured to output a `FileDescriptorSet` file (`descriptor.pb`). This binary file contains a complete, validated model of all the messages, services, and fields defined in the `.proto` files.

### 3. Kotlin & Swift Code Generation
Our custom Gradle tasks read the `FileDescriptorSet` and generate the necessary code:

-   **`generateProtoKitCode`**: Uses `kotlinpoet` to generate idiomatic Kotlin `data class`es and service clients.
-   **`generateIosGrpcTransport`**: Generates the `IosGrpcTransport.swift` file, which provides a native gRPC transport using the `gRPC-swift` library. It also modifies the `ContentView.swift` to automatically register this transport.

This entire process happens at build time, with no impact on runtime performance.

---

## Run-Time: The `protokit-sdk`

The SDK provides the minimal, essential components needed to make gRPC calls from a Kotlin Multiplatform project.

-   **`ProtoClient`**: The main entry point. It holds a reference to the platform-specific transport, which it retrieves from the `GrpcTransportProvider`.
-   **`GrpcTransportProvider`**: A crucial component that provides the correct `GrpcTransport` implementation based on the current platform (e.g., `OkHttpTransport` for Android, `IosGrpcTransport` for iOS). This allows the common code in `ProtoClient` to remain platform-agnostic.
-   **`ProtoReader` / `ProtoWriter`**: Simple, efficient internal classes for encoding and decoding the generated message classes.

---

## Call Flow (Unary gRPC)

1.  A developer calls a method on a **generated service client** (e.g., `userService.getUser(...)`).
2.  The generated method delegates to the core `ProtoClient.unary()` function.
3.  The `ProtoClient` retrieves the correct transport from the `GrpcTransportProvider`.
4.  The request object's **generated `encode()` method** is called, which serializes the data into a `ByteArray`.
5.  The `ProtoClient` sends the request via the platform-specific **transport**.
6.  The transport returns a `ByteArray` response body.
7.  The `ProtoClient` calls the **generated `decode()` method** to parse the bytes into a new `data class` instance.
8.  The final `Response<T>` is returned to the caller.

This flow is deterministic, type-safe, and contains no runtime "magic."
