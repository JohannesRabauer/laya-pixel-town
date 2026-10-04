<!-- Target: https://github.com/spring-ai-community/spring-ai-typesafe/issues/new (no issue template) -->

# Pass through extra request and response fields

I run Laya's `laya-serve` (0.3.26) in Docker and call it with `typesafe-java-sdk` 0.4.0, as your "Using Laya" page describes. Noul, choice and score work unchanged. Thanks for documenting that.

Laya accepts and returns a few fields outside the Jev contract, and the SDK can't reach them:

- Request: `lang`, `lang_guess`, `max_len`, `min_confidence`. `SystemOneRequest` only serializes `state`, `model` and `questions`.
- Response: `routing` (which checkpoint and language Laya picked), `usage.truncated` / `truncated_questions`, and per answer `answer_confidence` and `abstention`. The records ignore unknown properties, so these are dropped.

`lang` and `usage.truncated` are the two that matter in practice. Laya's docs say to pass `lang` explicitly for Polish, Czech, Turkish and Swedish. Its English checkpoint reads 512 tokens and silently cuts the rest, so without `truncated` you don't know.

Proposal, with no Laya-specific types:

1. `SystemOneRequest.Builder#extraBody(String name, Object value)`, merged into the JSON body. The TypeSafe Python SDK has `extra_body` for the same purpose.
2. Keep unknown fields on `SystemOneResponse`, `Usage` and each `Answer`, for example `Map<String, JsonNode> extra()` filled via `@JsonAnySetter`.

Any other compatible server (Ollama, OpenRouter) gets the same escape hatch.

A smaller point for the Laya page: Laya's `confidence` is entropy-based, and Laya recommends thresholding the max probability instead. Same choice answer in my test: `confidence` 0.836, max probability 0.9633. I can add a short note on that as part of the PR.

I'm happy to send a PR if this direction works for you.
