---
id: SPEC-pixel-town
companions:
  - ../ux-pixel-town/DESIGN.md
  - ../ux-pixel-town/EXPERIENCE.md
  - stack.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. Source documents listed in frontmatter are for traceability — consult them only if you need narrative rationale or prose color this contract intentionally omits.

# Pixel Town

## Why

A vision to realize. The author wants a small, very visual web demo that shows how fast Laya, a decision engine that returns typed answers from one forward pass, can decide for many agents at once. The viewer is a god-like observer of a pixel-art town, not a player. Watching a hundred tiny people choose what to do, and being able to open any one decision, is the demonstration.

## Capabilities

- **CAP-1**
  - **intent:** The viewer can set a few parameters (population, decision interval, seed, Laya address) and start a town only when Laya is reachable.
  - **success:** With Laya down, Start is disabled and the page names the address. With Laya up, Start opens a town with the chosen population.

- **CAP-2**
  - **intent:** People with five needs (hunger, energy, social, fun, money) live in a top-down pixel town, and Laya picks each person's next activity among sleep, eat, work, socialize, relax, shop and wander.
  - **success:** Over a simulated day with 100 people, every activity is chosen at least once, and most people are asleep indoors at night without any rule that forces it.

- **CAP-3**
  - **intent:** Decisions that are due are sent to Laya together in batches, not one request per person, as fast as Laya answers.
  - **success:** Every call to Laya is a batch call of up to 64 states, and with 100 people due at once the first pass takes two calls. People keep their current activity until their answer arrives.

- **CAP-4**
  - **intent:** The viewer can observe without changing the world: pause, resume, step one tick, change speed, and see day and night.
  - **success:** Pause freezes movement and ticks. Step while paused runs exactly one tick. 2× and 5× shorten real time per tick.

- **CAP-5**
  - **intent:** The viewer can click a person and see their needs, current activity and the Laya decision behind it.
  - **success:** The inspector shows the exact state text sent, a probability for each of the seven options, the chosen option, the decision latency, and the last ten decisions, as in the mocks.

- **CAP-6**
  - **intent:** The viewer can see Laya's speed as live numbers, and the town keeps running if Laya misbehaves.
  - **success:** The top bar shows decisions per second, batch size and last batch milliseconds from real measurements. When Laya fails mid-run, people keep their activity and a one-line message appears until the next good batch.

## Constraints

- The simulation runs on the Java server and calls Laya once per tick. The browser only renders. This keeps the batch call, which is the showcase, in one place.
- Laya is reached at a configurable base URL (default `http://laya-serve:8002` in Docker Compose, `http://localhost:8002` otherwise). The endpoint is `POST /v1/systemone/batch`, at most 64 states per call. The choice question needs an `instructions` field. Details in `stack.md`.
- `langchain4j-typesafe` 1.21.0-beta31 has no batch call, so all decisions go over plain HTTP. The app does not use LangChain4j.
- Decision state text names needs only. Mentioning the current activity or daytime makes Laya answer "relax" for everyone; the time is mentioned only at night. Text stays short because Laya reads about 512 tokens.
- Speed depends on the host: CPU Laya needs about 45 s for 64 states, CUDA about 3 s. The metric tiles always show the real numbers, and nothing in the app fakes or simulates Laya's answers.
- One command, `docker compose up --build`, starts Laya (built from its pinned upstream commit) and the app.
- Abstention, if shown, uses `answer_confidence` (max probability), never the entropy-based `confidence`.
- Java 17 or later (JDK 18 on the author's machine), Maven, Spring Boot, a static HTML and JS front end, server push to the browser. No database, accounts or persistence.
- `DESIGN.md` and `EXPERIENCE.md` win on any look or behavior question.

## Non-goals

- A player character, score, win state, goals or achievements.
- Placing buildings, giving orders to people or moving them.
- Saved games, accounts, multiplayer, persistence.
- A mobile layout.
- Language-model text generation. Every decision is a Laya choice answer.
- A reusable Laya client library. (That is the other initiative.)

## Success signal

With `laya-serve` running, the author opens the page, clicks Start, and sees a town of 100 moving people. Clicking one person shows a real Laya decision with true probabilities and a latency in milliseconds. The batch tile shows real timings, pause and step work, and at night most of the town goes to sleep.

## Assumptions

- A person re-decides when their current activity finishes or every N simulated seconds, whichever comes first.
- Needs drift over time (hunger rises, energy falls) and activities restore them. Buildings are typed home, work, restaurant, shop, park. Tuning is found by running it.
- Desktop browsers only, Chrome or Edge.
- Docker with the NVIDIA runtime is available for the default Compose setup; `compose.cpu.yaml` is the fallback.

## Open Questions

- Is plain HTTP for everything acceptable, given that LangChain4j has no batch call? v0.1 uses no LangChain4j at all.
- Abstention ("not sure, keeps current activity") and keyboard-only person selection from `EXPERIENCE.md` are not built yet.
