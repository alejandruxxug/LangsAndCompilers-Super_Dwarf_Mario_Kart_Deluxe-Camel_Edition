# Super_Dwarf_Mario_Kart_Deluxe-Camel_Edition

Java 25 + JavaFX desktop music player for a Data Structures course at Universidad EIA.
Graded on **hand-written data structures and OOP quality**; carries a Mario-Kart-styled
beat-synced rhythm game as its showpiece feature.

---

## 1. The name is deliberate — do not "clean it up"

The display name is **exactly** `Super_Dwarf_Mario_Kart_Deluxe-Camel_Edition`, underscores
and hyphen included. The ROM-filename styling is intentional and pairs with the 8-bit font.
Never convert it to spaces or title case.

It is illegal as a Java package (hyphens) and far too long for tight UI, so **three** values
live in `AppConfig` and are referenced everywhere instead of hardcoded strings:

| Constant | Value | Used for |
|---|---|---|
| `APP_NAME` | `Super_Dwarf_Mario_Kart_Deluxe-Camel_Edition` | window title, README heading, about box, fullscreen title screen |
| `APP_NAME_SHORT` | `SDMK_Deluxe` | mini player, tight labels |
| `APP_DIR` | `.superdwarfkart` | config/cache dir under `$HOME` |

- **Package root:** `com.eia.superdwarfkart`
- **Maven:** `groupId` `com.eia`, `artifactId` `super-dwarf-mario-kart-deluxe-camel-edition`
- **`APP_NAME` must never reach the mini player.** At 44 characters in Press Start 2P it is
  several times the width of that window. This overflow is invisible until the window is
  actually rendered — watch for it.
- Fullscreen mode opens on a Mario-Kart-style **title screen** showing `APP_NAME` in the
  8-bit font. The name is the joke; it deserves the screen real estate.

---

## 2. Non-negotiable ground rules

1. **Everything is in English.** Class names, methods, fields, locals, enum constants,
   comments, Javadoc, log messages, exception messages, UI labels, buttons, tooltips, README.
   **No Spanish anywhere in the codebase**, including user-visible strings. The assignment
   brief is in Spanish; the software is not. If you catch yourself writing `cancion`,
   `siguiente`, or `reproducir`, rename it before moving on.
   *The single tolerated exception:* Spanish **filename-matching patterns** in `AssetRegistry`
   (`estrella`, `moneda`, `fondo`, `personaje`) — as input patterns to match against, never
   as identifiers.
2. **The three playback structures are written by hand.** No `java.util.LinkedList`,
   `ArrayDeque`, `Queue`, `TreeMap`, `TreeSet`, or any library collection may back them.
   Generics (`<T>`) required — graded bonus. Every public method carries a Javadoc line
   stating its time complexity. Three people defend these orally.
3. **Strict separation of logic and presentation.** Nothing in `ds/`, `model/`, `playback/`,
   `audio/`, or `analysis/` may import `javafx.*`. The UI observes; it does not own state.
   (`javafx.util.Duration` included — `model/` uses `java.time.Duration`.)
4. **Never block the audio thread.** The PCM callback runs on the playback thread: copy,
   compute, publish to atomics, return. No `Platform.runLater` per audio block — the UI polls
   levels from an `AnimationTimer`.
5. **The app launches and is usable with zero assets present.** Missing sprites draw a
   labeled magenta placeholder and log a warning once. Never throw, never block startup.
   Art arrives incrementally from another machine.
6. **Verify every third-party coordinate and API signature against what actually resolves**
   before writing code against it. Do not trust names in the brief — check the resolved jar.
7. **No hardcoded colors outside `mood/`.** Every color — CSS, `gc.setFill(...)`, meter
   gradients, road stripes, tree edges — resolves through the active `Mood` by **role name**,
   from M0 onward. A literal `Color.web("#1a1a2e")` or `-fx-background-color: #222;` anywhere
   in `ui/`, `game/`, or `ui/visualizer/` is a bug. This costs nothing while building and is
   the entire reason M11 is a feature instead of a 400-site find-and-replace.
   **Debt, measured 2026-08-12:** `css/app.css` carries ~60 hex literals and
   `assets/SpriteSheet` two (the magenta placeholder, which stays literal by design — it is a
   diagnostic, not a theme color). Everything else predates this rule and must be routed
   through roles **before M11 starts**; retrofitting theming across a finished UI costs
   several times what the feature does. If a milestone comes back with a new hex literal in
   it, fix it that day.
8. **Pixel art is never smoothed.** `gc.setImageSmoothing(false)` on every `GraphicsContext`,
   `setSmooth(false)` on every `ImageView`. JavaFX interpolates by default, which turns
   hand-drawn 8-bit sprites into mush at any scale other than 1:1. Scale by **integer**
   factors only (2×, 3×, 4×) — never fractional.

---

## 3. Build and run

```bash
./mvnw clean compile          # Maven is NOT on PATH — always use the wrapper
./mvnw javafx:run             # launch the app
./mvnw test                   # JUnit
```

Three run switches, all forwarded to the forked JVM by the `javafx-maven-plugin` `<options>`
block (a bare `-D` on the Maven command line does **not** reach the app):

| Switch | Effect |
|---|---|
| `-Dsdmk.smokeTest=true` | Launch, print what was verified, auto-close after ~2 s. Use this to check a launch without leaving a window on screen. |
| `-Dsdmk.home=/tmp/demo` | Use a scratch profile instead of `~/.superdwarfkart`. Seed a `library.json` there to demo against fake data without touching the user's real library. |
| `-Dsdmk.screenshot=out.png` | During a smoke test, snapshot the window to a PNG. This is the only way to check layout without a person watching. |

```bash
# Full verification run against seeded data, leaving a screenshot behind
./mvnw javafx:run -Dsdmk.smokeTest=true -Dsdmk.home=/tmp/sdmk-demo -Dsdmk.screenshot=/tmp/shot.png
```

**Environment as measured on this machine:**

- Maven comes from `./mvnw` (wrapper, Apache Maven 3.8.5). There is no `mvn` on `PATH`.
- **Java 25**, not the Java 17 named in the original brief — the user overrode this
  explicitly: *use the latest Java*. Default JDK is **25.0.2** (JDK 21 and 24 also installed).
  The build sets `<release>25</release>`. Do not "restore" 17.
- Dependency versions are the latest that were verified to actually resolve on 2026-08-11:

  | Dependency | Version | Note |
  |---|---|---|
  | `org.openjfx:javafx-*` | 26.0.2 | class-file major 68 (Java 24), so it runs on JDK 25 — verified by inspecting the `mac-aarch64` classifier jar |
  | `com.googlecode.soundlibs:mp3spi` | 1.9.5.4 | pulls `jlayer` 1.0.1.4 + `tritonus-share` 0.3.7.4 |
  | `com.fasterxml.jackson.core:jackson-databind` | 2.22.1 | |
  | `org.junit.jupiter:junit-jupiter` | 6.1.3 | JUnit 6, run by surefire 3.5.6 |

  JavaFX publishes an **empty stub jar** for the main artifact; the real classes live in the
  platform-classified jar (`-mac-aarch64`). Inspect that one when checking anything.
- **No `module-info.java`.** The project runs on the classpath. `mp3spi`/`jlayer`/
  `tritonus-share` are legacy non-modular jars whose `javax.sound.sampled` SPI registration
  is painful under JPMS. This is a deliberate choice, not an oversight — do not reintroduce
  a module descriptor.
- FXGL has been removed. The runner is a plain JavaFX `Canvas` + one `AnimationTimer`.
  Do not reach for FXGL; it expects to own the application lifecycle.

---

## 3b. The interface is 8-bit everywhere — do not reintroduce JavaFX defaults

**Every control is styled from scratch in `css/app.css`, and the whole interface runs in
Press Start 2P.** No default JavaFX chrome, no rounded corners, no gradients on controls, no
drop shadows. Buttons, text fields, combo boxes, check boxes, sliders, spinners, scrollbars,
tooltips and dialogs are all rebuilt as hard-edged beveled blocks.

- **No third-party UI libraries.** ControlsFX, FormsFX, ValidatorFX, BootstrapFX, TilesFX and
  FXGL were all removed in M0 and must not come back — their widgets carry their own look.
- Bevels are drawn with four-sided `-fx-border-color` (light top-left, dark bottom-right);
  pressed states invert the bevel and translate by 1px so a button reads as a physical key.
- Arrows and check marks are `-fx-shape` pixel paths, not the default smooth triangles. The
  check mark is a **filled square**, which is the readable 8-bit convention at this size.
- **Sizing rule:** Press Start 2P is fixed-width at roughly one em per glyph, so an n-character
  string is about `n × font-size` pixels wide — several times wider than a proportional font.
  Body text sits at **7–9px**, headings at 11–14px, and sizes are **whole pixels** (fractional
  sizes make a pixel font blurry). Any new control needs an explicit width in characters, and
  any new label needs checking against the space it has. `MAIN_WIDTH` is 1440 for this reason.
- Long text must be shortened rather than wrapped: see `LibraryView.ellipsize` /
  `ellipsizeStart`, and put the full value in a tooltip.
- **Verify visually.** Layout overflow in this font is invisible to unit tests — take a
  screenshot with `-Dsdmk.screenshot=` after any UI change.

### Frameless windows are a design decision

**Every window this application opens is undecorated.** No operating system title bar, no
native buttons, no platform styling — so a window looks identical on macOS and Windows and
neither breaks the theme. `ui/PixelDialog` is the shell: it draws the title bar, the close
button and the border, and it supplies what the system chrome otherwise would — **dragging by
the title bar, a close button, Escape to cancel and Enter to accept**. Anything that needs a
window goes through it; `javafx.scene.control.Alert` and `Dialog` are not used anywhere.

This matches the mini player the brief specifies (`StageStyle.TRANSPARENT`, no title bar,
custom hide/quit/expand buttons, draggable by the top bar) — the mini player is the same idea
applied to the companion window, so M8 should build on `PixelDialog`'s drag handling rather
than reinvent it.

The **native file chooser is the one exception** — it belongs to the operating system and
cannot be styled.

### Sprites in the interface

`assets/SpriteSheet` loads a horizontal strip and addresses frames by viewport rectangle, so an
animation costs one image and switching frames just moves a rectangle. A missing sheet returns a
**magenta placeholder** and logs once — never an exception (ground rule 5).

`ui/SpriteView` animates them, and **all animated sprites share one `AnimationTimer`** held
statically. One timer ticking a counter is far cheaper than a timeline per sprite, which matters
because sprites appear once per visible table row. Each view takes a phase offset so a column
shimmers instead of beating in lockstep. A view that leaves the screen must call
`stopAnimating()` — recycled table cells do this when they go empty.

The rating is shown by `ui/RatingDisplay`: the ten-block meter (`RatingBar`) with the spinning
star from `Star.png` beside it. The meter's colour ramp is **red (<40) → yellow (40–69) → green
(≥70)**, so the colour alone says whether a song is barely liked or a favourite. The star turns
only for a rated song.

**Cover art is square and centre-cropped.** The frame spans the full width of the details column
inside its padding, and its height is *bound* to that width, because album art is square. The
image is cropped to its centre square (`LibraryView.centeredSquare`) rather than letterboxed.

Two traps here, both hit once already:
- The image must stop short of the frame's edge (`COVER_INSET`), or it paints straight over the
  border and the frame looks like it vanished.
- A full-width cover leaves little room beneath it, so the details column is a **`ScrollPane`** —
  otherwise the metadata under the cover is silently clipped on a short window. The panel styling
  lives on the scroll pane and the content inside it is transparent.

### Do not do expensive work on continuous input

A slider drag fires per pixel. The rating slider originally committed on every event, which meant
a full table rebuild **and a JSON write to disk per pixel of travel** — it visibly lagged. The rule:
while a control is being dragged, update only the cheap readout; commit on
`valueChangingProperty` going false (plus an immediate commit when the value changes while *not*
dragging, which covers track clicks and arrow keys). `RatingBar` additionally no-ops when the
repaint would look identical, since rewriting style classes forces a CSS pass per block.

### Two windows, one shared state

`AppState` is a single observable holding **current song, playback mode, selected racer, speed
class, and active mood**. Both windows bind to it — changing the racer in fullscreen must
immediately change the sprite riding the disk in the mini player, and switching mood must
restyle both at once. Same binding path for all five.

**Mini / companion mode** (`StageStyle.TRANSPARENT`, no title bar):
- Spinning disk with the racer sprite composited on top, "driving" while audio plays and
  freezing when paused — **this is the play/pause indicator**.
- Behind it an Apple-Music-style strip: cover, title, artist, back / play-pause / forward.
- Custom **hide, quit, expand** buttons — no OS chrome exists, so the window must supply them.
- Draggable by the top bar (`setOnMousePressed` / `setOnMouseDragged` with offset tracking),
  built on `PixelDialog`'s drag handling rather than reinvented.

**Fullscreen mode:**
- **Top** — playback-mode selector + `ComplexityPanel` for the active mode.
- **Left** — current song, cover, progress bar bound to real playback time, and **the structure
  visualizer for the active mode**. This *is* the queue view: upcoming songs are shown
  structurally, not as a flat list, and it swaps automatically when the mode changes.
- **Right** (wider, not an even split) — the 3-lane runner flanked by the L and R meter bars.
- **Side rail** — Library, Favorites, History, Racer Select, **Moods**, Settings.
- **Presentation Mode** (function key) — see §7.

## 4. Package layout

```
com.eia.superdwarfkart
├── app/          App (JavaFX bootstrap), AppState, AppConfig
├── model/        Song, Genre, ModeId, Racer
├── ds/           CircularDoublyLinkedList<T>, SimpleQueue<T>,
│                 BinarySearchTree<T>                        ← graded core
├── playback/     PlaybackMode (interface), AbstractPlaybackMode,
│                 ShuffleMode, ArrivalOrderMode, AlphabeticalMode, Player
├── audio/        AudioSource (interface), LocalFileAudioSource,
│                 PcmListener, Levels, AudioException
├── analysis/     BeatmapAnalyzer, Beatmap, BeatmapCache, OnsetDetector
├── game/         RunnerGame, Course, Lane, Entity, Obstacle, Coin,
│                 Star, ScoreKeeper, SpeedClass
├── persistence/  Repository<T> (interface), LibraryRepository, ScoreRepository
├── assets/       AssetRegistry, SpriteSheet, SpriteAnimation
├── mood/         Mood, Palette, PaletteRole (enum), GbaColor, MoodLayer,
│                 GradientLayer, ImageLayer, ProceduralLayer,
│                 PixelTile, MoodRepository, PaletteImporter,
│                 ImageQuantizer, MoodValidator              ← M11
└── ui/           MiniPlayerView, FullscreenView, LibraryView,
                  RacerSelectView, LevelMeterView, ComplexityPanel,
                  MoodCustomizerView, PixelEditorView, MoodOverlayRenderer
    └── visualizer/  CircuitView, StartingGridView, BstView, OperationCounter
```

**Three naming collisions to avoid:** never name the model class `Character`
(`java.lang.Character`), the sprite class `Animation` (`javafx.animation.Animation`), or the
tree view `TreeView` (`javafx.scene.control.TreeView`). Use **`Racer`**, **`SpriteAnimation`**,
and **`BstView`**.

---

## 5. The graded core

### `Song` — encapsulated, private fields, validation in setters

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | UUID, **stable across edits** |
| `title` | `String` | required, non-blank |
| `artist` | `String` | required |
| `album` | `String` | |
| `duration` | `java.time.Duration` | read from the file when available |
| `genre` | `Genre` (enum) | |
| `year` | `int` | sane range check |
| `rating` | `int` | **0–100, throws `IllegalArgumentException` outside range** |
| `filePath` | `Path` | the MP3/WAV on disk |
| `coverPath` | `Path` | nullable, falls back to a default cover |
| `favorite` | `boolean` | bonus feature |
| `playCount` | `int` | bonus: statistics |

### `CircularDoublyLinkedList<T>` — Mode 1, shuffle
Doubly linked, circular, **no null terminators**. Past the tail is the head; before the head
is the tail. `insert`, `remove(T)`, `next()`, `previous()`, `size()`, and an `Iterator<T>`
that walks **exactly one lap** before reporting exhausted, so `for-each` terminates on an
endless list.

Shuffle is **not** `Math.random()` per step: the shuffled ordering is baked into the ring
**once at load**, so `previous()` returns the song actually played before. This is also what
makes `peekNext()` deterministic.

### `SimpleQueue<T>` — Mode 2, arrival order
Strict FIFO: `enqueue`, `dequeue`, `peek`, `isEmpty`, `size`. Head and tail pointers, O(1)
both ends.

**Critical:** dequeuing must **not** destroy the user's library. The queue is a *view built
from* the master collection, not the storage of it. `Library` owns the canonical list;
selecting a mode builds that mode's structure from it. `previous()` throws
`UnsupportedOperationException` and the UI **disables** the button rather than letting it throw.

### `BinarySearchTree<T>` — Mode 3, alphabetical
Ordered by title, case-insensitive, **with a tiebreaker**: title → artist → id, so duplicate
titles don't collide and silently vanish. `insert`, `search`, `delete` (leaf / one child /
two children via in-order successor), `inOrderTraversal`. Keep a **`parent` pointer**.

`next()` = in-order **successor**, `previous()` = in-order **predecessor**, implemented as
**real tree navigation** (right subtree's minimum, or climb through parents until arriving
from a left child — and the mirror). **Never flatten the tree into an array and index it.**
That shortcut is the first thing the professor will probe at the oral defense.

### The mode abstraction
`PlaybackMode` interface → `AbstractPlaybackMode` (shared current-song state) → three concrete
modes. `Player` holds a `PlaybackMode` and **never type-checks it**; swapping modes is pure
polymorphism. `complexities()` returns a `Map<String,String>` that `ComplexityPanel` renders
live for the active mode.

**As built (2026-08-12, M3):**

- `AbstractPlaybackMode.load()` and `.previous()` are **`final`**. `load` resets the current song
  before delegating to `build()`, so no subclass can leave a stale song from the previous
  ordering — a bug that only shows after switching modes twice. `previous()` holds the
  `supportsPrevious()` guard in one place, so a one-way mode never invents its own behaviour.
- **`Player.previous()` returns `null` rather than propagating** when the mode is one-way. The
  control is already disabled; a keyboard shortcut arriving anyway must do nothing, not raise the
  mode's exception at the user. The exception still exists and is still tested — it is what the
  *mode* does, not what the *player* does.
- **An edit is not a structural change.** `Player` ignores `LibraryChange.UPDATED`. Rebuilding on
  it would re-draw the shuffle every time the rating slider moved, changing the running order
  under the user mid-listen. `ADDED` / `REMOVED` / `RELOADED` do rebuild.
- **Arrival order takes the front song as current at load**, so all three modes come up with a
  song playing. Leaving it queued would make the first `next()` replay it.
- `select()` in arrival order **dequeues everything in front of the target** — that is what FIFO
  means — but checks membership first, non-destructively. Draining the queue hunting for a song
  that was never in it would throw the whole running order away.
- `tree.first()` **throws on an empty tree**; alphabetical mode guards it. Every mode returns
  `null` from navigation when empty, because an empty library is an ordinary state (ground rule 5).
- Modes take a `StepCounter` now, defaulting to `NO_OP`, so M4's `OperationCounter` is an
  injection rather than a rewrite.
- `PlaybackModeContractTest` runs the shared guarantees against all three modes; a fourth mode is
  held to the same contract by adding one line.

---

## 6. Audio invariants

- Target format: `PCM_SIGNED, 44100 Hz, 16-bit, 2 ch, signed, little-endian`; 4 bytes/frame,
  interleaved `[L0 R0 L1 R1 ...]`.
- Little-endian decode with sign extension — get this wrong and the meters show garbage:
  ```java
  short s = (short) ((buf[i + 1] << 8) | (buf[i] & 0xFF)); // buf[i] = low byte
  float v = s / 32768f;                                    // normalize to -1..1
  ```
- **The sink must never swallow audio.** Tap the buffer *and* forward it, or the meters go silent.
- **Per-channel levels are the feature that matters most.** Deinterleave every block: even
  indices Left, odd Right. RMS **and** peak computed **separately per channel, never one
  combined number**. RMS → bar height; peak → slow-decaying peak-hold cap; per UI frame
  `displayed = max(newValue, displayed * 0.85f)`. Verify with a hard-panned track that L and
  R visibly diverge.
- Publish via `Levels` using `AtomicInteger` + `Float.floatToIntBits` (or `volatile float`);
  the `AnimationTimer` reads at ~60 fps.
- Don't over-buffer, or the meters lag behind what you hear.
- Clean shutdown: stop the game loop, stop playback, drain and close the `SourceDataLine`,
  release the stage.

---

## 7. Analysis, game, visualizer — the load-bearing rules

- `BeatmapAnalyzer` runs **off the playback path**, on a background thread with progress.
  Window 1024 / hop 512, mono-summed; spectral-flux novelty; adaptive threshold over ~±0.5 s
  × sensitivity; peak-pick with ~100 ms minimum inter-onset gap; BPM from an inter-onset-
  interval histogram; `strongBeats` = onsets nearest the BPM grid.
- `BeatmapCache` → `~/.superdwarfkart/beatmaps/<sha256-of-file>.json`, keyed by content hash
  **and analyzer version**, so bumping the algorithm invalidates old maps. Never re-analyze
  a cached file.
- **Lookahead is the whole trick.** An event at beat time `T` spawns at `T − travelTime`,
  `travelTime = courseLength / speed(cc)`. Drive all game timing off `audioSource.position()`,
  **never** accumulated frame deltas — frame drift desynchronizes within a minute.
- **Deterministic generation:** seed the RNG with `hash(songId + speedClass)`, so the same
  song at the same cc always yields the identical course. Without it high scores are noise.
- Speed classes: 50cc 1.0×/every 4th strong beat/×1.0 · 100cc 1.4×/every 2nd/×1.5 ·
  150cc 1.8×/every strong beat/×2.0 · 200cc 2.2×/strong + intermediate onsets/×3.0.
- **M4 (the structure visualizer) is the highest-impact milestone in the project** — the one
  thing a competing team is unlikely to have built, and the view used during the oral defense.
  Reach M4 before touching audio; do not let the game pull effort forward past it.
  `OperationCounter` instruments comparisons and pointer hops but **must not sit in the audio
  or render hot path**.
- Presentation Mode (function key) collapses the game and gives the visualizer the full stage
  with step-through controls and the enlarged complexity scatter. One keypress from the
  running app, no separate slides.

### Entities and scoring

All entities are placed **on beats**, never on a timer.

- **Coin** — frequent. `+1 coin × multiplier`, collect feedback + particle pop.
- **Obstacle (bump)** — avoided by changing lane or jumping. Hit while unstarred: **−5 coins**
  plus brief invulnerability, so a single mistake cannot chain into a wipeout.
- **Star** — rare, on a beat, seeded like everything else. Grants invulnerability for N beats;
  passing *through* an obstacle while starred **breaks it**, plays the 2-frame explosion and
  awards bonus coins. The star sheet is animated — slice and loop it.
- **Beat feedback:** a subtle scale/flash pulse of the course on each strong beat, and
  lane-edge glow driven by the live L/R RMS. This is where the metering and the game visibly
  meet — it is the reason the meters are not just decoration.
- **Rank** = coins collected as a percentage of coins *available in that generated course* →
  S / A / B / C / D. `ScoreRepository` persists the best score per `(songId, speedClass)` to
  `~/.superdwarfkart/scores.json`; the library view shows a rank badge per song, and marks
  which songs have a course ready.

### The three visualizer views (M4)

Each is a `Canvas` sharing the game's sprite set. One view per mode, swapped by `AppState`.

**`CircuitView` — the circular list as a race circuit.** A closed Mario-Kart loop, each song a
numbered position marker around the ring, the racer's kart parked on the current node.
`next()` drives the kart forward along the track, `previous()` backward, and **passing the last
node it visibly continues around the lap to the first** — circularity is demonstrated, never
explained. `prev`/`next` links are the track itself, with bidirectional arrows on the road.
Hovering a node shows title/artist in a tooltip.

**`StartingGridView` — the queue as a starting grid.** Karts in a column, front of the grid at
the top. `enqueue()` slots a kart in at the rear with a settle animation; `dequeue()` makes the
front kart accelerate away and exit frame — it does not come back. Head and tail pointers are
literal labeled flags on the grid. The previous control is visibly disabled here, tooltip
*"FIFO — no going back."*

**`BstView` — live tree with animated traversal.** In-order x-position, depth y-position;
pan/zoom once it outgrows the canvas. Each node shows its title, the current node is
highlighted. **Animate the path, not just the result:** on `next()` the successor walk lights up
each traversed edge in sequence — descend right then run to the minimum, or climb through
parents until arriving from a left child; `previous()` mirrors it. Includes a **slow-motion /
step-through toggle** (walk one edge per keypress during the defense), a **step counter** for
the path just walked, and **shape controls**: one button reinserts the whole library
alphabetically, one reinserts it shuffled. The alphabetical insert **visibly degenerates the
tree into a straight line** — height jumps from ~log n to n and the measured search cost jumps
with it. That is the BST worst case, live, in one click.

**`OperationCounter` / `ComplexityPanel`.** Per operation, show: operation · theoretical
complexity · **actual measured steps** · current n. Below the table, plot **measured steps vs n**
as a live scatter accumulating across operations with the theoretical curve overlaid — on a
500-song library a BST search lands near 9 steps while the circular list takes ~250, and the
scatter traces log n in front of the room. A **"Compare Structures"** action runs the same
search across all three at once and reports the three counts side by side.

---

## 8. Assets — assume nothing about the folder

Art is dumped flat; filenames and frame counts are unknown in advance. `AssetRegistry`
scans at startup, classifies by case-insensitive filename keyword (`disk`/`disc`,
`char`/`player`/`kart`/`racer`, `star`, `coin`, `explos`, `bump`/`obstacle`, `bg`/`background`,
`select`, plus the Spanish `estrella`/`moneda`/`fondo`/`personaje`), slices sheets by
inference (known frame count → `frameWidth = imageWidth / count`; otherwise square frames),
and reads an optional `assets/assets.json` manifest that **overrides** all detection. If the
manifest is absent, **write a template out on first run** populated with what was detected.

**Art already in the repo** (`src/main/resources/assets/textures/Sprites/`, scanned recursively):

| File | Size | Inferred |
|---|---|---|
| `Disk-Sheet.png` | 416×32 | 13 frames of 32×32 — matches the known 13-frame spinning disk |
| `Star.png` | 288×32 | 9 frames of 32×32 |
| `Coin.png` | 32×32 | single frame |
| `Mario/Luigi/Peach/Yoshi/Bowser.png` | 256×64 | 4 frames of 64×64 each |

Still missing: explosion (2 frames), background, racer select portraits, obstacle/bump.
These must resolve to magenta placeholders without breaking anything.

### As built (2026-08-12)

**Nothing outside `assets/` may name an image file.** Ask by `AssetKind` (`sheet(AssetKind.STAR)`)
or by key; the registry decides which file that is. `RatingDisplay` used to hardcode
`/assets/textures/Sprites/Star.png` — that is exactly the coupling this package exists to remove.

- **Two roots, scanned in order:** the artwork bundled in the jar under `/assets`, then
  **`~/.superdwarfkart/assets/`**, which **wins on a name clash**. Art can therefore be dropped in
  without a rebuild — which is how art actually arrives on this project.
- **The generated manifest lands in the user folder**, `~/.superdwarfkart/assets/assets.json`, not
  beside the bundled art: it is the only writable location that survives `mvn clean`. An existing
  manifest is never overwritten.
- **Classification priority runs most-specific first, and `RACER` is last.** `kart`, `char` and
  `player` appear as *qualifiers* on art that is not a racer sprite, so `kart_explosion.png` is an
  explosion and `racer-select.png` is a menu. Getting this backwards was caught by a test, not by
  eye. Two-letter keywords (`bg`) match only as whole words, or `bgm-theme.png` becomes a backdrop.
- Racer sheets are matched by `Racer.spriteKey()` as well as by keyword — `Mario.png` contains
  none of the generic words, so keyword matching alone left all five sheets `UNKNOWN`.
- **Scanning reads filenames only.** No image is decoded until something asks for it, so a folder
  of large sheets cannot slow startup. It also means the registry is unit-testable headlessly:
  `AssetRegistryTest` writes four-byte files that are not valid PNGs and never need to be.
- Square-frame inference was **verified against the real art** and needs no frame table:
  13 / 9 / 1 / 4, exactly the counts above. The smoke test prints them (`[smoke] star : … (9 frames)`)
  because that inference is the one part of this package a unit test cannot reach.
- `SpriteAnimation` holds **no timer and no state** — it answers "which frame at time `t`" and the
  caller supplies `t`. The game's clock must come from `audioSource.position()`, and an animation
  counting its own ticks would quietly break that rule.

---

## 8b. The mood system — M11, the app reskins itself

A **mood** is a saved look: a **16-color GBA palette** plus an ordered stack of **overlay
layers**. Selecting one restyles the whole app instantly and swaps the fullscreen background
art. The user can build, save, import and share their own.

Two scopes, deliberately different:

| Part | Applies to |
|---|---|
| **Palette** (16 colors) | **Everywhere** — fullscreen, mini player, library table, meters, visualizer, game HUD, dialogs |
| **Overlay layers** | **Fullscreen only.** The mini player is a ~300 px transparent strip; parallax art there is invisible noise costing framerate on the one window that is always on screen. |

`mood/` is the **only non-`ui/` package allowed to touch JavaFX**, and only
`javafx.scene.paint.Color` / `Paint` — not a single `Node`, control or layout class (ground rule
3 otherwise stands). Persistence goes through a DTO of hex strings so `MoodRepository` stays a
plain Jackson mapping.

### The GBA constraint is the entire aesthetic

The GBA framebuffer is **BGR555**: five bits per channel, 32 levels each, 32,768 colors; a 4bpp
tile addresses **one 16-color bank**. Both constraints are enforced, because they are what make
an arbitrary user palette still look like a GBA game instead of a Windows theme.

`GbaColor` is the only way a color enters the system:

```java
/** Snap an 8-bit channel to the GBA's 5-bit grid. */
static int quantize(int c8) { return Math.round(c8 * 31f / 255f); }

/** Expand 5 bits back to 8 so 0 -> 0 and 31 -> 255 (NOT c5 << 3, which caps at 248). */
static int expand(int c5)   { return (c5 << 3) | (c5 >> 2); }
```

Every color picked, imported or typed round-trips `quantize` → `expand` before storage, and the
picker shows the snapped result live so what the user chooses is what they get. A mood holds
**exactly 16** colors — no extra slots, no per-view exceptions.

### The 16 roles

`PaletteRole` is an enum with exactly these constants, in this order. Every color in the app
names one of them.

| # | Role | Used for |
|---|---|---|
| 0 | `BACKGROUND` | stage base fill, fullscreen backdrop |
| 1 | `BACKGROUND_ALT` | alternating table rows, panel bands |
| 2 | `SURFACE` | cards, panels, the library table |
| 3 | `SURFACE_RAISED` | hover, selection, popups, active tab |
| 4 | `OUTLINE` | borders, dividers, canvas strokes, lane lines |
| 5 | `TEXT_PRIMARY` | **protected** |
| 6 | `TEXT_DIM` | secondary labels, complexity table body |
| 7 | `PRIMARY` | mode selector, progress fill, buttons |
| 8 | `PRIMARY_DIM` | disabled/inactive variants (previous-disabled in queue mode) |
| 9 | `ACCENT` | focus rings, the kart marker on the circuit, grid pole position |
| 10 | `METER_LOW` | L/R meter gradient bottom |
| 11 | `METER_HIGH` | L/R meter gradient top, peak dot |
| 12 | `POSITIVE` | coins, score gain, "course ready" badge — **protected** |
| 13 | `NEGATIVE` | bumps/obstacles, errors — **protected** |
| 14 | `HIGHLIGHT` | the animating BST successor edge, step-through cursor — **protected** |
| 15 | `SHADOW` | drop shadows, road edge falloff, vignette core |

### Protected roles — the trap that would eat the demo

Four roles carry *meaning*, not decoration. A mood that makes `POSITIVE` and `NEGATIVE` similar
makes coins and bumps indistinguishable mid-run; one that flattens `HIGHLIGHT` into `OUTLINE`
kills the BST traversal animation from the back of the room. Nothing throws. It just quietly
stops working, on stage.

`MoodValidator` therefore runs **on every load and on every edit in the customizer**:

- `TEXT_PRIMARY` vs `BACKGROUND` and vs `SURFACE`: **WCAG contrast ≥ 4.5:1**
- `HIGHLIGHT` vs `OUTLINE`, and `POSITIVE` vs `NEGATIVE`: **CIE76 ΔE ≥ 25** in Lab
- On failure: inline warning naming the exact pair, **and** render an auto-derived substitute
  (shift lightness until the threshold is met) rather than the user's value. Never render an
  invalid mood; never silently accept one either.
- Coins and obstacles must additionally differ in **shape and brightness**, not hue alone — hue
  coding fails for a colorblind viewer and for a projector with bad gamma.

**Moods never tint the sprite art.** Disk, racers, star and explosion are authored PNGs and stay
exactly as drawn. Moods color the road, sky, lane lines, panels, HUD, visualizer and overlays.
Do not add a global `ColorAdjust` over the game.

### Overlay layers

`MoodLayer` is a **sealed interface** with three implementations; a mood holds an ordered list.
Shared properties:

| Property | Values |
|---|---|
| `zBand` | `BEHIND_CONTENT` or `ABOVE_CONTENT` |
| `opacity` | 0.0–1.0, **hard-capped at 0.35 for `ABOVE_CONTENT`** so a layer can never bury the game or the tree |
| `blend` | `NORMAL`, `MULTIPLY`, `SCREEN`, `OVERLAY`, `ADD` (`GraphicsContext.setGlobalBlendMode`) |
| `scrollX` / `scrollY` | px per second; 0 is static, small opposing values on stacked layers give parallax |
| `visible` | per-layer toggle |

**`GradientLayer`** — `LINEAR` (angle in degrees) or `RADIAL` (center, radius), 2–4 stops, each
stop snapped to a palette role *or* a free GBA color. Two properties make it read as 8-bit
rather than as a web page: `bands: int` (0 = smooth, otherwise posterize into N steps,
**default 8** — a hard-banded gradient is the authentic look, a smooth one instantly reads as
modern) and `dither: boolean` (4×4 Bayer ordered dithering across band boundaries — the single
detail that sells it).

**`ImageLayer`** — PNG, JPG or **animated GIF** (JavaFX decodes GIF frames natively;
`drawImage` blits the current one). `fit` = `TILE` / `STRETCH` / `CONTAIN` / `COVER` / `CENTER`,
plus integer `pixelScale`. On import offer a **"GBA-ify"** pass: nearest-neighbor downscale to a
base resolution (default **240×160**, the actual GBA screen) → quantize to the mood's 16 colors
→ optional Bayer dithering; store the processed copy and leave the original untouched. Anything
imported is **copied into the mood's own folder**, never referenced by its original path — a
mood must survive the source file being moved or deleted.

**`ProceduralLayer`** — generated, no asset needed, and worth far more than they cost:
`SCANLINES` (1 px alternating alpha rows), `LCD_GRID` (the faint GBA pixel grid at
`pixelScale`), `VIGNETTE` (radial `SHADOW` falloff), `STARFIELD` (seeded dots that scroll with
the layer). Ship all four, default off. Combined with a palette they make an empty mood look
finished — which matters because the first mood the user builds has no art in it.

### The pixel editor — draw the art inside the app

A **"Create your own"** tab in the customizer: a pixel editor at **16×16** and **32×32** (8×8 if
it is free; nothing larger — these are tiles, not paintings).

**The critical design decision: a tile stores palette *indices*, not RGB.** Each pixel is a
number 0–15 into the mood's palette. Consequences, all good:

- The editor's color picker **is** the mood palette — nothing drawn can be out of palette or off
  the GBA grid. The constraint is structural, not validated after the fact.
- **Changing the palette recolors every tile in that mood, instantly.** Import a Lospec palette
  and the user's hand-drawn background restyles itself — a real "oh" moment that falls out of
  the data model for free.
- One pixel is one hex digit, so the on-disk format is human-readable and diffable:
  `{ "size": 16, "rows": ["0000122100000000", "0001222210000000", "..."] }`
- **Index 0 is transparent**, matching the GBA convention where entry 0 of a bank is the
  transparency key. Render it as the standard checkerboard.

**Tools:** pencil, eraser (paints index 0), flood fill, line, rectangle, eyedropper, undo/redo
(≥ 30 steps), clear. Plus two that matter more than they look:

- **Symmetry** — horizontal / vertical / both, mirroring strokes live. Halves the work on
  anything decorative and is why hand-drawn tiles come out looking deliberate.
- **Tiled preview** — a live 3×3 repeat beside the canvas. Almost every tile here becomes an
  `ImageLayer` with `fit: TILE`, and **a seam is invisible on one tile and glaring once it fills
  the screen**. Without this panel the user finds out after applying it.

**Frames:** up to 4, with an fps field and a play toggle, stored as an array of `rows` blocks. A
2-frame twinkling star tile at 4 fps costs thirty seconds and reads as a professionally animated
background.

**In and out:** **Save to layer** turns the tile into an `ImageLayer` in the current mood
(defaults `fit: TILE`, `zBand: BEHIND_CONTENT`) and opens the layer list so scroll speed can be
set immediately — scrolling a hand-drawn 16×16 tile is the cheapest good-looking background in
the app. **Export PNG** at 1× and at an integer scale. **Import PNG** quantized to the palette
and snapped to the grid; **reject anything over 64×64 with a clear message** rather than
downscaling something unrecognizable. Tiles live in the mood folder and travel with
`.mood.json` export.

Render the editor canvas with `setImageSmoothing(false)` at integer zoom (default 16× for a
16×16 tile), grid lines in `OUTLINE` at low alpha, toggleable. Never anti-alias the surface.

### Beat reactivity — optional and fenced

A mood may set `reactive: true`. When it is and audio is playing, layer opacity and
`ACCENT`/`HIGHLIGHT` **brightness** modulate from the existing `Levels` RMS and beatmap onsets —
both feeds already exist by M7, so it costs nothing.

Hard rules: modulate **brightness and alpha only, never hue**; **never** touch the four
protected roles' distinguishability — clamp so validator thresholds hold at every point in the
modulation; cap visible change at **3 Hz** regardless of BPM; and put a **"Reduce motion"**
switch in Settings that kills reactivity and all layer scrolling globally. A fullscreen overlay
flashing at 8 Hz in a darkened classroom is a genuine problem, not a style question.

### Palette import — the highest-value 40 lines in this milestone

`PaletteImporter` reads two text formats:

- **GIMP `.gpl`** — `GIMP Palette` header, then `R G B<TAB>Name` rows; skip `#` comments and the
  `Name:` / `Columns:` lines
- **Plain `.hex`** — one `RRGGBB` per line

Both are exported by **Aseprite** (the art is already authored there) and by **Lospec**, which
hosts hundreds of ready-made GBA and 16-color palettes. Take the first 16 entries, snap each
through `GbaColor`, assign to roles in enum order, then run `MoodValidator` and auto-fix. Also
accept a **dropped `.gpl` / `.hex` anywhere on the fullscreen window** as an implicit "create a
mood from this palette."

This turns "design a mood" from an afternoon into ten seconds, which is why the app can ship
with twenty moods instead of two — twenty in the switcher reads as a *system*, three reads as a
setting. It is also the only part of this project a non-technical teammate can contribute to
without touching Java. **Do the import before hand-picking a single palette**: sixteen colors
chosen by eye takes an hour and usually looks muddy.

### The customizer

`MoodCustomizerView`, reachable from the side rail, applied **live** — no OK button, no preview
pane, the whole app is the preview.

- **Palette strip**: 16 swatches in a row, Aseprite-style; click one for a picker that snaps to
  the 5-bit grid, role name and current hex shown beneath.
- **Layer list**: add / delete / reorder by drag, per-layer opacity, blend, scroll, z-band,
  visibility. Reordering redraws immediately.
- **"Create your own" tab**: the pixel editor above, drawing straight into the mood's palette.
- **Validator bar**: green when clean, otherwise the failing pair named in plain English
  ("HIGHLIGHT is too close to OUTLINE — the BST traversal will be hard to see").
- **Actions**: Duplicate, Rename, Delete, **Export `.mood.json`**, **Import**. Export bundles
  palette, layer definitions and processed images into one folder so a teammate can drop it in
  and get the identical look.
- **Never edit a built-in preset in place** — duplicate-then-edit, so there is always a
  known-good mood to fall back to.

### Persistence, defaults, presets

- Moods live in `~/.superdwarfkart/moods/<slug>/` — `mood.json` plus imported images.
- Built-ins are resources, copied out on first run only if absent.
- The active mood id lives in `AppState` beside song, mode, racer and speed class, so both
  windows re-style together.
- A mood that fails to load is logged, falls back to the default, and the app keeps running.
  **Never block startup on a bad mood file** (same as ground rule 5).
- Presets are named after **Mario Kart: Super Circuit** tracks — the GBA Mario Kart, the exact
  reference the whole app is built on: `PEACH_CIRCUIT` (default, safe and readable) ·
  `SUNSET_WILDS` (orange→magenta banded gradient) · `SKY_GARDEN` (pastel green/cyan, scrolling
  clouds) · `BOO_LAKE` (desaturated purple/green, vignette) · `SNOW_LAND` (icy white/blue, high
  contrast) · `YOSHI_DESERT` (sand/amber) · `RIBBON_ROAD` (pink pastel, starfield) ·
  `BOWSER_CASTLE` (red/black/orange, scanlines). Plus plain `DARK` and `LIGHT` — **that is where
  the assignment's dark-mode bonus is satisfied, as two moods rather than a boolean.**

### Performance — non-negotiable, this runs over a 60 fps canvas

- Flatten every **static** `BEHIND_CONTENT` layer into one cached `WritableImage` on mood change
  or window resize. Per frame you blit **one** image, not N.
- Only scrolling and reactive layers draw live.
- Pre-scale imported images **once** at load to their final draw size. Never rescale in the
  render loop.
- Draw overlays on the `Canvas`. Do not stack JavaFX `Node`s with `Effect`s over the game —
  blur and blend effects on nodes are composited per frame and will cost the framerate.
- Cap a mood at **6 layers**. If an import would exceed it, say so instead of silently dropping
  one.
- Measure: **FPS must not drop below 58** with the heaviest built-in mood active and the game
  running. Report the number when M11 is done.

---

## 9. Milestone tracker

| # | Milestone | Status |
|---|---|---|
| M0 | Maven skeleton, `javafx:run` opens a styled window, font loads | ✅ done |
| M1 | `model/` + three hand-written structures + JUnit tests | ✅ done |
| M2 | Library CRUD, search, 0–100 rating, cover display, JSON persistence | ✅ done |
| — | Asset layer (§8): `AssetRegistry`, `AssetKind`, `SpriteAnimation`, `assets.json` manifest, drop-in folder | ✅ done |
| M3 | Three modes behind the interface, selector, previous disabled in queue mode, `ComplexityPanel` | ✅ done |
| M4 | ⭐ **Structure visualizer** — circuit, starting grid, animated BST traversal, `OperationCounter`, live scatter, Presentation Mode | ⬜ |
| M5 | ⭐ `LocalFileAudioSource`, real playback, PCM tap, independent L/R meters | ⬜ |
| M6 | `BeatmapAnalyzer` + cache + debug view (BPM, onsets on a timeline) | ⬜ |
| M7 | ⭐ 3-lane runner: lookahead spawning, coins, bumps, star, cc classes, scoring, beat pulse | ⬜ |
| M8 | ⭐ Mini companion mode: transparent stage, disk + racer, expand/hide/quit | ⬜ |
| M9 | Sweep: favorites, history, statistics, keyboard shortcuts, **`DARK` + `LIGHT` moods and a switcher** — the dark-mode bonus ships as moods, not a boolean | ⬜ |
| M11 | ⭐ **Mood system** — 16-color GBA palettes, gradient/image/procedural overlay layers, live customizer, 16×16 / 32×32 pixel editor, `.gpl` + `.hex` import, `MoodValidator`, presets | ⬜ |
| M10 | *Optional:* `go-librespot` child process. Strictly additive — nothing in M0–M9 or M11 may import it | ⬜ |

**M11 is listed before M10 on purpose — build it first.** It is visible in the first two seconds
of the demo, depends on nothing external, and cannot fail in a way that breaks the app. M10
depends on a third-party binary, an OAuth flow and a network. If only one of the two gets built,
build M11. Its prerequisite is *already paid for* if ground rule 7 was respected: with every
color resolved by role, M11 is new code rather than a refactor — so clear the hex-literal debt
recorded in ground rule 7 before starting it.

**Stop after each milestone and report exactly how to test it before continuing.**

---

## 10. Pending experiments — carry forward across sessions

### EXP-1 — Does the go-librespot FIFO pace to realtime?
**Status: OPEN.** Blocks whether the rhythm game works on a Spotify track's **first** play.
**Not on the critical path** — M0–M9 are unaffected. Do not guess the answer; run it.

```bash
mkfifo /tmp/sdmk-pcm
# configure go-librespot: audio_backend: pipe, audio_output_pipe: /tmp/sdmk-pcm,
# audio_output_pipe_format: s16le — then start the daemon and play any track
time dd if=/tmp/sdmk-pcm of=/dev/null bs=176400 count=10
```

| Elapsed | Meaning | Model to build |
|---|---|---|
| ~10 s | Realtime-paced | Analyze on first play; course unlocks on replay |
| ~1–2 s | Decodes ahead | Ring-buffer inside the track, play ~10 s behind the read head → full game on first play behind a `READY… SET…` countdown |
| Blocks forever | Misconfigured | Confirm backend is `pipe` and a track is playing |

Report the measured time and which model applies **before** implementing either.

### M10 notes (only if M0–M9 and M11 are complete and working on local files)
- Use **`devgianlu/go-librespot`**. `librespot-java` was tested and **does not work** — do not
  attempt it. `librespot-org/librespot-golang` is a different, archived project.
- `zeroconf_enabled: false` and `disable_autoplay: true` are **correctness requirements, not
  preferences**. Get either wrong and Spotify, not the active `PlaybackMode`, decides the
  running order — and the failure is silent: playback just continues normally while the
  circular list, queue and BST become decorations.
- `AudioSource` is the seam: nothing outside `audio/` may know a subprocess exists.
- Config is generated by this app on every launch into a private `-config_dir`; never
  user-edited. Destroy the child on **every** exit path (`addShutdownHook` +
  `destroyForcibly()`); an orphaned daemon holding a Spotify session is the failure mode.
- Add `Song peekNext()` (non-mutating) to the mode interface. **The queue must not dequeue** —
  that is the obvious bug and it silently eats a song per prefetch.
- Playback clock from the pipe is sample-accurate for free: `position = bytesConsumed / 176400`.
- **Do not containerize.** JavaFX needs a display and this app needs low-latency audio.
- The prefetch/pooled-buffer design in the brief applies only to a decoder drivable faster
  than realtime. go-librespot is not one — that machinery is documented history, not a plan.
- **go-librespot never touches the speakers.** With `audio_backend: pipe` it has no audio
  device: it writes `s16le` to the FIFO and makes no sound. *This* app reads the FIFO, writes to
  the `SourceDataLine` and taps the same buffer for meters and analysis — identical to
  `LocalFileAudioSource` with a different byte source, and nothing to decode on the Java side.
  Track selection stays with the active `PlaybackMode`: `mode.next()` → REST call to play that
  URI → go-librespot decodes that one track → Java reads it → track-end WebSocket event →
  repeat. It chooses nothing.
- Other load-bearing config: `bitrate: 320`, `crossfade_duration: 0` (crossfade blends adjacent
  tracks and destroys beat alignment at the boundary), `cache.enabled: false`, REST/WebSocket
  server on `localhost:3678`. Leave **normalization enabled** — Spotify's −14 LUFS target means
  consistent meter range and consistent game difficulty; disabling it makes quiet songs generate
  dead courses. Do not use DJ X: its synthesized narration would be fed to the beat analyzer as
  if it were music.
- Java cannot create a FIFO — invoke `mkfifo` via `ProcessBuilder` at startup and tolerate
  "already exists", then read it with an ordinary `FileInputStream` (reads block until the writer
  produces data). POSIX only, so the Spotify path is macOS/Linux; local files stay portable.
- **Binary resolution order at startup:** path stored in `AppConfig` → bundled resource at
  `src/main/resources/bin/<os>-<arch>/go-librespot` (extract to `~/.superdwarfkart/bin/`,
  `chmod +x`, strip `com.apple.quarantine` on macOS — Gatekeeper refuses an unsigned third-party
  binary if the project arrived as a browser download rather than a clone) → lookup on `PATH`
  (catches `brew install go-librespot`) → not found, Spotify features disable themselves
  silently. The user installs nothing.
- **Login is the only step needing a human**, and it gets exactly one piece of UI: a side-rail
  item reading **Spotify — Not connected** that starts the daemon, runs interactive OAuth and
  reports success or failure. Credentials persist in `state.json`, once per machine. **Never
  trigger any of this at startup** — anyone who never clicks that item must never learn the
  feature exists.
- If the FIFO turns out realtime-paced (EXP-1), a Spotify track **has no beatmap until played
  once**. Ship it as a feature: first play gives audio, meters and an empty course with a
  `BUILDING COURSE…` banner; the beatmap caches on completion and every later play has full
  obstacles and scoring. That mirrors driving a track before unlocking time trials, which fits
  the Mario Kart framing better than a loading screen. A `GENERATING COURSE` screen (kart running
  along a progress bar) appears only on cold start and manual library selection.

---

## 11. Gotchas that will actually bite

- **No Spanish identifiers or UI strings** (ground rule 1 — repeated here because it is the
  kind of thing that drifts halfway through a long build).
- `APP_NAME` leaking into the mini player — invisible until rendered.
- Little-endian byte order and sign extension.
- The PCM callback is on the audio thread: analyze fast, return, never touch the scene graph.
- Game timing from `audioSource.position()`, never accumulated frame time.
- **Dequeuing in mode 2 must not delete songs from the library.**
- **BST deletion with two children, and duplicate song titles** — the two places this breaks.
  Both are covered by tests; keep them passing.
- Idle meters and an empty course before playback starts; a clear message on load failure.
- Flattening the BST for in-order navigation is the single most likely shortcut to get caught.
- Clean shutdown: stop the game loop, stop playback, drain and close the `SourceDataLine`,
  release the stage.
- **A hex literal anywhere outside `mood/`** — it looks harmless per site and turns M11 into a
  find-and-replace across a finished UI, which is where the feature gets abandoned.
- **Smoothed pixel art.** JavaFX interpolates by default; the sprites turn to mush and it reads
  as a bug in the art, not in the code. `setImageSmoothing(false)`, integer scale factors.
- **Moods: the four protected roles are the whole risk.** A mood that makes coins and bumps the
  same color, or the BST highlight the same as the outline, throws nothing and looks fine in a
  screenshot — it fails live, in front of the room. `MoodValidator` runs on load *and* on every
  edit, and renders a substitute rather than the invalid value.
- **Moods never recolor the sprite art**, and `ABOVE_CONTENT` layers cap at 0.35 opacity. A
  background is a background; it must never compete with the game or the tree.
- Static overlay layers are flattened to one cached image on mood change — not recomposited per
  frame. Six layers redrawn individually at 60 fps is how this feature quietly costs the
  framerate the whole project exists to show off.
- **Pixel tiles store palette indices, never RGB.** Store RGB and the tiles stop following the
  palette, which throws away the best thing about the editor. Index 0 is transparent.
- The tiled 3×3 preview is not a nicety — a seam is invisible on one tile and unmissable once it
  fills a 1080p screen.

---

## 12. Team split (context, not a rule)

Divide **by structure, not by layer**: each teammate owns one structure end to end —
implementation, JUnit tests, and its visualizer view — because each has to defend that
structure out loud. Someone who only wrote UI cannot answer "why a BST here, and what's the
cost of deletion." The sustentación is live and in front of the class — and a friendly
professor tends to probe *harder*, not softer, precisely to look impartial. Presentation Mode
exists so the answer to any question is a keypress in the running app.

Only two pieces here are genuinely non-trivial: the **beatmap lookahead sync** (M6–M7) and the
**animated BST successor traversal + `OperationCounter`** (M4). Everything else is well-trodden
and can be owned without supervision, so those two should go to whoever is driving the build.

**What this is optimized for is not the grade — it's the room.** A polished JavaFX player is
table stakes in a class where people are trying, and the three structures are easy for anyone
competent. The differentiators are the **structure visualizer** and the **mood system**: one
makes the project *be* the lecture rather than decorate it, the other is what the room sees in
the first two seconds.

If FXGL is ever wanted after all: it can be embedded, but it expects to control the application
lifecycle. Reach for it only if the `Canvas` runner turns out to be the bottleneck — it won't.
