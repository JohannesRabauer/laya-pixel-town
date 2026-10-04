# Stack and Laya usage

Spec-authored companion for `spec-pixel-town.md`.

## Runtime

- Java 17+, Maven, Spring Boot (web, no JPA). One module.
- Static front end under `src/main/resources/static`: one `index.html`, plain JS, one `<canvas>` for the town. No build step.
- Server to browser: Server-Sent Events (or WebSocket) streaming a world snapshot per tick: people (id, name, x, y, activity, frame), time of day, metrics. Browser to server: REST for start, pause, resume, step, speed, select person, and a read of one person's inspector data.

## Simulation

- Fixed grid town, typed places (home, work, restaurant, shop, park). Layout per `ux-pixel-town/.working/key-town-view.html`.
- A tick advances simulated time, drifts needs, moves people toward their destination, and collects people due for a decision.
- Each due person becomes one Laya state string and the batch is sent in chunks of at most 64.
- The chosen activity maps to a place; the person walks there and stays for a duration.

## Laya call

`POST {base}/v1/systemone/batch`

```json
{
  "states": ["Maya is very hungry. Maya has little money. Other needs are fine."],
  "questions": {
    "next": {
      "type": "choice",
      "instructions": "Choose what this person most needs to do right now, given their needs.",
      "criteria": {
        "sleep": "go to bed and sleep because they are exhausted",
        "eat": "eat a meal because they are hungry",
        "work": "go to work to earn money",
        "socialize": "meet friends because they are lonely",
        "relax": "relax and have fun because they are bored",
        "shop": "go shopping",
        "wander": "stroll around aimlessly"
      }
    }
  }
}
```

The field is `instructions`; `question` is rejected with 422. Response: `{results:[{answers:{next:{choice, probabilities, answer_confidence, ...}}, usage}], total_usage}` in input order. Verified against laya-serve 0.3.26 on 2026-10-04. Treat all Laya-only fields as optional. Errors are `{"detail": "..."}`; 503 means busy, with `Retry-After`.

Measurements shown in the UI come from wall-clock around the HTTP call, plus the `X-Inference-Time-Ms` header when present.

## Measured speed (RTX 3060 laptop, `english` checkpoint)

| Host | 64 states per call |
|---|---|
| CPU container | about 46 s |
| CUDA container (torch 2.14, cu130) | about 2.7 s; about 70 ms for a small warm batch |

Another GPU workload (an Ollama model) can stall CUDA completely. torch 2.14 wheels exist on the `cu130` index, not `cu128`.

## State text

Needs only, in plain sentences, strongest need first: "Maya has no money left and needs to earn some. Maya feels lonely. Other needs are fine." Time of day appears only at night. The current activity is never mentioned.

## Tests that matter

- A contract test against the real laya-serve (skipped when it is not reachable): one batch of a few states returns one answer per state in order.
- A simulation test with a fake Laya: pause stops ticks, step runs exactly one, a failing Laya leaves activities unchanged.
