# Dot Matrix redesign — design doc

Date: 2026-09-27
Status: Approved in chat, awaiting written-spec review
Branch: `feature/podcast-tags`
Source designs: canvas "Podcasty — Dot Matrix redesign"
(https://claude.ai/artifact/MD3UZ3697h68ek2n8eGba2), artboards 01 Odběry,
01b Fronta, 01c Nové, 02 Detail, 03 Přehrávač, 04 Ikony — each in a dark
and a light version.

## Purpose

Replace the current Apple-Podcasts-inspired look with the "Dot Matrix"
visual language from the user's designs: Oxanium type in uppercase
letter-spaced labels, outlined rounded-square controls, two-tone line icons
with an accent "pixel", dot-matrix progress, and a light and a dark theme.

This is step 1 of 2. Step 2 (separate spec) adds the player features the
designs show but the app lacks: ±15/30 s skip, previous/next from the
queue, playback speed, sleep timer, a queue button in the player.

Deliberate deviations from the designs (user decisions):
- Odběry keeps the current 4-column cover grid (no names under covers),
  has no "NOVĚ VYŠLO" section, and keeps adding a podcast as a small square
  "+" button bottom-right (not the full-width "PŘIDAT PODCAST" button).
- The player shows only controls that work today (play/pause, seeking);
  the others arrive with step 2.

Out of scope: YouTube and Články content (stay placeholders), a theme
switch inside the app, real audio waveform analysis.

## Theme

Follows the system dark mode (`isSystemInDarkTheme()`); no dynamic color.

| Token | Light | Dark | Use |
|---|---|---|---|
| background | `#F5F4F8` | `#0A0B0D` | screens, nav bar |
| surface | `#FFFFFF` | `#141519` | mini player, now-playing row, player play button |
| text | `#16141C` | `#F2F2F4` | primary text, icon strokes, selected segment fill |
| textMuted | `#6B6875` | `#9A9BA3` | meta, labels, secondary icon strokes |
| textStrong | `#3F3C48` | `#C4C5CC` | unselected chip text |
| outline | `#C9C5D3` | `#3A3B42` | button and chip borders |
| divider | `#DCD9E3` | `#2A2B31` | list dividers, image borders, empty progress dots |
| accent | `#5E2E91` | `#A67CFF` | selected chip, accent pixel, progress, primary button |
| onAccent | `#FFFFFF` | `#0A0B0E` | text on accent |
| waveIdle | `#ECE9F1` | `#17181C` | unlit wave cells |
| waveMuted | `#BDB8CA` | `#4A4B53` | lit-but-unplayed wave cells |
| ringIdle | `#E0DCE7` | `#26272D` | unplayed ring dots |

Exposed as `LocalDotColors` (a `DotColors` data class) plus a matching
Material 3 color scheme (`primary` = accent, `background`, `surface`,
`onSurface` = text, `onSurfaceVariant` = textMuted, `outline`,
`outlineVariant` = divider) so untouched Material components stay
consistent. The status/navigation bar icons switch light/dark with the theme.

## Type

Oxanium variable font (OFL 1.1; `wght` 200–800; covers all Czech
glyphs) bundled as `res/font/oxanium.ttf`, used for every text style.

| Style | Size / weight / tracking | Use |
|---|---|---|
| display | 36 sp / 800 / 2 sp, uppercase | screen titles (PODCASTY, YOUTUBE, ČLÁNKY) |
| showTitle | 34 sp / 800 / 1 sp, uppercase | podcast title in detail |
| title | 18 sp / 700 | episode title in detail |
| rowTitle | 15 sp / 600 | episode titles in lists |
| body | 14 sp / 400, line height 1.45 | descriptions |
| button | 13–14 sp / 700 / 1.5 sp, uppercase | segment, primary button |
| chip | 12 sp / 600 (700 selected) / 1 sp, uppercase | tag chips |
| label | 11 sp / 600 / 1.5 sp, uppercase | section separators, meta, nav labels |
| meta | 10–11 sp / 500–600 / 1 sp, uppercase | "PODCAST · DATUM" lines |

## Icons

Two-tone line icons from artboard 04 (and the nav/header icons of the
other artboards), 24-unit grid (nav icons 32), stroke 2.2–3, round caps
and joins. Each part is drawn in one of three roles: `Primary` (text
color), `Muted` (textMuted), `Accent` (accent, usually a filled 2×2 "pixel").
Implemented as a `DotIcon(icon: DotIconSpec, size, contentDescription)`
composable drawing SVG path data with `PathParser` on a `Canvas`, so the
roles follow the theme. Set: play, pause, stop, previous, next, back15,
forward30, speed, sleep, queue, download, add, back (chevron), close
(chevron down), tag, refresh, drag handle, check, more, nav podcasts, nav
youtube, nav articles. Path data is copied verbatim from the artboards.

## Shared components

- `DotSegmented(options, selected, onSelect)`: bordered 14 dp container,
  4 dp padding and gap, 40 dp segments with 10 dp corners; selected = text
  color fill with background-colored label (700), others transparent muted
  (600).
- `DotChip(label, selected, onClick, onLongClick?)`: 38 dp tall, 14 dp
  horizontal padding, 10 dp corners; unselected 1 dp outline, textStrong;
  selected accent fill, onAccent, check icon. Replaces `TagChipRow`'s chip.
- `DotIconButton(icon, onClick, size = 44 dp, radius = 14 dp, border =
  outline 1 dp | accent 1.5 dp)`: outlined square button, ≥44 dp target.
- `DotPrimaryButton(label, icon)`: 54 dp, 14 dp corners, accent fill,
  onAccent uppercase label.
- `SectionSeparator(left, right)`: label-style text, a 1 dp divider line
  filling the middle, optional right text.
- `DotProgress(fraction, cells)`: row of square cells, 2 dp gap, 1 dp
  corners: played = accent, the current cell = text color, rest = divider.
- `CoverImage(url, size, radius)`: cover with 1 dp divider border and the
  existing podcast-icon fallback.

## Screens

### Podcasts tab (hub)
- Title "PODCASTY" (display). Right of it, label-style, two lines:
  "NN ODBĚRY" and, when a filter is active, "FILTR: <TAG>" (Odběry only).
  Nové shows its refresh as a `DotIconButton` there.
- `DotSegmented` ODBĚRY · FRONTA · NOVÉ below, 20 dp side padding.

### Odběry
- Tag `DotChip` row (VŠE first).
- Current 4-column grid of square covers (14 dp corners, 1 dp border),
  12 dp gaps, no names.
- Small square "+" button bottom-right: 56 dp, 16 dp corners, accent fill,
  add icon in onAccent — floats above the grid like today's FAB.

### Fronta
- `SectionSeparator("4 EPIZODY", "CELKEM 3 H 13 MIN")` (existing
  `queueSummary` numbers, uppercase).
- Tag chips (VŠECHNY first).
- Rows: drag handle (28 dp wide), cover 64 dp / 12 dp corners, then
  meta "PODCAST · 14.09.2026", title (2 lines), and accent "22 MIN" /
  "ZBÝVÁ 1 H 12 MIN" with a 16-cell `DotProgress` beside it for episodes in
  progress; right a 48 dp square play button (accent border when this
  episode is the current one). The current episode's row sits on a surface
  card (16 dp corners); others are separated by divider lines.
- Drag-and-drop, swipe-to-remove with undo, accessibility actions: unchanged.

### Nové
- `SectionSeparator` "■ DNES ——— NEJNOVĚJŠÍ" (accent square before DNES).
- Rows: cover 72 dp / 14 dp corners; meta "■ 27.09.2026 · 41 MIN" (accent
  square only for episodes published today); title 2 lines; actions row:
  download and add-to-queue `DotIconButton`s (44 dp, 12 dp corners), spacer,
  play `DotIconButton` 50 dp. Divider between rows.

### Detail podcastu
- Top row (pinned bar replaced by an in-list row that scrolls with the
  header; a solid bar with the title appears once it scrolls away, as now):
  back `DotIconButton`, spacer, the podcast's tags as selected-style chips,
  tag `DotIconButton`, refresh `DotIconButton`.
- Header: cover 150 dp / 20 dp corners; beside it, bottom-aligned:
  "PODCAST · <FIRST TAG>" (label), title (showTitle, may wrap), author (14 sp,
  muted).
- `DotPrimaryButton` full width: "POSLEDNÍ EPIZODA" / "POKRAČOVAT" /
  "POZASTAVIT" (existing `pickHeaderEpisode` logic).
- `SectionSeparator("EPIZODY", "NEJNOVĚJŠÍ")`.
- Episode rows: date "27.09" bold (accent when the episode is from the
  last 7 days, with a "NOVÁ" accent badge right) and "2026" muted; title
  (title style); 2-line description; actions: queue and download
  `DotIconButton`s, spacer, duration text, play `DotIconButton` 50 dp
  (accent border when new).
- The cover-colour palette header, `androidx.palette` use and
  `HeaderTagChip` are removed.

### Mini player
- Surface card, 1 dp divider border, 18 dp corners, 12 dp side inset.
- Cover 46 dp / 10 dp; title 14 sp 600; meta "PODCAST · 12:48" (current
  position); play/pause 44 dp square with 1.5 dp accent border.
- A 52-cell `DotProgress` under the row.

### Bottom navigation
- Background color, 1 dp top line, three items with the custom nav icons
  (30 dp) and uppercase labels; selected item text color, others muted;
  no pill indicator.

### Player (look only)
- Top: close `DotIconButton` (chevron down), centre label "PŘEHRÁVÁ SE"
  with the small wave glyph.
- 280 dp ring of 56 dots (6 dp) around a 210 dp circular cover: dots up to
  the progress in accent, the current dot text color, the rest ringIdle.
- Title (20 sp 700, 1 line) and podcast (label).
- Wave: 36 columns × 9 rows of square cells; column heights come from a
  deterministic pseudo-random pattern seeded by the episode id (decorative);
  lit cells left of the progress are accent, lit cells right of it
  waveMuted, unlit cells waveIdle. Tap or drag on the wave seeks.
- Elapsed "12:48" and remaining "−31:12".
- Centre 78 dp play/pause button, 24 dp corners, 2 dp accent border,
  surface fill. No other controls in step 1.

### YouTube / Články placeholders
- Display title and the existing "Připravujeme" empty state, restyled.

## Testing

- JVM unit tests: `dotProgressCells` (played/current/rest counts,
  clamping); `ringDots` (count, positions on the circle, colour roles);
  `waveColumns` (deterministic for an id, heights 1..9); `waveSeekFraction`
  (x → fraction, clamped); the "new episode" rule (≤ 7 days) and
  "published today" rule; uppercase meta formatting ("14.09.2026").
- Build, then a manual emulator pass in light and dark mode on every
  screen, plus install on the user's phone.
