# ProtoKit Architecture

This document describes the high-level architecture of ProtoKit and the design
decisions behind it.

ProtoKit is designed to be **explicit**, **predictable**, and **minimal**.

---

## Overview

ProtoKit consists of two main components:

- **protokit-plugin** – build-time code generation
- **protokit-sdk** – runtime gRPC client for Kotlin Multiplatform

There is a strict separation between build-time concerns and runtime behavior.

---

## Modules

### protokit-plugin

Responsibilities:

- Parse `.proto` files
- Generate Kotlin source code
- Integrate with Gradle
- Run at build time only

Constraints:

- No runtime dependencies
- No network logic
- No reflection
- No hidden behavior

All code in this module is considered **internal**.

---

### protokit-sdk

Responsibilities:

- Encode and decode protobuf messages
- Execute gRPC calls
- Handle transports per platform
- Expose a minimal public API

Structure:

- `sdk/` – public API (stable)
- `core/` – internal implementation details

Only `sdk` is considered public and stable.

---

## Public API

The only entry point is:

- `ProtoClient`

All other types are `internal` unless explicitly documented.

---

## Call Flow (Unary)

1. User calls a generated client method
2. Generated code delegates to `ProtoClient`
3. Request is encoded into protobuf bytes
4. Transport frames the gRPC message
5. HTTP/2 request is executed
6. Response body is unframed
7. Trailers are parsed
8. `Response<T>` is returned

No magic or implicit behavior occurs in this flow.

---

## Transports

Transports are platform-specific:

- Android → OkHttp
- iOS → NSURLSession

Each transport is responsible for:

- gRPC framing / unframing
- Timeout handling
- Cancellation
- Trailer extraction

Compression and advanced HTTP/2 features are currently unsupported.

---

## Protobuf Support

- Proto3: supported
- Proto2: planned

Encoding and decoding is implemented manually to avoid reflection and large
runtime dependencies.

---

## Design Principles

ProtoKit follows these principles:

- Explicit APIs over implicit behavior
- Small, auditable codebase
- No runtime code generation
- Minimal dependencies
- Human-readable generated code

---

## Non-Goals

ProtoKit intentionally does NOT:

- Implement a full gRPC server
- Support interceptors (for now)
- Provide automatic retries
- Hide network errors
- Emulate official gRPC SDKs

These decisions are intentional.

---

## Future Evolution

ProtoKit is expected to evolve carefully.
Public APIs should change rarely and only with clear migration paths.

---
