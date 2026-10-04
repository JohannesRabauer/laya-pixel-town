---
name: Pixel Town
description: A tiny-sprite pixel-art town seen from above, with a calm dark control surface around it, so that a hundred small lives and one inspector panel stay readable.
status: draft
created: 2026-10-04
updated: 2026-10-04
colors:
  town-grass: '#7DB35A'
  town-path: '#D9C7A0'
  town-road: '#8E8E96'
  town-water: '#4F9BD6'
  town-wall: '#C9805A'
  town-roof: '#A64B4B'
  town-night-tint: '#1B2347'
  ui-bg: '#14161F'
  ui-panel: '#1E212E'
  ui-panel-raised: '#272B3B'
  ui-border: '#3A3F55'
  ui-text: '#E8E9F0'
  ui-text-muted: '#9A9DB3'
  accent-laya: '#FFC857'
  accent-select: '#FFFFFF'
  need-hunger: '#E8743B'
  need-energy: '#5B8DEF'
  need-social: '#E86FA8'
  need-fun: '#B58CF0'
  need-money: '#5CC38A'
  activity-sleep: '#5B8DEF'
  activity-eat: '#E8743B'
  activity-work: '#8E8E96'
  activity-socialize: '#E86FA8'
  activity-relax: '#B58CF0'
  activity-shop: '#5CC38A'
  activity-wander: '#D9C7A0'
typography:
  ui-heading:
    fontFamily: 'Silkscreen, monospace'
    fontSize: 14px
    fontWeight: 400
    letterSpacing: 0.04em
  ui-body:
    fontFamily: 'Inter, system-ui, sans-serif'
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
  ui-mono:
    fontFamily: 'JetBrains Mono, ui-monospace, monospace'
    fontSize: 12px
    fontWeight: 400
    lineHeight: 18px
  ui-metric:
    fontFamily: 'Silkscreen, monospace'
    fontSize: 20px
    fontWeight: 400
rounded:
  none: 0px
  sm: 2px
  md: 4px
  full: 9999px
spacing:
  '1': 4px
  '2': 8px
  '3': 12px
  '4': 16px
  '6': 24px
  tile: 16px
  gutter: 16px
components:
  sprite-person:
    width: 6px
    height: 10px
    scale: 2
  sprite-selected-ring:
    color: '{colors.accent-select}'
    rounded: '{rounded.full}'
  panel:
    backgroundColor: '{colors.ui-panel}'
    textColor: '{colors.ui-text}'
    rounded: '{rounded.md}'
    padding: '{spacing.4}'
  button-primary:
    backgroundColor: '{colors.accent-laya}'
    textColor: '{colors.ui-bg}'
    rounded: '{rounded.sm}'
    padding: '{spacing.3}'
  button-secondary:
    backgroundColor: '{colors.ui-panel-raised}'
    textColor: '{colors.ui-text}'
    rounded: '{rounded.sm}'
    padding: '{spacing.3}'
  need-bar:
    height: 8px
    backgroundColor: '{colors.ui-panel-raised}'
    rounded: '{rounded.none}'
  probability-bar:
    height: 14px
    backgroundColor: '{colors.ui-panel-raised}'
    rounded: '{rounded.none}'
  metric-tile:
    backgroundColor: '{colors.ui-panel}'
    textColor: '{colors.accent-laya}'
    rounded: '{rounded.md}'
---

# Pixel Town — Design Spine

## Brand & Style

A diorama, not a dashboard. The town is rendered as chunky, flat pixel art seen from straight above, with no outlines and no gradients. Each person is a tiny sprite of about 6×10 source pixels, drawn at 2× scale, so that a hundred of them move at once and still read as individuals. The surrounding interface is a quiet dark "control room" that stays out of the way: the town is the star, and the one warm accent colour belongs to Laya.

The feeling is cozy and slightly scientific. Think of a tiny world under glass that someone is gently observing. [ASSUMPTION: cozy-diorama look, chosen over a cold techy look. Confirm or change.]

The accent colour `{colors.accent-laya}` is reserved for anything that Laya does: decision metrics, the batch latency, the chosen option in the inspector. If it is amber, Laya made it happen.

## Colors

- **Town palette** (`town-*`): muted, slightly saturated daylight colours. Grass, paths, road, water and buildings stay in the mid-tones so that the sprites contrast against them.
- **Night tint** `{colors.town-night-tint}`: a multiply overlay at about 55% over the whole town during night hours. Sprites are not tinted, so the people stay visible at night. [ASSUMPTION: day/night cycle exists.]
- **UI palette** (`ui-*`): a near-black blue surface with slightly lighter panels. Text is off-white, and muted text is used for labels and units.
- **Need colours** (`need-*`): one fixed hue per need. They appear on the need bars in the inspector and nowhere else, so they never compete with the activity colours.
- **Activity colours** (`activity-*`): one colour per activity, shown as a 2px ring colour under each sprite and as the segment colour in the probability bars. Several activity colours deliberately reuse a need hue (sleep/energy, eat/hunger, socialize/social, relax/fun, shop/money) so the link "need → activity" is visible without a legend. Wander and work have no matching need.
- **Selection** `{colors.accent-select}`: pure white, only for the selected-person ring.

Never use the Laya accent for decoration, and never use red or green to mean good or bad: the simulation has no good or bad.

## Typography

- `{typography.ui-heading}`: a pixel font (Silkscreen) for panel titles and the start-screen title only.
- `{typography.ui-body}`: a plain sans-serif for labels and parameter descriptions.
- `{typography.ui-mono}`: monospace for the exact text state sent to Laya, the Laya answer, and any numbers that should line up.
- `{typography.ui-metric}`: the pixel font at a larger size for the headline metrics (decisions per second, batch milliseconds).

Pixel fonts are for short strings only. Any string longer than about three words uses the sans-serif.

## Layout & Spacing

The simulation screen is a full-viewport canvas with the control surface laid over it:

- **Town canvas** fills the viewport.
- **Top bar**, 48px: title, pause/play, speed, time of day, population, and the Laya metric tiles on the right.
- **Inspector panel**, 360px wide, docked to the right edge and shown only when a person is selected. It overlays the canvas rather than shrinking it. [ASSUMPTION: right-docked overlay.]
- The start screen is a centred panel of about 480px over a slowly drifting, non-interactive view of the town.

Spacing is on a 4px base. The town grid is 16px tiles, and sprites are snapped to whole screen pixels so that they never blur. Canvas scaling uses nearest-neighbour only. Gutters are 16px.

## Elevation & Depth

Almost flat. The inspector and the start panel separate from the town with a 1px `{colors.ui-border}` border and no soft shadow. The top bar sits on `{colors.ui-bg}` at 90% opacity. The only "depth" in the town is sprite order: people lower on the screen draw in front of people above them.

## Shapes

Hard pixel corners everywhere in the town. UI panels use `{rounded.md}` (4px) and buttons `{rounded.sm}` (2px). The selected-person ring is a circle (`{rounded.full}`). Bars (need bars, probability bars) have square ends so that they read as pixel gauges.

## Components

- **Person sprite** (`sprite-person`): 6×10 source pixels at 2×. Body colour is one of about 8 skin/clothing combinations assigned at random. Two walk frames and one idle frame are enough. Activities that occur indoors hide the sprite. Activities that occur outdoors are shown with a 2px activity-colour ring under the feet.
- **Selected ring** (`sprite-selected-ring`): a 1px white ring, 14px across, around the selected person. It pulses once per Laya decision for that person. [ASSUMPTION: pulse marks the moment a decision happens.]
- **Panel** (`panel`): dark panel with a pixel-font title row.
- **Buttons**: `button-primary` is amber (Start, Resume). `button-secondary` is the muted panel colour (Pause, Step, speed).
- **Need bar** (`need-bar`): 8px tall, filled in the need's colour, with the need name and value on the same row. Value 0–100.
- **Probability bar** (`probability-bar`): one horizontal bar per activity option, 14px tall, the filled width equal to the probability. The chosen option is outlined in `{colors.accent-laya}` and its label is bold.
- **Metric tile** (`metric-tile`): a number in `{typography.ui-metric}` coloured `{colors.accent-laya}`, with a muted unit below it. Used for decisions per second, batch size and batch milliseconds.
- **State text box**: monospace text on `{colors.ui-panel-raised}`, showing the exact text that was sent to Laya. Long text wraps. It is never truncated.

## Do's and Don'ts

**Do**
- Keep sprites crisp: whole-pixel positions, nearest-neighbour scaling.
- Let the town fill the screen. Overlay panels, don't shrink the canvas.
- Use the amber accent for Laya-related information only.
- Show real numbers (probabilities, milliseconds) in monospace.

**Don't**
- Don't use gradients, blur, or soft shadows in the town.
- Don't add outlines to sprites. At 2× they become noise at 100 people.
- Don't use red/green for good/bad.
- Don't animate panels with long transitions. Use at most a 120ms fade.
