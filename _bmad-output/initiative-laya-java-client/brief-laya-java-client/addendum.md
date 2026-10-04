---
title: "Addendum: Laya Java Client"
created: 2026-10-04
---

# Addendum: depth for the PRD, spec and architecture

These notes come from reading the sources: laya at HEAD 2026-10-03 (`laya/serve.py`, `docs/http-api.md`, `docs/questions-and-answers.md`, `sdk/typescript`, `laya-dotnet`), the laya-integration `SKILL.md`, and docs.typesafe.ai.

## Wire protocol (laya-serve)

**Endpoints**
- `POST /v1/systemone` handles a single state.
- `POST /v1/systemone/batch` takes `{states:[...], questions, ...}` and returns `{results:[...], total_usage}` in input order.
  - Batch-only controls: `batch_size` and `sort_by_length`.
- `GET /health` has no auth. Without a valid key it returns only `{status:"ok"}`; with one it adds loaded models, device and fallbacks.
- There are no streaming routes and no `/v1/models` (Jev has `/v1/models`).

**Request fields**
- `state`: a string, object or array.
- `questions`: an object mapping each id to a question.
- Optional: `model`, `task`, `lang`, `lang_guess`, `max_len`, `head_max_len`, `min_confidence`.
  - `min_confidence` is a number or a bucket map such as `{"choice:2":.., "choice:3-5":.., "default":..}`.
- `model` values:
  - These select a checkpoint: `english` | `multilingual` | `typed-decisions`, plus aliases.
  - Anything else (e.g. `jev-1`) means the router decides.
  - Jev requires `model` (e.g. `jev-latest`).

**Question shapes**
- choice: `criteria` is a `{label: description|null}` map. Order is positional and labels come back verbatim.
- score: `criteria` is a list of levels in ascending order.
- noul: optional `criteria:{true,false}`. `labels` is Laya-only.

**Answers**
- choice: `choice`, `probabilities`, `confidence`, `answer_confidence`, `action`.
- score: `score` (the expected value), `legend`, `probabilities` keyed by index.
- noul: `noul` = P(true).
- With `min_confidence` set, answers add `abstention` (`passed` | `abstained` | `unevaluated`), `abstention_threshold` and `low_confidence`.
- Response root: `model`, `answers`, `usage` (with `truncated` and `truncated_questions`), `routing` (with `detection`, which may be null).
- Headers: `Server-Timing` and `X-Inference-Time-Ms`.

**Jev-strict mode**
- With `LAYA_JEV_STRICT=1`, the response is trimmed to `model`, `answers` and `usage`. Noul answers then carry only `noul`.
- The client must treat every Laya-only field as optional.

**Errors**
- The body is `{"detail": "..."}`.

| Status | Meaning |
|---|---|
| 400 | Malformed request |
| 401 | Bad or missing bearer token |
| 413 | A limit was exceeded |
| 422 | Invalid question or control |
| 500 | `inference failed` |
| 503 | Busy, with `Retry-After` |

- Jev additionally uses 429 and 529.

**Limits (Laya)**

| Limit | Value |
|---|---|
| Body size | 2 MiB |
| `state` | 50k chars |
| Questions | 64 |
| Batch states | 64 |
| Choice options | 100 |
| Score levels | 32 |
| Options across all questions | 512 |

- Jev limits: score 2–10 levels; choice up to 255 options.
- **Implication:** client-side validation must be backend-aware or permissive.

## Jev vs Laya semantic differences
- `confidence` is entropy-based in Laya and formula-based in Jev, so thresholds do **not** transfer.
- Laya noul answers carry `confidence`; Jev noul answers don't.
- In Laya, `output_tokens` is always 0 and the response `model` is `"laya-rl-agent"`.
- Laya has no 429; it returns 503 instead.

## Reference SDK shapes

**TypeSafe Python SDK**
- `TypeSafeClient(api_key, model, retry=RetryPolicy, timeout=10s, headers, base_url)`.
- `system_one(state, questions, model, retry, timeout, extra_headers, extra_body, response_model)`.
- Typed views: `.nouls`, `.choices`, `.scores`.
- `RetryPolicy` defaults: `max_retries=2`, backoff 0.5–5 s with jitter, statuses 408/429/5xx, `respect_retry_after`.
- Env vars: `TYPESAFE_API_KEY`, `TYPESAFE_BASE_URL`, `TYPESAFE_DEFAULT_MODEL`.
- Exceptions:
  - `TypeSafeAPIError(status, body, headers, request_id)` is the base.
  - Subclasses: BadRequest, Authentication, PermissionDenied, NotFound, UnprocessableEntity, RateLimit (`retry_after_ms`), InternalServer.
  - Separate connection and timeout errors.

**laya-client (TypeScript 0.1.0, unpublished)**
- `new Laya({baseURL, apiKey, model, timeoutMs=120000})`.
- `predict(...)` and `health()`.
- No batch method and never retries.
- Validates locally before sending.
- Presets: triage, email, guard, moderation, router.
- Errors: `LayaAPIError`, `LayaValidationError`, `LayaConnectionError`, `LayaTimeoutError`.

**jevai.org `decide()` shorthand** (list → choice, `bool` → noul, `("score",1,5)`)
- It does not match the official TypeSafe docs and looks like a third-party wrapper.
- Treat it as a design idea only.

## Traps the client should encode (from laya-integration SKILL.md)
- Gate on `answer_confidence` (max p), never on entropy `confidence`, and never use one threshold for both.
- Thresholds depend on the question and on the option count; a threshold measured on 3 options doesn't transfer to 20. Don't default to 0.5.
- Score: take the argmax of `probabilities` plus the `legend`; never round the expected value.
- Front-load content. English reads 512 tokens and the others 1024; the state is cut from the end. Surface `usage.truncated` loudly.
- Options are truncated to about 48 tokens each, within a shared head budget of about 192–256 tokens, so dense descriptions get 422. Keep to about 20 or fewer options and describe each one.
- Pass `lang` explicitly for pl, cs, tr and sv.
- Warm up with one throwaway call at startup. Keep the HTTP connection alive.
- Cascade pattern: decide what is confident, escalate the rest to an LLM or a human.

## Existing JVM clients (prior art)
- `org.springaicommunity:typesafe-java-sdk` 0.4.0 (repo spring-ai-community/spring-ai-typesafe, created 2026-09-20, Apache-2.0).
  - Built on Spring `RestClient` and Jackson 3.
  - Starter: `spring-ai-starter-typesafe`. The `typesafe-spring-ai` module adds judge, guardrail, RAG filter/reranker and tool index.
  - Has a "Using Laya" docs page listing the protocol differences.
  - `SystemOneRequest` holds only `state`, `model` and `questions`.
  - Answers are records with `@JsonIgnoreProperties(ignoreUnknown = true)`.
  - `systemOneAll` fans out single calls on a thread pool.
- `dev.langchain4j:langchain4j-typesafe` 1.21.0-beta31 (experimental, released 2026-10-02).
  - `TypeSafeDecisionModel implements DecisionModel` and works with any `/v1/systemone` server via `baseUrl`.
  - Internal DTOs: the request has `model`, `state` and `questions`; the answer has `type`, `noul`, `choice`, `score`, `probabilities` and `confidence`.
  - `DecisionRequestParameters` and `DecisionResponseMetadata` are documented as extensible by providers.

## Compatibility test (2026-10-04)
- Server: `laya-serve` from Laya's own `compose.yaml` + `compose.http.yaml`.
  - Laya 0.3.26, commit `2e4d9c8`, `english` checkpoint, CPU, port 8002.
- Client: a Java 21 harness in the session scratchpad, calling both libraries with the same three questions.
- Result: both return identical values.

| Question | Result |
|---|---|
| noul `refund` | 0.9219 |
| choice `queue` | `billing` (0.9633, confidence 0.836) |
| score `urgency` | 1.7411 over 4 levels (confidence 0.2406) |

- Usage: 155 input tokens, 0 output tokens. Model `laya-rl-agent`.
- The same request via curl with `lang:"en"` and `min_confidence:0.9` returned:
  - choice: `answer_confidence` 0.9633, `abstention: passed`.
  - score: `answer_confidence` 0.4746, `abstention: abstained`, `low_confidence: true`.
  - `routing.model: english`; `usage.truncated: false`.
- None of this is reachable through either client.
- `/v1/systemone/batch` answered 3 states in one call.

## Rejected
- **In-process inference (ONNX Runtime Java).** Laya stays a separate Docker container (user decision 2026-10-04).
  - For reference: no ONNX artifacts are published, the fused export is about 1.29 GB, and the C# `Laya.Onnx` port would be the template.
- **Standalone `dev.rabauer:laya-java-client`** with its own builders, Spring Boot starter and adapters. It duplicates the two existing clients. Fallback only.
- **jevai.org `decide()` shorthand.** It does not match the official TypeSafe docs and looks like a third-party wrapper.
