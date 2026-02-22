# Agents Guide

This document describes how agents are expected to work within the ProtoKit codebase. Agents are considered **expert assistants**, not autonomous decision-makers.

---

## Core Philosophy

Agents in ProtoKit must adhere to the project's core principles:

-   **Explicitness over Magic**: Reduce repetitive work, but never introduce hidden behavior.
-   **Architectural Consistency**: All changes must align with the established architecture.
-   **Predictability**: The outcome of any agent action should be easily understandable by a human developer.

---

## Allowed Agent Responsibilities

-   **Generate Code**: Create Kotlin source code that follows the established patterns.
-   **Refactor**: Improve internal APIs without changing public behavior.
-   **Document**: Help evolve documentation (`README.md`, code comments, etc.) to keep it aligned with the code.
-   **Validate**: Review code for consistency, safety, and correctness.

---

## Disallowed Agent Responsibilities

-   Do not change public APIs without explicit human approval.
-   Do not introduce new, unrequested abstractions.
-   Do not modify build logic or publishing configurations silently.
-   Do not make architectural decisions autonomously.

---

## Code Generation Rules

The primary task for agents in this project is code generation. This process must follow a strict pattern:

1.  **Source of Truth**: The `.proto` files are the single source of truth.
2.  **Parsing**: Parsing is handled **exclusively** by the official `protoc` compiler, invoked via the `com.google.protobuf` Gradle plugin. The plugin is configured to generate a `FileDescriptorSet`.
3.  **Code Creation**: The agent reads the `FileDescriptorSet` and uses it as a model to generate Kotlin code with the `kotlinpoet` library.

**Key constraints:**

-   **No Manual Parsing**: Agents must not attempt to parse `.proto` files manually.
-   **Follow Existing Style**: The generated code must match the style and structure of the code in `ProtoKitCodegen.kt`.
-   **Readable and Debuggable**: Generated code must always be clear and easy for a human to debug.

---

## Workflow

1.  **Understand**: Analyze the current state of the codebase and the user's request.
2.  **Clarify**: If the request is ambiguous, ask for clarification before proceeding.
3.  **Propose**: Outline the steps you will take.
4.  **Execute**: Apply changes incrementally.
5.  **Validate**: Ensure the changes are correct and do not break existing functionality.

---

## Human-in-the-Loop

All agent output must be reviewed by a human. Agents are tools to augment developer productivity, not to replace developer oversight.
