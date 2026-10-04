---
name: Pixel Town
status: draft
created: 2026-10-04
updated: 2026-10-04
sources: []
---

# Pixel Town — Experience Spine

> Single-surface web demo, desktop-first. The player is an observer, not a character. The simulation exists to show Laya making many typed decisions quickly. Paired with `DESIGN.md`. Items marked `[ASSUMPTION]` are drafted without confirmation.

## Foundation

Web application, desktop browsers first, in a Java service with a browser canvas. [ASSUMPTION: desktop-first; phones are not a target for v1.] No UI system named. Visual identity is in `DESIGN.md`. The town simulation runs on the server (Java), where each tick sends one batch of decision requests to Laya. The browser draws the result. [ASSUMPTION: server-side simulation, because the batch call is the thing being showcased.]

There are no accounts, no saved games, and no direct control over people. The only player powers are: set parameters at start, pause, change speed, select a person, and inspect.

## Information Architecture

| Surface | Reached from | Purpose |
|---|---|---|
| Start screen | Page load | Set a few parameters and begin |
| Town view | Start | Watch the simulation |
| Inspector panel | Click a person in Town view | See one person's needs, current action and the Laya decision behind it |
| Top bar | Always on Town view | Pause, speed, time, Laya metrics |

Start screen parameters, the minimum set. [ASSUMPTION]:

| Parameter | Default | Meaning |
|---|---|---|
| Population | 100 | Number of people, 10 to 300 |
| Decision interval | 1 s of sim time | How often each person re-decides |
| Random seed | random | For repeatable runs |
| Laya server address | `http://localhost:8002` | Where to reach `laya-serve` |

There is one way back: a "New town" button in the top bar returns to the start screen and discards the running simulation after a confirmation.

## Voice and Tone

Short, plain, calm. The interface describes what happens and never cheers it on.

| Do | Don't |
|---|---|
| "Maya is eating." | "Maya is having a yummy lunch!" |
| "Decided in 3 ms." | "Lightning-fast AI decision!" |
| "Laya is not reachable at http://localhost:8002." | "Oops, something went wrong." |
| "Paused." | "Simulation halted." |

People have short first names, shown in the inspector only. [ASSUMPTION: names are assigned from a fixed list.]

## Component Patterns

Behavioral. Visual specs live in `DESIGN.md.Components`.

| Component | Use | Behavioral rules |
|---|---|---|
| Person sprite | Town view | Moves toward the place for its current activity. Click selects it. Hit area is at least 16×16 screen pixels even though the sprite is smaller. |
| Selected ring | Town view | Follows the selected person. Pulses once when that person gets a new decision. Remains when paused. |
| Pause / Play | Top bar | Toggles. Space bar does the same. Pausing stops ticks. It does not clear the selection. |
| Step | Top bar | Enabled only when paused. Runs exactly one tick (one Laya batch), then stays paused. |
| Speed | Top bar | 1×, 2×, 5×. A faster speed shortens the real time per tick. It does not change the decisions asked per person. |
| Metric tiles | Top bar | Decisions per second, batch size, last batch milliseconds. Update after every batch. |
| Inspector | Right overlay | Opens on person click, closes on the close button, Escape, or a click on empty ground. Switches person when another person is clicked. |
| Need bars | Inspector | Five needs, 0 to 100, updating each tick. |
| Decision card | Inspector | Shows the last decision: state text, options with probabilities, the chosen option, latency. |
| Decision history | Inspector | The last 10 decisions of this person, newest first. A row shows time, chosen activity, and probability of the chosen option. Clicking a row loads that decision into the Decision card. [ASSUMPTION] |

### The decision, as shown to the viewer

For each person and each tick, the simulation builds a text state and asks Laya one choice question. [ASSUMPTION: needs and activities below, to be refined with the Laya integration.]

- **Needs:** hunger, energy, social, fun, money (0 to 100).
- **Activities (the options):** sleep, eat, work, socialize, relax, shop, wander.
- **State text** (example): `Maya, 14:20. Hunger high (82). Energy medium (55). Social low (20). Fun medium (48). Money low (15). At the park. Currently: relaxing for 25 minutes.`
- **Question:** "What should Maya do next?" with the seven options.

The inspector shows the state text exactly as sent, so the viewer can see why the decision is plausible.

## State Patterns

| State | Surface | Treatment |
|---|---|---|
| Start, ready | Start screen | Parameters at defaults, "Start" enabled. |
| Laya unreachable at start | Start screen | Message `Laya is not reachable at <address>.` below the address field. "Start" disabled until the check passes. A "Check again" button. |
| Running | Town view | People move, metrics update. |
| Paused | Town view | People freeze in place. Top bar shows `Paused.` Metrics hold their last values. |
| Nothing selected | Town view | Inspector is closed. A one-line hint appears in the top bar on first run only: `Click a person to see how they decide.` |
| Person selected | Town view | Ring on the person, inspector open. |
| Person indoors | Town view | The sprite is hidden. The selected ring moves to the building outline. The inspector continues to work. |
| Laya slow or failing mid-run | Town view | The simulation continues: people keep their current activity. The metric tile for batch milliseconds turns to `…`, and a one-line message appears in the top bar: `Laya did not answer. People keep doing what they were doing.` It clears on the next successful batch. |
| Abstain (Laya not sure) | Inspector | Optional. If the best option's probability is below the abstain threshold, the person keeps their current activity and the card says `Not sure — keeps current activity.` [ASSUMPTION: uses Laya's answer confidence; threshold is a start-screen advanced option.] |
| Night | Town view | Night tint over the town. Most people choose sleep. This should happen on its own as a result of the decisions. |
| New town | Top bar | Confirmation: `Discard this town and start over?` Yes / No. |

## Interaction Primitives

- Click a person to select. Click empty ground to deselect.
- Space toggles pause.
- Escape closes the inspector.
- Mouse wheel zooms the town in discrete steps (1×, 2×, 3×). Drag pans. Zoom and pan are for looking only. [ASSUMPTION: town may be larger than the viewport at 300 people.]
- Hovering a person shows a small label with their name and current activity. Hover works in both running and paused states.
- **Banned:** drag-to-move a person, placing buildings, giving orders, scores, win states, achievements, sound that cannot be muted. The player never changes the simulation except by pausing it.

## Accessibility Floor

Behavioral. Visual contrast lives in `DESIGN.md`.

- Every control is reachable by keyboard in reading order. Visible focus ring on every control.
- The inspector's content is real text, not drawn on the canvas, so a screen reader can read it. When the selected person's decision changes, it is announced politely: `Maya chose eat.`
- People can be selected without a mouse: Tab moves into the town, Left/Right arrows step through people sorted by name, Enter selects. [ASSUMPTION]
- Activities are never distinguished by colour alone: each probability bar has its text label, and the person's current activity is named in the hover label.
- Reduce Motion: sprite walking slows to 1 frame per tick (no interpolation), and the selection pulse is replaced by a static ring.
- Tap/click targets for buttons are at least 32px.

## Inspiration & Anti-patterns

- **Lifted from The Sims, but as an observer:** needs bars that explain behaviour. We keep needs and drop everything else (building, controlling).
- **Lifted from ant-farm and "god game" toys:** watching is the product. There is no goal.
- **Lifted from Flappy Bird AI demos:** the reason to watch is to see the decision as it happens, in real time.
- **Rejected: a player character or a score.** This is a showcase of decision-making, not a game.
- **Rejected: a generic AI dashboard.** The numbers are there to support the town, not to replace it.

## Key Flows

### Flow 1: First run (Jonas, a Java developer at his desk, curious what Laya can do)

1. Jonas opens the page. A slow, non-interactive view of the town drifts behind the start panel.
2. He leaves the defaults (100 people) and clicks Start.
3. The town fills with moving people. The decisions-per-second tile begins to climb.
4. He sees the batch tile show a few milliseconds.
5. He clicks a person walking toward a building.
6. **Climax:** the inspector shows a decision card with the state text and the probabilities for seven activities, and the card says `Decided in 3 ms.` He sees why the person chose to eat: hunger is high.

Failure: Laya is not running. The start screen says so and keeps Start disabled until the check passes.

### Flow 2: Pause and compare (Jonas, a minute later)

1. He presses Space. The town freezes. The selected ring stays.
2. He clicks a second person. The inspector switches to them.
3. He reads their state text and sees low energy and high hunger, and that the options for sleep and eat are close.
4. He clicks Step. One tick runs.
5. **Climax:** he watches both people's decision cards update with new probabilities, and the batch tile shows one batch for all 100 people at once.

### Flow 3: Night (Jonas, letting it run)

1. He sets speed to 5× and lets the day pass.
2. The night tint comes in. Over the next minute the sleep activity rises across the town without anyone telling it to.
3. **Climax:** nearly every person is indoors; he clicks the one person still wandering and sees that their energy is high and their social need is low, so Laya sent them to the park.

## Open Questions

- [ASSUMPTION] Exact set of needs and activities, and whether buildings are typed (home, workplace, shop, park, restaurant). Needs the Laya integration spike.
- [ASSUMPTION] Whether each person decides at a fixed interval or only when their current activity finishes. This changes the batch size per tick, and so the headline metric.
- [ASSUMPTION] Simulation on the server, rendering in the browser. Confirm when architecture is chosen.
- Should the inspector also show Laya's `answer_confidence` next to the probabilities? Draft says yes, as a small muted number.
