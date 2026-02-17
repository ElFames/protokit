# Agents Guide

This document describes how agents are expected to work within the ProtoKit
codebase.

Agents are used to assist development tasks such as code generation,
refactoring, documentation, and validation. They are **helpers**, not decision
makers.

---

## Goals

Agents in ProtoKit should:

- Reduce repetitive work
- Enforce architectural consistency
- Assist with documentation and validation
- Never introduce hidden behavior or magic

ProtoKit prioritizes explicitness and predictability.
Agents must follow the same philosophy.

---

## Allowed Agent Responsibilities

Agents may:

- Generate Kotlin source code following existing patterns
- Refactor internal APIs without changing public behavior
- Review code for consistency, safety, and correctness
- Help evolve documentation (README, examples, comments)
- Propose improvements clearly separated from implementation

---

## Disallowed Agent Responsibilities

Agents must NOT:

- Change public APIs without explicit human approval
- Introduce new abstractions without justification
- Hide logic behind implicit behavior
- Modify build logic or publishing configuration silently
- Make architectural decisions autonomously

---

## Code Generation Rules

When generating code:

- Follow existing style and structure
- Prefer explicit parameters to global state
- Avoid reflection and runtime codegen
- Use `internal` by default unless stated otherwise
- Do not assume future features exist

Generated code must always be readable and debuggable by humans.

---

## Runtime Constraints

Agents working on runtime code must respect:

- Kotlin Multiplatform compatibility
- Platform-specific implementations (Android / iOS)
- Explicit cancellation and timeout handling
- Correct gRPC framing and trailers semantics

No shortcuts are allowed in transport or protocol logic.

---

## Documentation Rules

When updating documentation:

- Be concise and factual
- Avoid marketing language
- Clearly state limitations and unsupported features
- Keep examples minimal and correct

If a feature is not implemented, it must be stated explicitly.

---

## Workflow

Suggested agent workflow:

1. Understand the current state of the codebase
2. Identify the scope of the task
3. Propose changes clearly
4. Apply changes incrementally
5. Validate against existing behavior

If unsure, the agent should stop and ask for clarification.

---

## Human-in-the-Loop

All agent output must be reviewed by a human before being merged.

ProtoKit is designed to be understandable without agents.
Agents are tools, not authors.

---
