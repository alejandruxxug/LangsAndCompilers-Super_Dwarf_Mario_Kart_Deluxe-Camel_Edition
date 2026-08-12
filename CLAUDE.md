Al# Super_Dwarf_Mario_Kart_Deluxe-Camel_Edition

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
└── ui/           MiniPlayerView, FullscreenView, LibraryView,
                  RacerSelectView, LevelMeterView, ComplexityPanel
    └── visualizer/  CircuitView, StartingGridView, BstView, OperationCounter
```

**Three naming collisions to avoid:** never name the model class `Character`
(`java.lang.Character`), the sprite class `Animation` (`javafx.animation.Animation`), or the
tree view `TreeView` (`javafx.scene.control.TreeView`). Use **`Racer`**, **`SpriteAnimation`**,
and **`BstView`**.

---

## 5. The graded core

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

---

## 9. Milestone tracker

| # | Milestone | Status |
|---|---|---|
| M0 | Maven skeleton, `javafx:run` opens a styled window, font loads | ✅ done |
| M1 | `model/` + three hand-written structures + JUnit tests | ✅ done |
| M2 | Library CRUD, search, 0–100 rating, cover display, JSON persistence | ✅ done |
| M3 | Three modes behind the interface, selector, previous disabled in queue mode, `ComplexityPanel` | ⬜ |
| M4 | ⭐ **Structure visualizer** — circuit, starting grid, animated BST traversal, `OperationCounter`, live scatter, Presentation Mode | ⬜ |
| M5 | ⭐ `LocalFileAudioSource`, real playback, PCM tap, independent L/R meters | ⬜ |
| M6 | `BeatmapAnalyzer` + cache + debug view (BPM, onsets on a timeline) | ⬜ |
| M7 | ⭐ 3-lane runner: lookahead spawning, coins, bumps, star, cc classes, scoring, beat pulse | ⬜ |
| M8 | ⭐ Mini companion mode: transparent stage, disk + racer, expand/hide/quit | ⬜ |
| M9 | Sweep: dark mode, favorites, history, statistics, keyboard shortcuts | ⬜ |
| M10 | *Optional:* `go-librespot` child process. Strictly additive — nothing in M0–M9 may import it | ⬜ |

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

### M10 notes (only if M0–M8 are complete and working on local files)
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

---

## 12. Team split (context, not a rule)

Divide **by structure, not by layer**: each teammate owns one structure end to end —
implementation, JUnit tests, and its visualizer view — because each has to defend that
structure out loud. Someone who only wrote UI cannot answer "why a BST here, and what's the
cost of deletion." The sustentación is live and in front of the class.
