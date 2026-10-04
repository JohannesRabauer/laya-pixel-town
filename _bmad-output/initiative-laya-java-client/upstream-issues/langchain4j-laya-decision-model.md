<!-- Target: https://github.com/langchain4j/langchain4j/issues/new?template=feature_request.md (labels: enhancement) -->

# [FEATURE] Laya support for DecisionModel (lang, truncation, batch)

**Is your feature request related to a problem? Please describe.**

[Laya](https://github.com/NandhaKishorM/laya) (Apache-2.0) is a self-hosted decision model server. Its `laya-serve` implements the same `POST /v1/systemone` protocol as TypeSafe. `TypeSafeDecisionModel` 1.21.0-beta31 works against it with `baseUrl("http://localhost:8002")`. I tested yes/no, choice and scale questions against Laya 0.3.26 in Docker, and all three come back correct.

The Laya-specific parts can't be used:

- `TypeSafeRequest` sends only `model`, `state` and `questions`. Laya also accepts `lang`, `lang_guess`, `max_len` and `min_confidence`. Laya's docs say to pass `lang` explicitly for Polish, Czech, Turkish and Swedish.
- The response's `routing` (which checkpoint and language Laya picked) and `usage.truncated` / `truncated_questions` are dropped. The English checkpoint reads 512 tokens and cuts the rest silently.
- Laya runs one forward pass at a time. `POST /v1/systemone/batch` answers up to 64 inputs with shared passes, and there is no way to call it.

**Describe the solution you'd like**

A Laya provider built on the extension points the decision API already documents:

- `LayaDecisionRequestParameters` extending `DefaultDecisionRequestParameters`: `lang`, `langGuess`, `maxLength`, `minConfidence`.
- `LayaDecisionResponseMetadata` extending `DecisionResponseMetadata`: routing (model, language) and truncation.
- Optionally, a batch call that uses `/v1/systemone/batch`.

This could be a new `langchain4j-laya` module or Laya options on the existing TypeSafe module. I don't have a strong preference and would follow what you prefer.

**Describe alternatives you've considered**

- Plain `TypeSafeDecisionModel` with `baseUrl`: works today, without the points above.
- Raw HTTP next to LangChain4j: loses retries, listeners and the decision components (routing, guardrails, tool selection).

**Additional context**

Laya's `confidence` is entropy-based. Its docs recommend thresholding the max probability instead, which callers can already compute from `probabilities()`. Same choice answer in my test: `confidence` 0.836, max probability 0.9633. A note in the docs would help anyone running the existing components against Laya.

I'm happy to send a PR once the shape is agreed.
