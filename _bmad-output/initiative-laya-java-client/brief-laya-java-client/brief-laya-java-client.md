---
title: "Product Brief: Laya on the JVM"
status: draft
created: 2026-10-04
updated: 2026-10-04
---

# Product Brief: Laya on the JVM

## Executive Summary

Laya is an open-source (Apache-2.0) decision engine. It answers typed questions about text: choice, score, and noul (yes/no). It returns probabilities from one forward pass, with no text generation. Its server, `laya-serve`, runs as a Docker container and speaks the same `POST /v1/systemone` protocol as TypeSafe AI's hosted Jev.

Java already has two clients for that protocol: `org.springaicommunity:typesafe-java-sdk` 0.4.0 (with a Spring Boot starter and Spring AI components) and `dev.langchain4j:langchain4j-typesafe` 1.21.0-beta31. On 2026-10-04 we tested both unchanged against `laya-serve` (Laya 0.3.26, commit `2e4d9c8`). Noul, choice, and score all work.

They work only with the Jev-shaped subset, though. Some Laya features cannot be reached from Java today: the `lang` and `max_len` request controls, truncation warnings, routing info, and the batch endpoint. `answer_confidence` and abstention are dropped as well, but a caller can compute both from the returned probabilities.

**This initiative closes that gap upstream instead of shipping a third client.** It makes two contributions, one to each project. A standalone client is the fallback if both projects decline.

## The Problem

A Java team runs `laya-serve` in Docker and points LangChain4j or Spring AI TypeSafe at it. The calls succeed, but a few things go wrong quietly:

- **They cannot send `lang`, `lang_guess` or `max_len`.** Laya's docs say to pass `lang` explicitly for Polish, Czech, Turkish, and Swedish to avoid misrouting.
- **The response's `routing` block (which model and language Laya picked) is dropped.** That leaves no way to check misrouting.
- **The confidence field steers callers to the number Laya says not to threshold.** Both clients expose `confidence`, which in Laya is entropy-based. Laya's integration guide says to gate on `answer_confidence`, the max probability. In our test the same choice answer had `confidence` 0.836 and `answer_confidence` 0.9633.
  - The max probability can be derived from the returned probabilities, so this is a documentation and convenience gap, not a blocker.
  - The same goes for server-side `min_confidence` and its abstention verdict.
  - Spring AI TypeSafe's confidence gate pattern uses `confidence`.
- `usage.truncated` is dropped, so input cut at the 512-token window fails silently.
- Batches: Spring AI TypeSafe fans out single requests client-side, and LangChain4j has no batch call. Laya runs one forward pass at a time, so these requests queue up. `/v1/systemone/batch` shares forward passes across states.

The workaround today is hand-written HTTP next to the framework client. That loses retries, typed errors, and the framework components.

## The Solution

Two upstream contributions, each shaped to its project's existing extension points:

1. **Spring AI TypeSafe: vendor-neutral pass-through.**
   - Extra request fields on `SystemOneRequest`.
   - Unknown response and answer fields preserved instead of dropped.
   - No Laya-specific types. Any compatible server benefits, and the project's existing "Using Laya" page gets a working example.
2. **LangChain4j: a Laya provider.**
   - LangChain4j's `DecisionRequestParameters` and `DecisionResponseMetadata` are documented as extensible by provider integrations.
   - A Laya provider (new module, or Laya options on `TypeSafeDecisionModel`, whichever the maintainers prefer) adds `lang` and `minConfidence` parameters.
   - It reports routing and truncation in metadata, exposes `answerConfidence` and abstention, and can use the batch endpoint.

Both start as issues, so the maintainers can confirm interest and shape before any code is written.

## Who This Serves

- Java and Spring teams that self-host Laya for data locality or cost and already use Spring AI or LangChain4j.
- The two upstream projects, which get documented Laya support without maintaining a Laya fork.

## Success Criteria

- Both issues receive a maintainer response.
- At least one contribution is merged and released.
- After the merge, a Java user can do three things without raw HTTP:
  - send `lang`;
  - see truncation and routing;
  - find in the docs which confidence value to threshold on Laya.
- Each merged change is covered by a test against a real `laya-serve` (Testcontainers or the projects' existing opt-in IT setup).

## Scope

**In:**
- The two issues.
- After a positive response, the two pull requests with tests and docs.
- A reproducible compatibility check (Docker `laya-serve` plus a small Java harness).

**Out:**
- A standalone `dev.rabauer:laya-java-client`, unless both projects decline.
- In-process inference (ONNX). Laya stays a separate container by decision.
- Own builders, starter, or framework adapters; both projects already have them.

## Open Questions

- Spring AI TypeSafe is Jev-first. Will they accept pass-through fields, or prefer that Laya support live elsewhere?
- LangChain4j: does it want a separate `langchain4j-laya` module or Laya options on the TypeSafe one? Per-answer fields (`answer_confidence`, abstention) have no obvious home in the current answer classes.
- Fallback trigger: how long to wait for an answer before reconsidering a standalone client.

## Vision

A Laya user on the JVM picks the framework they already use, adds a base URL, and gets Laya's full decision surface with documented thresholds. No extra client and no Python in the application process.
