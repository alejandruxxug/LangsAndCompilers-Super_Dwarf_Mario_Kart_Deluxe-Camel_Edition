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
- **`APP_NAME` must never reach the mini player.** Measured, now that the window exists: it is
  **43 characters**, which at 9px is 387px against a **card 124px wide** — three times over, and
  wider than the 224px window itself. **So the companion window draws no application name at all**;
  the record and the kart are the identity, and there is no room for anything else. This overflow is
  invisible until the window is actually rendered, so it is checked two ways rather than watched for:
  `MiniPlayerLayoutTest.fullNameWouldOverflow` holds the arithmetic, and the smoke test reads every
  label back out of the live companion window and fails if the full name is in any of them.
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
   **Debt paid in M9.** `css/app.css` carried 60 hex literals; it now carries **three**, all of
   them the magenta fault marker, all in one block the test allows by name. Everything else
   resolves through `-role-*` (one of the sixteen) or `-ui-*` (a surface derived from them), both
   supplied at runtime by `mood/PaletteCss`. `PaletteCssTest` fails the build on a literal
   anywhere else in that file **and** on a token the palette does not define — the second of those
   being the failure that throws nothing and simply draws the control in the wrong colour.
   `assets/SpriteSheet` keeps its two, which stay literal by design.

   **This was on M9's critical path, not M11's.** A mood switcher shipped over a stylesheet full of
   literals restyles the canvases and none of the controls, which does not read as a partly built
   feature — it reads as a broken one.

   **Still true after M7:** the runner is a full-screen canvas of road, sky, kerbs, sprites and a
   head-up display and it added **zero** hex literals — every one of them resolves through a role,
   and where two roles were too close together to tell apart the fix was a `Palette.mix` between
   roles rather than a colour. The CSS is still the only debt.

   **Paid down in M4:** `mood/PaletteRole`, `mood/Palette` and `mood/GbaColor` exist now — the
   16 roles, an immutable palette snapped to the 5-bit GBA grid, and `Palette.active()` as the
   accessor. Every color the visualizer draws resolves through a role, so M4 added **zero** new
   literals. M11 inherits the enum and the color pipeline and has to build the layers, the
   customizer, the validator and the persistence, not retrofit the roles. The remaining debt is
   the CSS: those ~60 literals are still the job to do before M11 starts.
8. **Pixel art is never smoothed.** `gc.setImageSmoothing(false)` on every `GraphicsContext`,
   `setSmooth(false)` on every `ImageView`. JavaFX interpolates by default, which turns
   hand-drawn 8-bit sprites into mush at any scale other than 1:1. Scale by **integer**
   factors only (2×, 3×, 4×) — never fractional.

   **One exception, and it is exactly one: entities travelling down the runner's road**
   (`RunnerView.entityScale`). The integer rule was written about a sprite sitting *still* at a
   scale, and about that it is right. A sprite coming towards the camera is a different case:
   snapping between four sizes meant it visibly jumped three times on the way in, and on the one
   thing the player is trying to **time**, a sprite that changes size in a step reads as a sprite
   that changed *position* in a step. The mush is avoided rather than accepted — smoothing stays
   off, so this is a nearest-neighbour blow-up and never a blurred one, and `drawSprite` rounds the
   destination rectangle to whole pixels so nothing straddles a half pixel. What it costs is uneven
   pixel widths at fractional scales, which is invisible on something moving and is what the
   hardware did anyway. **Everything else — the racer, the explosion, every sprite in the
   interface — still goes through `spriteScale` and still snaps.** Do not widen this without the
   same argument.

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
| `-Dsdmk.diag=true` | Measure the runner's frame loop: achieved fps and frame-interval percentiles, the tick split, and the playback clock's own granularity. Prints a line every two seconds and draws a readout over the road. **`F3` toggles it live** (off → printed + overlay → printed only), which is the more useful of the two — a stutter somebody is watching can be measured while it happens. |
| `-Djavafx.pulseLogger=true` | The toolkit's own per-phase frame log. Reach for this only when `sdmk.diag` says the frame interval is long but the tick is cheap, i.e. the time is going to the render thread or to layout rather than to anything this project wrote. |
| `-Dsdmk.screenshot=out.png` | During a smoke test, snapshot the window to a PNG. This is the only way to check layout without a person watching. It also writes one shot per view beside it — `out-shuffle`, `-arrival`, `-alphabetical` (the three structure views), `-presentation`, `-history`, `-racers`, `-settings`, `-moods`, `-moods-light`, `-library-light` (the light palette on the controls, where a bevel drawn the wrong way round shows), `-dsa-folded` (the table with the structure column folded away), `-race` and `-mini` / `-mini-compact`. **Every one of them exists only once a mode has been selected or a key pressed**, so one shot of the opening state proves nothing about any of them. The companion shots are taken from **its own scene** (it is a separate window, so the main one's snapshot contains none of it) and after a seek a third of the way in, because a progress line at zero is a picture of an empty line. |

**The smoke test plays about three seconds of the current song** and prints the measured L/R levels,
so a run is audible. That is the point: the base screenshot is taken while audio is still flowing,
and a picture of two meters that have already fallen to silence says nothing about either. The
`channels differ` line is the check for the mistake that matters most — two channels reading
identically because they were never deinterleaved.

**It also analyses the current song and prints the beatmap**, waiting for it if the cache is cold.
The line to read is `grid deviation`: a tempo is always a plausible number, but beats sitting a few
milliseconds off the grid means the detected beat is the one in the music, and a figure approaching
a quarter of the beat means the histogram picked a tempo the track does not have.

**And it generates the course at all four speed classes and drives each one.** Three things on
those lines cannot be checked any other way. The entity counts are the claim that difficulty comes
from the music rather than from a timer, and only mean something against a real track's onsets.
`reproducible` regenerates each course and compares it — every stored high score rests on that
holding. And `lap` runs a scripted greedy driver over the whole course at sixty frames a second
through the real collision rules; a course a competent driver cannot rank well on is a generated
course the rules cannot survive, and that is what the line catches, over four minutes of beatmap,
on every launch.

**Then it drives the runner with real key events and times a frame.** `steering` and `jump` fire
`LEFT` and `SPACE` at the scene and report what the kart actually did — a control that never reaches
the game is a routing fault, invisible to both a screenshot and a unit test, and this found exactly
that twice. `entities drawn` is what is on screen against what the course holds, and `frame cost` is
the average of 120 repaints. Read those two together before believing the game is slow: a projection
that makes things crawl and then rush looks laggy at 0.2 ms a frame.

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

### Keyboard shortcuts

| Key | Does |
|---|---|
| `←` / `→` | previous / next song |
| media prev / next (`TRACK_PREV`, `TRACK_NEXT`) | previous / next song |
| `Tab` | cycle the playback mode |
| `Space` (`PLAY`, `PAUSE`) | play / pause |
| `F4` | fold the structure column away, and back |
| `F5` | Presentation Mode on/off |
| `F6` | swap the library for the runner, and back |
| `F7` | collapse to the companion strip, and back — **the same key in both windows** |
| `F8` *(companion window)* | put the artwork away, leaving the song and the transport, and back |
| `Esc` | leave Presentation Mode; on the companion strip, expand |
| `→` / `Space` *(tree view focused)* | step through one edge of a traversal |
| `←` / `→` / `A` / `D` *(road focused)* | change lane |
| `Space` / `↑` / `W` *(road focused)* | jump |
| `F3` *(road on screen)* | cycle the frame-pacing readout: off → drawn → printed only |

They are wired across **both phases of event delivery**, and the split is load-bearing:

- **Filter** (runs first, wins everywhere) for `F4`, `F5`, `F6`, `F7`, `Esc` and `Tab`. `Tab` is
  excused when a text field has focus, where it belongs to the field.
- **Handler** (runs last, only if nothing else wanted the key) for the transport keys. The library
  table uses the arrows for its selection, the search box for the caret, the tree view for its
  step-through, and **the runner to steer** — all four consume the event first, so the transport
  never takes a key out from under a control that was using it. Put the arrows in a filter and all
  four break at once. `Space` is in this group for the same reason: it steps the tree when the tree
  has focus, jumps the kart when the road has focus, presses whichever button has focus, and only
  reaches play/pause when nothing else claimed it.

**Which is why nothing that duplicates a function key may take keyboard focus.** The header's three
toggles (`F4 HIDE DSA`, `F7 MINI`, `F6 RACE`) and every control on the companion strip are
`setFocusTraversable(false)`. The header sits at the top of the scene, so whichever of its buttons
came first held focus from launch and would have answered the **first space bar of the session** —
and with the mini toggle added, that meant the opening `Space` collapsing the whole window instead
of starting the music. It is the same fault that left the runner's jump dead, in a different place:
a control quietly eating the key play/pause is meant to get. These buttons have the key printed on
them, so focus buys them nothing.

**The companion window has the same two-phase split, for the same reasons.** `F7` and `Esc` are a
filter so leaving works wherever the pointer left the focus; the transport keys are a handler.

**The driving keys are a scene filter installed while the road is on screen — not a handler waiting
for focus.** The first version needed the road to have keyboard focus, which is a condition the user
cannot see and had no reliable way to satisfy: `Tab` is bound to the mode cycle application-wide, so
focus could only be taken by clicking the right pixels, and any button pressed since took it away.
Pressing space then fell through to the transport and *paused the music* instead of jumping — which
is exactly what a broken control looks like. The filter runs before anything else and does not care
what has focus. The cost is that the transport shortcuts are unavailable during a race; the buttons
at the top still work and `F6` hands the keys back.

The runner **only claims the keys while the music is running**, and it asks the engine outright
rather than reading a flag the frame loop maintains — that flag is stale for a frame after every
resume, which left the controls dead exactly when they were first pressed. Steering a frozen kart to
line up an obstacle that cannot reach it is not a control, and `Space` has to reach the transport
while paused or the play key stops working when it is the only one wanted.

**`Tab` no longer moves focus between controls.** That is the deliberate cost of binding it; the
interface is mouse-driven and every control is reachable by clicking.

**macOS usually swallows the media keys** before the JVM sees them — they are routed to the system
media controls. They are wired because they cost nothing and work elsewhere, but the arrows are the
path that can be relied on. Do not "fix" the media keys by grabbing a global hotkey.

### Frameless windows are a design decision

**Every window this application opens is undecorated.** No operating system title bar, no
native buttons, no platform styling — so a window looks identical on macOS and Windows and
neither breaks the theme. `ui/PixelDialog` is the shell: it draws the title bar, the close
button and the border, and it supplies what the system chrome otherwise would — **dragging by
the title bar, a close button, Escape to cancel and Enter to accept**. Anything that needs a
window goes through it; `javafx.scene.control.Alert` and `Dialog` are not used anywhere.

This matches the mini player the brief specifies (`StageStyle.TRANSPARENT`, no title bar,
custom hide/quit/expand buttons, draggable by the top bar) — the mini player is the same idea
applied to the companion window, and M8 built on `PixelDialog`'s drag handling rather than
reinventing it. **`PixelDialog.dragBy(Node, Stage)` is now that one implementation**, called by the
dialogs' own title bar and by the companion strip's. Any future undecorated window uses it too;
there must never be a second copy of those eight lines, because a window that cannot be moved is
the one thing the missing chrome would actually be missed for.

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

**Mini / companion mode** (`StageStyle.TRANSPARENT`, no title bar) — **a game cartridge standing on
the record, and nothing else**:
- The card **is `Cartridge.png`**, darkened by `CARTRIDGE_SHADE`, and the song information sits on
  its **black label**: cover, title, artist, progress, back / play-pause / forward. The label's
  rectangle is measured off the artwork by `SpriteSheet.darkRegion`, never written down.
- The cartridge's width comes from its own inlet, so its foot is exactly as wide as the record and
  it reads as **plugged into** it; `CARTRIDGE_SEAT` then pushes it down into the record's slot.
- **A compact view** (`F8`, or the `^` button) puts the cartridge, the record, the kart, the cover,
  the progress line and the clock away, leaving the song's name and the three transport keys — 210 x
  87 against the full 280 x 426. It is the state for a window parked in a corner, where the artwork
  is the part costing screen space rather than the part earning it.
- Underneath it the **spinning disk is a pedestal**, half again as wide as the card and breaking
  out past both its edges, with the racer sprite driving on top — "driving" while audio plays and
  freezing when paused, **this is the play/pause indicator**.
- **Everything else is transparent.** No outer frame, no background panel, no rectangle around the
  whole thing: what sits on the desktop is the shape of the artwork. The card keeps its own border
  (it is `.pixel-window`, the same frame the dialogs draw); the root paints nothing.
- Custom **hide, quit, expand** buttons — no OS chrome exists, so the window must supply them.
  They sit **on top**, above the card, on the transparent ground.
- Draggable by the card and the record (`setOnMousePressed` / `setOnMouseDragged` with offset
  tracking), built on `PixelDialog.dragBy` rather than reinvented. There is no title bar left to
  drag by, so the body is the handle.

**Fullscreen mode:**
- **Top** — playback-mode selector + `ComplexityPanel` for the active mode.
- **Left** — current song, cover, progress bar bound to real playback time, and **the structure
  visualizer for the active mode**. This *is* the queue view: upcoming songs are shown
  structurally, not as a flat list, and it swaps automatically when the mode changes.
- **Right** (wider, not an even split) — the 3-lane runner flanked by the L and R meter bars.
- **Side rail** — Library, Favorites, History, Racer Select, **Moods**, Settings.
- **Presentation Mode** (function key) — see §7.

### As built (2026-08-13, M8)

**The window is 224 × 394, measured — it is sized to its content and the constants are the ceiling
it is checked against, not a size it is forced into.** The card is 140 wide with 124 of content, and
every caption limit comes from `charBudget(CONTENT_WIDTH, fontSize)` rather than from a number
somebody counted. This is the narrowest thing in the application, and in this font a caption that
does not fit runs off the side while **nothing anywhere reports it** — so the smoke test measures the
real window and reads every label back: the printed size against the constants, and every label's
right edge against the window's.

- **`-fx-background-color: null`, and `transparent` is not the same thing.** Two separate traps, one
  line. Omitting the declaration does not give a transparent root — **Modena's own `.root` rule wins
  and paints `#f4f4f4`**, which is a solid grey rectangle on the desktop where the design called for
  nothing at all. And a `transparent` fill *is still a background*, so the region goes on being
  picked and the window's invisible corners swallow every click meant for whatever is behind it.
  Only `null` gives both: nothing drawn and nothing caught. Verified by reading the corner pixels'
  alpha out of the screenshot, which is the only way to tell these three states apart — all of them
  look identical in a viewer that composites onto white.
- **Where the sprites go is measured off the artwork, not guessed.** `SpriteSheet.opaqueBounds(frame)`
  was added for this: the record occupies the **lower 60%** of its frame with nothing above it, and
  the racer's driving frames leave 14 transparent rows above the kart and 10 below. Placed by frame
  rectangle the kart hovers in the air above a record whose top edge is nowhere near where the frame
  says it is. So the kart stands on the middle of the record's own ink and the card's foot is covered
  by the record's own top edge. This is knowledge about artwork, so it lives in `assets/` beside the
  filename matching and the frame-count inference — and art with different margins needs no change
  anywhere. Measured once per frame and cached; **never call it per repaint.**
- **The window's whole shape follows from the artwork, and `DISK_SIZE` is the only knob.** The
  cartridge is **full width down to source row 466 and then steps in 27 pixels a side**, so its
  *inlet* — the foot it stands on — is 454 of 500. `CARD_TO_DISK_RATIO` is the inverse of that share,
  which makes the inlet exactly as wide as the record: the cartridge reads as **plugged into** it
  rather than balanced on it. Matching the cartridge's *widest* part instead leaves the record
  narrower than the part actually resting on it, which is the version that looks like a mistake.
- **`DISK_SIZE` is a request, not a promise.** The record is pixel art snapped to a whole multiple of
  its 32px frame, so 254 draws at 256. `companion inlet` therefore compares the inlet against the
  record **as drawn** — comparing against the request passes while the two visibly do not meet.
  Set it to a multiple of 32 for an exact match; anything else is a pixel or two nobody can see.
  `SpriteSheet.footprint(frame)` is what measures the real inlet, so replacement art that steps in
  differently is reported rather than silently misaligned.
- **The cartridge's width is also what decides how long a song title can be**, because the label is
  only 48% of it and it is the window's width. At the current size the label is 133 and the title
  fifteen characters; at the 200-wide cartridge this started on it was ten. Whatever the number, the
  whole value is in the tooltip as it is everywhere else.
- **The record's canvas is sized to what is drawn, not to what was asked for.** A 256px sprite
  centred on a 254px canvas loses a pixel off each side; clipping artwork to honour a number nobody
  can see is the wrong way round.
- **The cartridge is darkened by one number**, `CARTRIDGE_SHADE`, as a brightness adjustment on the
  one image rather than a repaint of it, so the grain and the moulded shading survive. It is the only
  node effect in the application and it is cached, which is what makes it honest: the image never
  changes, so it is rasterised once rather than recomposited on every frame the record draws — the
  per-frame cost is the whole of what the warning against node effects is about.
  **A negative brightness on `ColorAdjust` is a multiplier, not a subtraction** — measured, -0.28
  took the shell's 113 to 81, which is ×0.72 and not −71. So the scale is proportional: it never
  quite reaches black, and the label keeps its share of the distance instead of converging on the
  shell. At -0.5 the shell reads 56 and the label 5.
- **The label's contents are centred in it, not top-aligned.** The cover can only grow until it is as
  wide as the label, so on a tall label there is height left that it cannot take. Piled up under the
  transport that reads as a band somebody forgot to fill; split evenly it reads as a margin.
- **The cover absorbs the slack.** It is the one thing on the label that can be any size, so
  `fitCoverTo` measures everything else and gives it the difference — a number written down here
  would be right for one font and quietly wrong for the fallback. Guarded against re-entry, because
  setting sizes during layout asks for another layout.
- **`ImageView` is not a resizable node.** `resizeRelocate` moved the cartridge and left it at the
  artwork's own 500 pixels, which is most of a window three times narrower than that; it is sized by
  `setFitWidth`/`setFitHeight` and only positioned by the layout.
- **Content taller than the label is not clipped** — a `VBox` lets it run on down over the grey
  body, still inside the window, so the check for labels inside the window catches none of it.
  `companion label` reports the measured panel and the overflow.
- **The cartridge is seated *into* the record, not stood on it.** `CARTRIDGE_SEAT` is 0.5 — half way
  down the record's own ink, which is where its slot is and the same line the kart's wheels stand on.
  The record is drawn in front of the cartridge, so its near half closes over the foot. Measured at
  the join: rows 345–360 of the window are 256px wide, which is the record's widest band and the
  cartridge's 254px inlet at once — you cannot see where one ends and the other starts, which is the
  whole effect. A fraction of the ink rather than a pixel count, so it stays the same *position* if
  the record is resized or the art replaced.
- **`CARD_FOOT` is now only the fallback**, for the layout used when there is no cartridge art at
  all.
- **A title too long for the compact strip scrolls**, and one that fits sits still — motion carrying
  no information is just something moving in the corner of the eye. The window is cut from the title
  followed by a copy of itself, so it wraps seamlessly; in a fixed-width font a character is a whole
  step, so it needs no measuring pass and can never land on half a glyph. It runs on **wall time**,
  not the playback clock: a title that stopped moving when the music paused reads as a stuck window,
  where a *rainbow* that stops reads as the beat stopping, which it has.
- **The compact title is a rainbow walked in time with the track** — one step per beat, taken from
  the beatmap's tempo, falling back to a fixed rate for the first seconds of a song nobody has
  analysed yet. The colours are the runner's star's, made of **roles** for the same reason: reaching
  for `Color.hsb` would put six colours into the interface that no mood could ever reach. It is
  interpolated rather than stepped, with a small lift toward `TEXT_PRIMARY` peaking on the beat, so
  the beat arrives as a swell in an already-moving colour and never as a flash — which is what keeps
  it inside §8b's cap on beat reactivity. A hard colour switch per beat would not be.
- **The kart is darkened by `RACER_SHADE`** in the full view. It is the one bright thing in a window
  that is otherwise a dark cartridge on a dark record, and at full strength it read as pasted on.
  Drawn through a `ColorAdjust` set on the `GraphicsContext` and **cleared straight afterwards** —
  an effect left on the context shades whatever the next repaint draws first, which is the record.
  Moods still must never tint sprite art; this is a fixed decision about one window's lighting, in
  the same breath as `CARTRIDGE_SHADE`.
- **The compact strip is wider than the cartridge's label**, which is the pleasant surprise in it:
  with no cartridge to fit inside, the title gets the whole window rather than the 48% of it the
  label is — twenty-five characters against fifteen. Compact is where long titles read best.
- **CSS outranks a value set in code, and that cost three separate bugs in one sitting.** JavaFX
  ranks an author stylesheet *above* a programmatic value (only an inline style beats it), so:
  `title.setTextFill(rainbow)` did nothing at all — the title stayed perfectly legible in
  `.mini-title`'s colour and the effect simply never appeared; and `.mini-compact { -fx-padding: 8 }`
  silently replaced the padding the title had been measured against. **Colours that have to win are
  set with `setStyle`; sizes the layout depends on are set in code and deliberately absent from the
  stylesheet.**
- **A border is part of a region's insets.** The compact strip is 210 wide and a child inside it gets
  210 less the padding *and* less the 3px `.pixel-window` frame, twice. Six pixels the title thought
  it had is exactly one glyph, and one glyph over is the difference between a title that scrolls and
  one the label cuts short a second time — which looks like the marquee is broken rather than like
  the arithmetic is.
- **The font really does advance one em per glyph — measured, 8.00px at 8px.** `Fonts.advance(size)`
  answers it for the one caption that has to fit outright rather than be ellipsized. Measuring it
  needs care: layout bounds are the *ink*, so a single string is short by the bearings at both ends
  and underestimates. It measures **the difference between two lengths**, which cancels them.
- **Everything that spans the column is re-sized when the view changes** (`fitRowsTo`). The first
  version was not, so the compact strip inherited the *label's* width: the title was cut at fourteen
  characters where twenty-five fitted, and the transport huddled in the middle of a strip built to
  hold it. Nothing overflowed and nothing threw — it quietly wasted most of the window it was given,
  which is the failure mode this whole window keeps having and why so much of it is measured.
- **The window is resized around the new contents.** A toolkit does not shrink a window because its
  contents stopped filling it, so without `sizeToScene` the compact view is a full-size window with a
  hole in it. `companion compact` prints both sizes and fails if it did not shrink, if the transport
  stopped being clickable, or if it did not come back to exactly its old size.
- **The record takes no mouse events at all, and that is not optional.** A `Canvas` is picked on its
  whole rectangle whatever it has drawn in it, and this one is 224 square reaching a long way up over
  the card — measured, it covers the transport row completely (canvas y 173–397, keys at y 220–249).
  Left pickable it **silently swallowed every click on play, previous and next**: the keys still
  highlighted on hover, because that is the card underneath, and then did nothing. Nothing throws, no
  screenshot shows it, and the keyboard shortcuts kept working the whole time, so the smoke test's
  `companion space` line stayed green through all of it. `disk.setMouseTransparent(true)`, and the
  window is dragged by the card and by `recordGrip` — a transparent region over the part of the
  record hanging *below* the card, where it cannot be in front of anything.
  **`companion clicks` is the check**, and it was confirmed to fail when the flag is taken away
  rather than merely to pass with it there.
- **Three knobs adjust the kart**, all named and all in `MiniPlayerView`: `RACER_SHARE` (size, in
  whole-number steps — 0.44 to 0.71 all land on 2x), `RECORD_SURFACE` (where on the record it
  stands, as a fraction down the record's ink) and `RACER_LIFT` (how far above that it floats), plus
  `RACER_NUDGE_X` sideways.
- **The spinning disk freezes because the playback position does, and nothing is told to freeze.**
  The disk frame and the kart's frame are both functions of `engine.positionSeconds()` through
  `SmoothClock` — pause the card and the picture stops with it, exactly as the runner's road does.
  The indicator and the audio cannot disagree because there is only one of them. `SmoothClock` is
  here for its usual reason: a card reports whole buffers, and a record driven straight off one
  jerks.
- **`previewAt(seconds)` exists because this is unphotographable.** A still picture of a spinning
  disk is a static disk, and the frame loop cannot be watched either — the smoke test holds the
  interface thread, so no pulse arrives. Two different moments are asked for and compared, which is
  simultaneously the check that it *stops*: same position in, same frame out.
- **A missing cover gets the library's magenta placeholder, not an empty frame.** On a card this
  small the cover is most of what there is to look at, and a bare outline reads as something that
  failed to draw. It wears `cover-placeholder-label` for its colour and `mini-cover-label` only for
  its size, so the two placeholders cannot drift apart and no new hex literal was added.
- **The first version of this window was a landscape strip** — 420 × 190, cover and record side by
  side with the text beside them. It worked and it was wrong: it read as a widget in a box. The
  portrait card on a plinth is the design, and the difference is almost entirely that the record is
  allowed out of the frame.
- **The companion window has no owner, and it is shown before the main one is hidden.** An owned
  window is hidden along with its owner, which is precisely the moment this one has to stay up; and
  JavaFX exits when the last window is hidden, so the order is what separates collapsing the
  application from closing it. Both are one line and neither fails loudly.
- **Collapsing stops what the main window was drawing** — meters, beatmap timeline, playback clock,
  structure view and runner. An `AnimationTimer` does not stop because its window was hidden; the
  companion keeps the toolkit's pulse running, so five canvases would carry on recording draw
  commands for a window nobody can see, on the one arrangement designed to sit in the background for
  a whole album. `StructureView.start()` and `PlaybackBar.startClock()` were added as the
  counterparts of the `stop()`s that already existed.
- **Stopping the runner also files the run**, which is the same thing closing the window does and
  the right answer to the same question: collapsing to the companion is leaving the race, because
  there is no longer a road to look at.
- **`Space` on the companion is checked by the smoke test and `→` is checked with its
  precondition.** Space is the key a focused button steals, so nothing here is focus-traversable;
  the arrow only has somewhere to go if the running order does, and a disabled next control doing
  nothing is a drained queue behaving correctly rather than a key that failed to arrive. Reporting
  that distinction is the difference between a check and a red light nobody trusts.
- **Hide is reported, not asserted.** Minimising belongs to the window manager and an undecorated
  transparent window is exactly where one may decline; on this machine it minimises to the dock. A
  dead button on some other platform is worth knowing about and is not a reason to fail a build.
- Screenshots of every view live in `docs/screenshots/`.

## 4. Package layout

```
com.eia.superdwarfkart
├── app/          App (JavaFX bootstrap), AppState, AppConfig
├── model/        Song, Genre, ModeId, Racer
├── ds/           CircularDoublyLinkedList<T>, SimpleQueue<T>,
│                 BinarySearchTree<T>                        ← graded core
├── playback/     PlaybackMode (interface), AbstractPlaybackMode,
│                 ShuffleMode, ArrivalOrderMode, AlphabeticalMode, Player,
│                 PlaybackEngine (running order <-> audio output)
├── audio/        AudioSource (interface), LocalFileAudioSource, PcmFormat, MonoPcmReader,
│                 PcmListener, Levels, LevelAnalyzer, SmoothClock, AudioMetadata, AudioException
├── analysis/     BeatmapAnalyzer, Beatmap, BeatmapCache, OnsetDetector, Fft, BeatmapService,
│                 BeatmapIndex
├── game/         RunnerGame, RunnerListener, Course, Lane, Entity (sealed), Obstacle, Coin,
│                 Star, EntityState, ScoreKeeper, Rank, ScoreEntry, SpeedClass
├── persistence/  Repository<T> (interface), LibraryRepository, ScoreRepository
├── assets/       AssetRegistry, SpriteSheet, SpriteAnimation, RacerFrame
├── mood/         Palette, PaletteRole (enum), GbaColor            ← built in M4
│                 Mood, MoodLayer, GradientLayer, ImageLayer,
│                 ProceduralLayer, PixelTile, MoodRepository,
│                 PaletteImporter, ImageQuantizer, MoodValidator   ← M11
└── ui/           MiniPlayerView, FullscreenView, LibraryView, BeatmapTimeline, RunnerView,
                  RacerSelectView, LevelMeterView, ComplexityPanel,
                  MoodCustomizerView, PixelEditorView, MoodOverlayRenderer
    └── visualizer/  StructureView (base) -> RoadView (base) -> CircuitView,
                     StraightView;  BstView extends StructureView directly,
                     StructureVisualizer (swaps them),
                     OperationCounter, Measurement, ComplexityScatter,
                     StructureComparison, PresentationView
```

**`game/` holds no JavaFX and `ui/RunnerView` draws it.** Ground rule 3 does not list `game/`, and
the runner is documented as a `Canvas` — but splitting them anyway is what makes the collision
rules, the scoring and the generator testable by handing them a sequence of times, with no window,
no sound card and no beatmap. `RunnerGameTest` and `CourseTest` are that split paying for itself.

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

### As built (2026-08-12, M5)

- **Reaching the one format takes *two* conversions, not one, and one silently is not enough.**
  A decoder declares its output at the file's own rate and channel count, so a 22 kHz mono MP3 can
  be asked for 16-bit PCM but **not** for 44.1 kHz stereo 16-bit PCM — that call just fails. The
  plain PCM→PCM providers *do* resample and *do* mix channels (measured against the resolved jars,
  not assumed: 22050 mono → 44100 stereo comes out at 176 416 bytes for a second, the 16 spare
  bytes being the resampler's edge handling). So `convertToPlaybackFormat` tries one step, and
  falls back to decode-then-resample. Almost every real file takes the one-step path.
  **Do not "simplify" this back to a single call** — it works on every file you own and breaks on
  the first 22 kHz one somebody else brings.
- The tap therefore sees **exactly one format, sample rate included**, which is what M6's window
  1024 / hop 512 and M7's lookahead are entitled to assume. `LocalFileAudioSourceTest` pins it with
  a 22 kHz mono file whose byte count at the tap can only come out right if both stages ran.
- **`position()` reads `getLongFramePosition()`**, offset by a snapshot taken after each flush —
  frames the card actually *rendered*. Counting bytes written runs a whole buffer ahead of the
  music; counting frame deltas drifts. This is the clock M7 must drive everything from.
- **Seeking decodes from the start of the track.** Linear, and it has to be: a compressed stream
  has no byte offset corresponding to an instant. Which is exactly why the seek bar commits on
  `valueChangingProperty` going false and never per pixel.
- **The playback thread retires itself before announcing the end of a track**, and clears the field
  by *identity*. Announcing first means the handler — whose whole job is to start the next song —
  finds a pump apparently still running, starts nothing, and the running order stops dead one song
  in. Left dead in the field, the same thing happens permanently.
- **`play()` on a track that already finished rewinds first.** An exhausted stream has no bytes
  left, so without this the button starts a thread that immediately reads end-of-file. It looks
  exactly like a broken button and nothing is logged.
- **`PlaybackEngine` (in `playback/`) is where the running order meets the sound card**, and it is
  a separate class so that `Player` stays pure navigation and stays testable without a device.
  It holds `playWhenReady` — what the *user* last asked for — because "is it playing" is
  momentarily false at every transition for reasons that have nothing to do with intent.
- **End of track checks `canGoNext()` before moving, not the result of `next()` after.** A drained
  queue leaves its last song current and answers `next()` with `null` *but still notifies*, and
  reacting to that notification reloads the song that just finished and plays it again, forever.
  `PlaybackEngineTest` pins this, and the tree's equivalent: the last song alphabetically ends
  playback, where the ring correctly wraps.
- **Silence is published to the taps whenever output stops** — paused, sought, replaced, played
  out. Without it the meters hold the last block and a paused player sits there showing level.
- **The meters' colour ramp is cached against the palette by identity.** Two bars of ~70 blocks at
  60 fps is 8 400 interpolations a second, each snapping back onto the 5-bit grid, for a ramp that
  changes only on resize or mood switch. A `Palette` is immutable and switching mood installs a
  different object, so the cache invalidates itself when M11 arrives.
- Meters are drawn on a **logarithmic scale, −60 dBFS to full**. Linear, a bar sits in its bottom
  tenth all song and every song looks identical.
- Volume is a `MASTER_GAIN` control and is **silently ignored** where the card exposes none. The
  system volume still works; failing over it would be absurd.

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
- **Wall** — a row of obstacles across **all three lanes**, placed on the track's **accented**
  beats. There is no lane to change into, so the only way past is the jump. This is what makes the
  jump a control the player has to learn rather than one they can ignore for a whole song, and it
  is the reason the analyser records a strength per beat at all.
- **Star** — uncommon, on a beat, seeded like everything else, and **spaced at least 15 s apart so
  every class actually gets some**: at the first tuning a four-minute track at 50cc got none at all,
  which made the invulnerability, the explosion and the break bonus three features nobody ever met.
  `CourseTest` now asserts every class puts at least one on a long track. Grants invulnerability for
  N beats;
  passing *through* an obstacle while starred **breaks it**, plays the 2-frame explosion and
  awards bonus coins. The star sheet is animated — slice and loop it.
- **Entities are drawn far to near, and the loop runs *backwards* for that reason.** The course is in
  ascending beat order, so a low index arrives sooner — which is the entity closest to the racer and
  lowest on the screen. Walking up from `firstVisible` therefore drew the nearest first and let every
  farther entity paint over it: a coin still up at the horizon clipped through the bump about to hit
  you. Descending is the painter's algorithm the receding road actually needs. Resolved entities fall
  out of it for free — their effects belong on top of everything, they hold the lowest indices, and a
  descending walk draws them last.
- **Every wall's hazard band is laid down before any sprite**, in a pass of its own. A wall is three
  obstacles sharing one beat and each asks for the band; drawn inline with its own sprite, the second
  obstacle's band painted over the first obstacle's sprite and the third over both, so the bumps
  looked cut off at the ankles. The band is opaque and identical for all three, so hoisting it into
  its own pass costs nothing and makes the overdraw invisible, which is what it was always meant
  to be.
- **Effects are drawn after the kart, not with the other entities.** Every one of them happens *at*
  the racer — the coin pop, the explosion, the coins a bump scattered — and the kart is nearly two
  hundred pixels of opaque sprite standing exactly there. Drawn before it, an explosion is a ring of
  light peeping out from behind the thing it supposedly hit, which reads as the effect going off
  somewhere else. `drawEffects` is its own pass for this reason.
- **The beat effect's length is a share of the track's own beat, not a fixed number of seconds.**
  A fixed length has to be chosen for some tempo and is wrong at every other: at the old 0.18 s a
  90 BPM track barely flickered between beats while a 175 BPM track never returned to normal at
  all — the screen sat permanently part-washed and it stopped reading as a beat and started reading
  as a haze. `pulseSeconds()` takes 62% of the period, clamped to 0.14–0.38 s, falling back to the
  fixed figure when no tempo was established. Same reasoning as the star's life in beats and the
  wall's warning as a fraction of the travel time.
- **The wash has a strike *and* a release.** Two lobes over one envelope: a squared dip toward
  `SHADOW` that is hardest at the beat and lets go fast, then a hump peaking halfway through the
  decay that lifts past normal toward `TEXT_PRIMARY` before settling. Darkening alone reads as the
  picture being dimmed — it has a beginning and no end — and it is the release that makes the beat
  legible out of the corner of an eye while the player is watching the road. The lift is smaller
  than the dip because brightening a palette this dark washes the road out far faster than
  darkening it hides anything.
- **The camera punches in on the beat, and that is not the thing this file forbids.** `beatZoom` is a
  uniform scale of the finished frame about the canvas centre — 5.5%, squared so it is sharp
  and gone, and only ever inwards so the canvas edges stay covered. The version that failed grew the
  *road's width*, and the reason that failed is worth stating precisely: the road's width is an input
  to the projection, so changing it moved every entity relative to the lane lines and to each other.
  Where a thing was stopped meaning when it would arrive, which is the projection's only job. A
  uniform scale moves every pixel by the same factor, so nothing moves relative to anything else and
  the lookahead still reads as timing; what changes is how much of the frame you can see, which is
  what a camera is. The head-up display, the banner and the washes are drawn **outside** the
  transform — text must not scale, and a full-canvas wash must stay full-canvas.
- **Beat feedback: washes over the picture, and never the geometry.** Three of them now, all drawn
  over the finished frame and *under* the head-up display, so none can make a score unreadable:
  - the **beat wash** dips the whole screen towards `SHADOW` on a strong beat, squared so it is a
    brief dip rather than a throb across the beat. It **darkens**; a light wash over this palette
    washes out the road and hides the entities the beat has just placed on it.
  - a **pickup lights the screen `PRIMARY`** — the palette's yellow, the same role the coin counter
    is drawn in — and fades. A star does it for twice as long, a broken bump for the same as a coin.
  - **a bump lights it `NEGATIVE` and pulses** three times before it goes (fading envelope × a
    rectified sine, so it beats and dies rather than strobing and then vanishing mid-flash). A
    single fade reads as a change in the light; a few beats of it read as an alarm, which is what a
    bump is, and it is the one event the player may have missed the cause of.

  All three fire from `RunnerListener` callbacks, so each happens exactly once per event rather than
  being deduced per frame by diffing `EntityState` — which is wrong the first time a frame is
  dropped. They are timed off the game clock, so a pause freezes them with everything else.
- **The star cycles the kart through the palette, and the rainbow is made of roles.**
  `RunnerView.RAINBOW` walks `PRIMARY → POSITIVE → METER_LOW → ACCENT → HIGHLIGHT → NEGATIVE`,
  mixing between neighbours and snapping back onto the 5-bit grid. Reaching for `Color.hsb` would
  have been shorter and would have put six colours into the runner that no mood can reach — exactly
  the debt ground rule 7 exists to prevent, and it would leave the star looking like it belonged to
  a different game the moment M11 lands. This way it restyles itself for free. The kart itself is
  tinted with a hue-cycling `ColorAdjust` on the one sprite, for the eight beats the star runs;
  **moods still never tint artwork** — a power-up saying so is not a theme.
- **The star's halo is drawn as blocks, not as an ellipse** (`fillPixelOval`). A smooth
  anti-aliased oval was the one shape on that screen that gave away a modern toolkit: everything
  else is hard-edged and the eye finds the odd one out immediately.
- **`explode` clamps the frame index rather than letting it wrap.** `SpriteSheet.viewport` takes the
  index modulo the frame count, which is right for a loop and wrong for an animation that plays
  once: at the last instant of the effect the index reaches `frameCount` and a two-frame explosion
  snapped back to its first frame for a single frame as it faded.
- **Beat feedback: a flash, and never the geometry.** The horizon line flashes on each strong beat
  and the lane edges glow with the live L/R RMS — that is where the metering and the game visibly
  meet, and it is the reason the meters are not just decoration. **The road's width and its scroll
  rate are functions of the speed class alone.** An earlier version swelled the road a few percent
  per beat, as the brief originally said, and it was wrong: the whole picture pumped, the kart
  appeared to lurch, and the motion stopped reading as driving. Speed is a constant; what the music
  does is put things *on* the road — coins, stars, and a wall on the big hits.
- **Rank** = coins collected as a percentage of coins *available in that generated course* →
  S / A / B / C / D. `ScoreRepository` persists the best score per `(songId, speedClass)` to
  `~/.superdwarfkart/scores.json`; the library view shows a rank badge per song, and marks
  which songs have a course ready.

### The three visualizer views (M4)

Each is a `Canvas` sharing the game's sprite set. One view per mode, swapped by `AppState`.

**Two of the three are roads.** The circular list and the queue are both sequences, and a sequence
reads best as a side-on road: songs on roadside markers in order, the racer driving it, the surface
sliding underneath. `RoadView` holds all of that — road, scrolling stripes, signs, camera, burst,
racer, mini-map — and the two subclasses contribute only **which moves are possible**, which is
exactly what distinguishes the structures. The tree is not a road and does not extend it.

**On a road the racer never stops** (wheels turning, surface scrolling, gentle bob) because he is
travelling through a running order the whole time; a song change is a *burst on top of* that.
**In the tree he freezes when parked**, because stopping at a junction means something there. That
split is deliberate: `StructureView.racerFrame()` freezes on a still frame and `RoadView` overrides
it to always cycle. Don't call `RacerFrame.driving()` from a view directly — go through
`racerFrame()`, or that view will keep spinning its wheels in a car park.

The view clock (`clockSeconds()`) **survives the timer stopping** — it banks elapsed time on stop
and resumes from it. Anything phase-based reads that clock, the scrolling road most of all, and
restarting from zero snaps the road to a new phase every time the racer sets off.

**The mini-map is not decoration.** A strip of `+ -> + -> (+)` above the road draws the *structure*
next to the songs it is holding, with the racer's node ringed. Without it the view is a list of
songs on posts and the shape is left implied. On the ring it closes with a return arc — the same
shape the racer flies when he comes round — so the circularity is on screen even while he is
driving a straight.

The view clock (`clockSeconds()`) **survives the timer stopping** — it banks elapsed time on stop
and resumes from it. Anything phase-based reads that clock, the scrolling road most of all, and
restarting from zero snaps the road to a new phase every time the racer sets off.

**`CircuitView` — the circular list as a road that can be driven both ways.** The same straight the
queue uses (`RoadView`); the differences *are* the structure's:

- **He can go back.** `previous()` reverses him down the road one marker in one move — mirrored
  sprite, camera following him down — because the list is doubly linked and stepping back is a
  single pointer hop. A singly linked ring would have to drive the whole lap to get there.
- **The road has no end.** Driving off the last marker he takes an **up-turn, flies back over the
  road and lands on the first**, and the return is a blur. Reversing off the first does the same
  the other way. Circularity is demonstrated, never explained.

**The loop back must stay a whip, not a journey.** Past the tail is the head and it costs *one
pointer hop*. A camera that panned back down the road at an even pace would be drawing O(n) for an
O(1) operation — the picture would claim the wrong complexity. `whipFraction` covers the whole
road in a quarter of the move, and `RoadViewMotionTest` asserts that it does.

**`StraightView` — the queue as a side-on race down a straight.** The road runs left to right
with the waiting songs on roadside markers along it, and the racer drives it in **side view**
(sprite frames 0/1).

**The racer holds his mark and the road moves under him.** He rides up and down over the surface
and the kerb stripes and centre line slide past, so he reads as driving even when the queue is
untouched — a sprite pinned to one spot reads as a sticker on the glass however good the art is.
What scrolls is the *surface only*; the song markers stay pinned, because those are queue
positions and a queue position does not drift.

On `dequeue()` he floors it, pulls away and **leaves the frame to the right**; the camera holds a
beat, gives chase, and eases to a stop with him back on his mark as the next marker arrives at
the front. It **catches up — it never cuts.** The camera **only ever moves forward**, and that one
rule is what a FIFO looks like: played songs slide off the left and no shot brings them back,
because no operation would. Head and tail are flags on the roadside. The previous control is
visibly disabled here, tooltip *"FIFO — no going back."*

Two things this view has already been got wrong on:
- Marker positions are **absolute**, from a running count of songs that have left the queue. Laid
  out relative to the racer instead, the whole road slides forward underneath him every time he
  sets off and he never appears to get anywhere.
- The lead he opens up is **screen-space**, not a change to his queue position — one dequeue is a
  step, not a distance, and no honest slot spacing both clears a 400px panel *and* leaves room to
  show what is coming up. `burstFraction` / `chaseFraction` are pure functions of progress and are
  unit-tested (`StraightViewMotionTest`), because a still frame of a scrolling road is a static
  road and no screenshot can check any of this.

**`BstView` — the tree as a neighbourhood seen from above.** Nodes are buildings, the parent
links are **streets** (kerb, asphalt, dashed centre line), and a traversal is **the racer driving
across town**. In-order x-position, depth y-position; pan/zoom once it outgrows the canvas. Each
node shows its title, the current node is highlighted. **Drive the path, not just the result:**
on `next()` the kart takes each traversed street in sequence — descend right then run to the
minimum, or climb through parents until arriving from a left child; `previous()` mirrors it. The
sprite is chosen from the direction of travel: the **rear view (frame 2)** heading up or down the
map, the **driving cycle (frames 0/1)** heading across, mirrored when going left. The kart parks
*above* the building it is beside, never centred on it — centred, it covers the very title the
node exists to show. It is drawn at 1:1 here even on the full stage: this is a map, and a vehicle
on it should be smaller than the blocks it drives between. Includes a **slow-motion /
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

### As built (2026-08-12, M4)

- **`StepCounter` gained `begin`/`end`, and `Player` brackets the structure calls itself.** The
  scope has to close *before* the listeners run: redrawing the bar peeks at the next song, and in
  a tree a peek is a whole successor walk that would otherwise be billed to the navigation that
  triggered it and roughly double every number on screen. `PlayerInstrumentationTest` pins this.
  Steps counted outside any scope are discarded when the next one opens, and a nested scope folds
  into its parent rather than overwriting it.
- **The three modes spell four operations identically** — `next()`, `previous()`, `select(song)`,
  `build` — because the panel puts the measured count on the row whose key matches. Getting this
  wrong throws nothing; the row just silently never shows a measurement, so
  `PlaybackModeContractTest` asserts the names. The structural detail moved into the *value*
  (`next()` is O(1) over a queue and O(log n) over the tree), with the structure named above the
  table.
- **A measurement is only shown against the structure that produced it.** One counter serves
  every mode, so it still holds what `previous()` cost over the ring after a switch to the queue —
  printing that beside "not supported" would read as the queue having walked backwards.
- **`build` is excluded from the scatter** (`ComplexityScatter.BULK_OPERATIONS`). A build is n
  operations, not one; leaving it in pins the y scale so high that the log n and n curves collapse
  onto each other, which is the one thing the plot exists to separate. It still gets its row.
- `BinarySearchTree` gained `successorPath` / `predecessorPath` / `searchPath` and a read-only
  `NodeRef`. The paths are the **real** walks recorded rather than reconstructed — the same code
  with a trace list threaded through it — so the animation cannot drift from the navigation.
  A walk with no successor returns **the climb to the root**, not a single node: that climb is
  work the structure genuinely did, and the view shows it happening.
- **`StructureVisualizer` is the one class that names the concrete modes**, by pattern-matching
  switch. Unavoidable and legitimate — a view of a circuit *is* a view of a ring — and an
  unrecognised mode gets a message rather than an exception.
- **The tree does not fit-to-canvas by default.** Thirty nodes laid out in order are ~2900 px
  wide, and fitting that into the 400 px panel lands at minimum zoom where the tree is a grey
  smudge. It comes up at 1:1 centred on the current song, and reframes when the canvas changes
  size by more than 1.5× — which is exactly what entering Presentation Mode does. `FIT` remains
  the deliberate whole-shape action.
- **Layout: the visualizer and the complexity panel share one 400 px left column, stacked.** Side
  by side they left the library table ~470 px, and at one em per glyph that truncates every song
  title to three characters. Presentation Mode gives the visualizer the height back.
  **And `F4` folds the column away entirely** — see below; the stacking recovered enough width for
  the table to be read, the fold is for when the structures are not what the user is doing.
- Presentation Mode is **F5** (Escape also leaves), wired as a scene event filter rather than an
  accelerator so the tree keeps receiving space and the arrow keys for its step-through. The
  visualizer node is *moved*, not duplicated, so pan, zoom and any walk in progress survive the
  trip in both directions.
- The BST control bar is a **`FlowPane`**: five controls do not fit across 400 px, and an `HBox`
  answers that by truncating every label to `INS...`. It wraps in the panel and lays out in one
  row on the full stage.

### As built (2026-08-12, M6)

**Measured on the two real tracks in the library** — `Crimewave` (4:18, dense electronic) locks to
**120.0 BPM with the beats sitting 12.1 ms off the grid**, 826 onsets of which 461 are on the beat;
the 8-second percussion sample gives 119.8 BPM at 6.0 ms. Analysis of the 4-minute track takes
about a second. Re-run those numbers with `-Dsdmk.smokeTest=true` — the smoke test prints them.

- **Playback and analysis decode through one class, `audio/PcmFormat`.** The two-stage conversion
  documented in §6 moved there out of `LocalFileAudioSource`. This is not tidying: the analyser
  measures times in frames and the playback clock counts frames, so if the two ever resolved a file
  to different sample rates every onset would be reported at the wrong instant and nothing would
  look wrong anywhere. `MonoPcmReader` is the analyser's way in and sums to mono, which keeps every
  `javax.sound` import inside `audio/`.
- **`Fft` is hand-written** — the platform has none. Radix-2, iterative, in place, with the twiddles
  and the bit-reversal computed once per instance; a 4-minute track is ~20 000 transforms.
- **Onsets are detected *before* they sound, and the compensation is not optional.** An attack falls
  inside exactly two Hann-tapered windows, and the flux peaks in the earlier one unless the attack
  sits in its last fifth — so the reported window start precedes the attack by 312 to 823 samples.
  `DETECTION_LEAD_SAMPLES` adds back the midpoint, leaving about ±6 ms. Drop it and every entity in
  the game is a consistent 13 ms early: invisible in a screenshot, loose to play.
  `OnsetDetectorTest` measures the residual rather than trusting the derivation.
- **`DEFAULT_SENSITIVITY` is 3.0, measured against real audio, and 1.5 was wrong.** Tempo comes out
  the same at every setting; how tightly the beats sit on the grid does not — 44.2 ms at 1.5,
  20.6 ms at 3.0, 13.8 ms at 4.0. Below 3 the surplus is texture rather than attacks and it drags
  the grid off the beat; above 4 the onset rate falls so far that 200cc (which spends the
  *intermediate* onsets) would stop differing from 150cc. 3.0 also clears the flux ripple a
  sustained tone produces on its own, measured at 2.78× its own local mean — which is what stops a
  quiet ambient passage from generating a course full of entities corresponding to nothing audible.
- **The tempo octave is the trap, and it has two separate causes.** The considered range spans more
  than a factor of two, so 174 and 87 are both in it and neither folds onto the other.
  1. *Every pair voting equally.* The gap to the next onset votes 174, but the gaps to the second
     and the fourth both vote 87 — the half-tempo wins two to one. Votes are weighted `1/distance`:
     adjacent onsets are direct evidence, a gap spanning three may be spanning a missed beat.
  2. *Quantisation spread.* Onsets sit on hop boundaries, so the gap between adjacent beats
     alternates between whole hops either side of the true period. In BPM that spread grows with the
     **square** of the tempo — 1.6 BPM at 90 but nearly 6 at 175 — so a fast fundamental arrives
     smeared across six bins while its half, being twice as long and half as sensitive, lands in
     one. `SMOOTHING_BINS` is 3 for exactly this, and `refine` must use the same span: at the top of
     the range the winning bin can hold no votes at all.
- **The histogram cannot be precise enough on its own, because the error accumulates.** Its bins are
  1 BPM wide and it lands within a few tenths — which is two thirds of a second of drift by the end
  of a 4-minute track, most of a beat. So the histogram only establishes *which* tempo, and
  `lockOnto` then fits the period to every onset at once by maximising how concentrated their beat
  phases are. That fit took `Crimewave` from 55.4 ms of drift to 12.1 ms.
- **The fit is believed only when it beats chance.** `n` unrelated angles still have a resultant of
  about `1/sqrt(n)`, so the score means nothing until multiplied by `sqrt(n)`. The case this guards
  is a track running at twice the tempo it folded onto: half its onsets sit at the start of the
  folded beat and half exactly halfway, they cancel, and the fit locks onto noise. Measured, that
  case scores 0.16 where every genuine fit scored 6.3 to 6.8 — so the bar sits at 2.0 and anything
  under it keeps the histogram's answer.
- **`strongBeats` are real onsets nearest the grid, never grid points.** A bar the track drops out
  of produces no beats, so the game cannot spawn four entities onto silence. Tested.
- **`Beatmap.gridDeviationSeconds()` is the confidence measure and is worth more than the tempo.**
  A detected tempo is always a plausible number; a grid the beats actually sit on is not. A correct
  grid reads a few milliseconds, a wrong one approaches a quarter of the beat. Measured as an
  **angle**, so a beat on the bar line is a small deviation rather than a whole beat.
- Cache at `~/.superdwarfkart/beatmaps/<sha256>.json`, keyed by **content** so moving or renaming a
  file keeps its analysis, and invalidated by `ANALYZER_VERSION` so improving the detector cannot
  leave the game on courses the old one built. A corrupt or stale entry is a **miss with a warning**,
  never an exception.
- **`BeatmapService` is polled, not a callback.** Same arrangement as the level meters: one
  immutable `Status` in one volatile field, so a reader always sees a matched set and neither side
  waits. A request returns immediately — blocking for the second an analysis takes would drop 90
  frames at every song change. A superseded analysis is still **stored** before being discarded; the
  work is done, and the user may come back to that song.
- The debug view is `ui/BeatmapTimeline`, a strip under the playback bar. **The lamp is the part
  that matters** — it flashes on each strong beat as the playhead reaches it, which is the only way
  to tell that the detected beat is the beat being heard. Neither a unit test nor a screenshot can
  establish that. It repaints only when the picture would differ (the playhead of a long track moves
  one pixel about six times a second), because redrawing a thousand ticks at 60 fps to show nothing
  new would cost most of a core.

### As built (2026-08-12, M7)

**Measured on `Crimewave` (4:18, 120.0 BPM, 826 onsets, 461 on the beat).** The four classes place
66 / 119 / 235 / 454 coins, 33 walls each, and 1 / 4 / 11 / 11 stars, at 2.22 / 1.59 / 1.23 / 1.01
seconds of lookahead. The event counts follow the density rule exactly — every 4th strong beat, every
2nd, every one, and the deduplicated onset list — less whatever the walls' clear road takes out. The
smoke test prints all of it and re-derives it every launch.

**Walls, and the accents that place them** (added the same day, after the first pass):

- **`ANALYZER_VERSION` is 2, and the bump bought one thing: a strength per strong beat.** The beats
  themselves did not move — the same track still reads 120.0 BPM, 826 onsets, 461 on the beat. What
  is new is *how big* each of those beats was, which is what the game needs to know where a wall
  belongs. Old cache entries are a miss and re-analyse in about a second.
- **Strength is the ratio to the local mean, not the raw novelty.** Raw novelty tracks how loud the
  music is, so ranking by it would call every attack in a loud chorus big and none in a quiet verse
  — the opposite of useful, since a quiet passage's snare is just as much of a landmark. The ratio
  asks how far the attack stood above *its own* surroundings, which is already the quantity the
  sensitivity is compared against, so a strength of 6 means literally "six times its neighbourhood".
- **The accent threshold is a percentile of the track's own beats**, the top fifth
  (`Beatmap.ACCENT_FRACTION`), never a fixed number — a sparse recording's snare towers over its
  surroundings where a dense mix's does not, and a fixed bar would wall one track solid and leave
  the other bare. A map with no strengths recorded returns an **infinite** threshold, so it gets no
  walls rather than a wall on every beat.
- **Measured: 33 walls on the 4:18 track**, one every ~7.8 s, and the same 33 at every speed class —
  a wall is on the music, not on the difficulty. Nothing else is placed within 0.6 s either side, so
  the jump is never also a lane change, and the scripted lap still ranks S at 50cc and 100cc.
- **Wall-ness is part of the course**, not something worked out by looking for three obstacles at
  one instant: `Obstacle.isWall()`, compared by `equals`, so two courses that differ only in it
  compare different. `Entity.equals` stays final and subclasses extend it through `sameDetails`.
- The view draws a **hazard band across the whole road** behind the three sprites — three bumps in a
  row read as three bumps you might squeeze between — and shouts **JUMP!** from 45% of the way down
  the lookahead. That is a fraction of the travel time rather than a number of seconds, so it warns
  the same *distance* ahead at every class. Shouting it is not a crutch: a wall has no decision in
  it, only timing.

- **The runner is seen from behind the kart, down a road that recedes to a horizon**, and a
  **sprite only ever draws at 1x, 2x, 3x or 4x**, snapping between them as it comes in. That is not
  a compromise between perspective and ground rule 8, it is what the hardware this look comes from
  actually did, and it is the reason it reads as a GBA game rather than as a 3D engine with
  filtering off.
- **Everything on the road is placed by one curve, and it is not a perspective divide.**
  `screenFraction(progress) = progress^1.25`, where progress is purely how much of the travel time
  has gone. Entities, surface bands, kerbs and lane lines all go through it, so they move as one
  picture and can never slide against each other; the road's half-width is then simply proportional
  to how far down the screen it is, which is exactly right for a straight flat road and needs no
  divide at all.

  **The first version used a real `1/z` divide with depth linear in time, and it was unplayable.**
  A still frame of it looked like a perfectly good road. In motion, half the travel was spent inside
  the top fifth of the road and the last tenth of it covered 44% of the screen, so an entity hung
  around the horizon and then whooshed past — which is what "everything pools at the horizon" looks
  like, and it is why the game did not read as a rhythm game. Nothing about where a thing was told
  you when it would arrive, which is the only job this projection has. `RunnerProjectionTest` pins
  the replacement: monotonic, spanning exactly horizon to racer, and no tenth of the travel time
  covering more than three times the road of any other.

  **`PERSPECTIVE_BIAS` is the one knob.** 1.0 is Piano Tiles exactly — constant screen speed, where
  a thing is *is* when it arrives. 2.0 is a true perspective divide. 1.25 keeps a visible
  foreshortening while leaving position near enough proportional to time to be read as timing.
- **The road is a few dozen filled trapezoids, not a scanline loop**, and the bands are spaced in
  **time** rather than in depth — fourteen across the whole lookahead — so a band travels exactly as
  an entity does and the surface is visibly faster at the quick classes for free.
- **The depth the projection gives up, the road's own texture pays back.** With `PERSPECTIVE_BIAS` at
  1.25 the road is very nearly a flat ramp, and drawn as plain alternating bands it read exactly like
  that: *"the lines don't look like they are in a 3D space, they look just lines scrolling."* The fix
  cannot be the projection — that carries the timing and `RunnerProjectionTest` pins it there — so it
  is the texture, in three parts, none of which moves anything:
  - **Rungs that grow.** A band is drawn dark across its whole depth and its lit part is a *rung*
    whose near edge is the band boundary it always was, and whose trailing edge is new:
    `rungFraction` takes it from about a third of its band at the horizon to all of it at the racer.
    Only the trailing edge is new, so a boundary still travels at exactly the entity rate and the two
    still cannot slide against each other. The lane dashes are shortened by the same fraction, so a
    dash is a tick at the horizon and a long stroke underfoot. Fourteen bands rather than nine,
    because a third of a band is only a *stripe* if the band is small, and nine left the far half of
    the road with about two boundaries in it.
  - **Haze.** Road, kerbs and ground fade towards `SURFACE_RAISED`, which is what `drawSky` leaves
    sitting against the horizon — so the road fades into the sky it meets rather than into a colour
    of its own, and a light mood lightens where a dark one darkens. **The entities are never hazed**:
    a coin at the far end of the lookahead has to stay readable, moods do not tint sprite art, and an
    unhazed sprite over a hazed road is *easier* to pick out at distance, not harder.
  - **The verge stopped being stripes at all**, and this was the largest single change. It is drawn
    across the whole canvas, so its bands were the biggest shapes on screen by a wide margin —
    full-width bars of near-equal height at full contrast, marching to the horizon. A flat plane's
    own texture *is* full-width, so those bars could never converge, and while they were there the
    picture read as horizontal stripes scrolling behind a triangle however well the triangle itself
    receded. It is now one flat band per step hazed by its own distance: a posterised gradient going
    away from the player, with the road left as the only thing carrying the motion — and the road is
    the thing that gets narrower.

  Two numbers were tuned by screenshot and are worth knowing. `HAZE_MAX` is **0.18** on the road and
  **0.86** on the ground, and the split is not fussiness: the road's own unlit colour *is*
  `SURFACE_RAISED`, so haze cannot make the road recede — all it can do there is dissolve the rungs
  into the surface they sit on. At the 0.62 it started at, the top third of the road came out as one
  flat slab with no texture in it at all, which is worse than the flat road this set out to fix: at
  least that one had stripes. The far rungs are meant to be **thin, not faint**. The ground has the
  opposite problem and can afford to recede hard, because it carries nothing.
- **Measured, not guessed: a frame costs 0.25–0.45 ms**, which the smoke test prints. (It was
  0.15–0.2 before the rungs and the extra bands above; the figure is noisy by about twice across
  runs, so read it as an order of magnitude rather than as a stopwatch.) When the game
  was reported as laggy the suspicion was that every entity on the course was being drawn; the
  measurement said otherwise (three-figure headroom, and the visible window is a handful of entities
  out of hundreds) and pointed at the projection instead. Keep that line — "it feels slow" has
  several causes and they need different fixes.

  **But know what that line does not measure, because it was later trusted too far.** A `Canvas`
  call draws nothing: it appends to a command buffer the render thread rasterises during the pulse.
  So the smoke test's figure is the cost of *writing the commands down*, and a tight loop of
  `redraw()` calls that never yields has the renderer coalesce the lot and paint once. It reports
  three-figure headroom on a game that visibly stutters, by construction. **The number with no blind
  spot is the interval between `AnimationTimer` callbacks** — the toolkit will not start a pulse
  while the last one is still being painted, so the render thread, the layout pass, another view's
  timer and a garbage collection all land in it. That is what `-Dsdmk.diag` reports, and it is the
  number the player is looking at. Measured on this machine: **120 Hz display, p50 8.3 ms, tick
  0.23 ms** — the runner is nowhere near the frame budget and never was.
- **The palette is too dark to draw a road from two adjacent surface roles.** `SURFACE` and
  `SURFACE_RAISED` are a few 5-bit steps apart, and the first version drew a correct road nobody
  could see against the verge. The lit band is `mix(SURFACE_RAISED, TEXT_DIM, 0.22)` — a *distance
  between roles*, not a colour, so it still lifts in a dark mood and darkens in a light one.
- **`audio/SmoothClock` is the fix for the stutter, and it is not the accumulated frame time this
  project forbids.** A sound card reports `position()` in whole buffers: read sixty times a second
  it stands still for several frames and then jumps, and a road drawn straight off it stutters on
  any machine. The clock free-runs on wall time and is pulled back onto the audio position by 8% of
  the error *every frame*, so it cannot be more than a few milliseconds out — an accumulator has
  nothing correcting it and is a beat wrong inside a minute. It snaps past 250 ms (a seek, a new
  song), caps one step at 100 ms (a minimised window), and reports the raw position while paused.
- **`java.time.Duration.toSeconds()` returns a `long`, and it quantised the entire game clock to one
  second.** `engine.position().toSeconds()` compiles, reads exactly right, and is silently widened to
  `double` with the fraction already gone — because the method everyone remembers is
  `javafx.util.Duration.toSeconds()`, which returns a `double`. The two are told apart by nothing but
  the import, and ground rule 3 puts `java.time` on this side of the line.

  Nothing threw and no test failed. What it did instead: the road crawled and then lurched about once
  a second, and clearing a wall stopped depending on when the key was pressed. **Both reported
  symptoms — "laggy driving" and "the jumps are frame perfect" — were the same one line.** Measured
  at 60 Hz against a real track, before and after: the clock advanced **0.9 times a second in flat
  1000 ms steps** and now advances **59.8 times a second in 21 ms steps**; `SmoothClock` went from
  191.7 ms of error and a snap every second to **6.2 ms and no snaps at all**, and the smoothed
  reading's median step went from **2.1 ms per frame to 16.6 ms** — from one eighth of real time to
  exactly real time.

  `SmoothClock` was doing its job perfectly and made the bug *harder* to see rather than easier: fed
  a stale target it pulls back 8% of the error per frame, which settles at a reading that has almost
  stopped moving instead of one that is visibly frozen. A frozen road is a bug report in one word; a
  road running at an eighth speed is "laggy". **`AudioSource.positionSeconds()` /
  `PlaybackEngine.positionSeconds()` are now the only way to read the clock**, and
  `AudioSourceSecondsTest` fails if `toSeconds()` comes back. The only legitimate remaining calls are
  in `PlaybackBar.formatTime` and `LibraryView`, where a clock face wants whole seconds.
- **Pausing the music pauses the game, and nothing is told to.** The clock stops because the card
  stopped, so the entities stop, the road stops and the star stops running out. The two can never
  disagree because there is only one of them.
- **The timing window has a late half, and it did not used to.** A jump covers
  `[pressed, pressed + JUMP_SECONDS)`, so judging an obstacle on its beat accepted any press in
  `(T - 0.45s, T]` and **nothing whatsoever after `T`** — 450 ms of early tolerance and zero late
  tolerance. Human timing error is symmetric about what the player aimed at, so that threw away half
  of every player's attempts, and precisely the half they cannot see themselves making: a key
  pressed a frame late looks, on screen, exactly like a key pressed on time. It reads as a
  frame-perfect input. `RunnerGame.JUDGEMENT_GRACE_SECONDS` is 0.10 and moves judgement that far
  past the beat, so the window is now roughly symmetric.

  Three details make it honest rather than merely lenient. The entity is still **drawn** arriving on
  its beat — only the verdict is late, and a pop that begins a tenth of a second after contact is
  the trade every rhythm game makes. The jump test is an **interval overlap**
  (`clearedByJump`), not a reading of `isJumping()` at the moment of judgement, which would refuse a
  jump that was airborne for the whole arrival and had just landed. And **the lane that counts is
  the lane the racer was in on the beat** (`laneAt`), not the one they are in a grace period later —
  otherwise the grace would quietly punish steering, hitting a player with an obstacle they had
  already gone past. That last one is the trap that comes free with a delayed judgement, and
  `RunnerGameTest.theGraceDoesNotPunishSteering` is what keeps it shut. The grace is applied to
  every entity, not just obstacles, because the resolution cursor walks the course in beat order.
- **The road scrolled the wrong way for a whole milestone.** A surface band's progress was
  `(band - scroll)`, which *shrinks* as the clock advances, so the surface climbed towards the
  horizon while every entity standing on it came down the screen. It is `(scroll - band)` — the same
  direction `progressOf` moves. Nobody could name what was wrong with the picture, which is exactly
  how it survived: a still frame of a scrolling road is a static road, so no screenshot shows it.
  `RunnerProjectionTest` now pins both the direction and that the surface covers exactly the ground
  an entity does in the same time.
- **A collision is resolved at the entity's beat time, not by overlapping two rectangles.** The
  entity is drawn travelling towards a fixed racer over `travelTime`, so it arrives exactly when its
  note sounds. A hit test in screen space would be a test of the renderer's geometry; this is a test
  of the music.
- **A course carries no run state.** Entities are immutable and `RunnerGame` keeps an `EntityState`
  array beside them, so a course can be replayed, screenshotted and driven twice. Put a `collected`
  flag on the entity and the second run starts with every coin already taken.
- **Seeking forward writes off what it skipped.** Anything more than 250 ms overdue resolves as
  `MISSED` without scoring, so a course cannot be collected by dragging the playhead across it — and
  a second of dropped frames cannot silently award whatever was in the racer's lane. Seeking
  backwards re-arms the stretch and deliberately **keeps** the tally: re-scoring would let a run be
  farmed over one good bar, and clearing it would throw a run away because somebody nudged the bar.
- **The lane changes at once; only the sprite glides.** A logical lane that eased across with the
  drawing would count a player who moved as still standing in front of the thing they moved away
  from, and there is no way to explain that to them — the screen showed them moving.
- **The generator makes courses that can be survived.** Two obstacles closer together than 0.30 s
  are forced into the same lane, because no player can get out of two different lanes in that time.
  It is real seconds rather than course units — a human reaction is being allowed for, not a
  distance — which is exactly why the fast classes are harder without ever becoming unfair. Entities
  closer than 90 ms are merged, since an onset can register twice a few milliseconds apart.
- **Coins come in runs.** A lane drawn independently per coin gives a field of scattered dots that
  reads as output; coins within 0.55 s continue the previous lane 60% of the time and read as a
  route somebody chose.
- **The seed is FNV-1a over `songId|CLASS`, defined in `Course` rather than taken from
  `String.hashCode()`.** 64 bits instead of 32, and pinned here so a future runtime cannot change
  the number and silently re-roll every stored score.
- **`ScoreKeeper` keeps two numbers and keeping them apart is the point.** `coinsCollected` never
  goes down and is what `Rank` divides by the course's coins; `coins` is the balance, less 5 a bump
  and plus 5 an obstacle broken. Break bonuses are deliberately **not** in the rank — a bonus that
  was never on the course could otherwise push it past 100%.
- **The rank ignores the speed class and the score does not.** A clean 50cc run is an S and so is a
  clean 200cc one; what separates them is what the coins were worth. Weighting the rank as well
  would put the letter out of reach at the class a beginner starts on.
- **`ScoreRepository` is its own in-memory model**, unlike `Library` and its repository. There is
  nothing for a separate `ScoreBoard` to do — at most one entry per song per class, only ever read
  by key, and every change has to reach disk immediately anyway. A run that earned an S and was lost
  because the window closed is worse than any amount of symmetry. `record` compares first, so
  skipping a track ten seconds in cannot wipe out the complete run before it — which matters because
  skipping is how most runs actually end.
- **The rank is derived on read, never stored.** The file carries a `rank` field for a human reading
  it and it is ignored on the way back in; two counts and a letter that can disagree with them is a
  bug waiting for a hand edit.
- **`analysis/BeatmapIndex` exists because the cache is keyed by content hash.** The library table
  marks songs whose course is ready, and asking the cache per row would read the whole audio file —
  megabytes per frame per row over a scrolling table. The hashing happens once on a background
  thread and `isReady` is a map lookup; answers are invalidated by size and modification time.
- **The library and the runner share the middle of the window, swapped by F6.** At one em per glyph
  the table needs every pixel and the runner needs a road long enough to read a lookahead off;
  neither survives half. It is also the shape the M9 side rail will formalise, where Library is one
  destination among several rather than a permanent fixture.
- **Nothing plays until the user says so.** A dialog on first launch asks, and accepting brings the
  road up on its own — the game appears because a race began, not because a function key was found.
  Leaving the road by hand pins the library, so play and pause stop moving the window under them.
- **The smoke test fires the real keys at the real scene** and prints what the kart did, because a
  routing fault lives entirely in how the scene delivers an event: no screenshot shows it and no
  unit test reaches it. It caught the dead jump the moment it was added, and it caught the stale
  playing-flag straight after.
- **A jump lifts the kart 130 px, not the 54 it started at**, with the shadow shrinking and pulling
  away and a puff of dust at the take-off point. On a 190 px kart the original lift was a bob
  indistinguishable from the sprite sitting still — the control worked and read as broken, which is
  the same bug as not working.
- **The controls appear in the middle of the road for three and a half seconds when the music
  starts**, then fade on their own. The corner line is a reminder for somebody who already knows;
  this is the one moment somebody who does not is both looking at the road and about to need them.
- **`Fonts.pixel` caches by size.** It was calling a `synchronized` loader and the platform font
  lookup on every text draw, several times a frame, across four canvases.
- **A bump explodes and scatters the coins it cost.** Each dropped coin's arc is a pure function of
  the obstacle's beat time and its index — thrown, scattered, pulled down — so there is no particle
  list, nothing allocated per frame, and the same bump scatters the same way every run. A number
  ticking down in the corner is arithmetic; coins on the floor are a mistake.

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
| `Cartridge.png` | 500×575 | 1 frame — the companion window's body; **not pixel art**, see below |

**A racer sheet's four frames are not four steps of one animation** — see `assets/RacerFrame`:

| Frame | Shows | Rule |
|---|---|---|
| 0, 1 | the driving cycle, side view | **the only two that may loop** |
| 2 | the kart from behind | for driving away, or across a map seen from above |
| 3 | static icon | menus, labels **and the racer-select portrait**; never animated |

Looping all four — which is what a generic `SpriteAnimation` over the sheet does — flickers the
kart between a side view, a rear view and a menu icon several times a second. Ask
`RacerFrame.driving(seconds)` for the cycle rather than counting frames by hand, and mirror the
sprite (`drawSprite(..., flipped)`) to face left; the art is drawn facing right only.

**`Cartridge.png` is the one asset in the project that is not 8-bit pixel art, and it is scaled
smoothly for that reason.** Measured: 500×575, **1830 colours, per-pixel grain across the whole
body, not a single flat block** — a high-resolution illustration of an object, in the same category
as album art, which this application has always scaled to fit. Ground rule 8 is about hand-drawn
sprites, where interpolating between pixels placed one at a time reads as bad artwork; drawn at a
whole number this would be 500px wide in a 224px window, and drawn at a fraction with smoothing off
its ridges alias into moiré. Every real sprite — the record, the kart, everything in the runner —
still goes through an integer scale with smoothing off. **Check this before adding the next asset
that "looks like" a sprite**: `getcolors()` and a run-length scan answer it in seconds.

**Where its label is, is measured rather than written down** — `SpriteSheet.darkRegion(frame)`
returns the bounding box of the dark panel, which for this cartridge is 238×389 at (204, 3), i.e.
48% of its width. It is **refused** rather than guessed when the result is not panel-shaped: under a
fifth of the frame, over 0.7 of it, or a box the dark pixels do not fill. That bar is what stops the
magenta placeholder — which is 88% near-black backing — from being reported as a perfectly good
panel and having the song laid out over a missing-artwork marker.

Still missing: background, obstacle/bump. These must resolve to magenta placeholders without
breaking anything, and as of M7 they do — the runner draws a magenta X where a bump should be and
stays entirely playable.

**Racer-select portraits are not missing and never were: they are frame 3 of each racer's own
sheet** (`RacerFrame.ICON`), which is exactly what that frame is drawn for and why it is the one
frame that must never be animated. All five racers already have one. So M9's select screen draws
`assets.racer(racer)` at `RacerFrame.ICON` and needs no portrait artwork at all; `AssetKind.SELECT`
covers only whatever backdrop or furniture that screen wants, and its absence is not a missing
portrait. Do not put a portrait file per racer on the art to-do list.

**`Explosion.png` arrived and needed no code at all**, which is the asset layer working exactly as
designed. It is 64×32; square-frame inference read it as **2 frames of 32×32** with no manifest
entry, and `RunnerView.explode` already spread whatever count it found across the effect's life. The
only change it forced was a bug fix it exposed: `explode` now **clamps** the frame index instead of
letting `SpriteSheet.viewport` wrap it, because at the last instant of the effect the index reaches
`frameCount` and a two-frame explosion snapped back to its first frame for a single frame as it
faded. A longer sheet drops in the same way, including into `~/.superdwarfkart/assets/` with no
rebuild. The **wall's hazard band and the flash under a bump are drawn from the palette, not from the
sheet**, so a bump reads as a bump with no artwork at all and gains the sprite when it arrives.

**A test that fails when artwork *arrives* is testing a folder, not the code.**
`AssetRegistryTest.missingKindIsReportedNotThrown` used to name the explosion as the kind the project
had not drawn yet, and it broke the day the file was dropped in — the one day the asset layer was
behaving perfectly. It now asks the question that is actually the rule, of every `AssetKind` there
is: each must resolve to a drawable sheet with at least one frame, art or no art. Do not write the
next one against whatever happens to be missing today.

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

**The runner's beat effects already fall under this and must be wired to that switch when it is
built.** `beatZoom`, the dip-and-lift wash and the pickup/bump flashes are all full-screen, and on a
120 BPM track the beat ones fire at 2 Hz — inside the 3 Hz cap, but only because the cap was
respected by luck rather than by a check. "Reduce motion" must reach `RunnerView`, not only `mood/`.

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

### As built (2026-08-13, M9)

**The palette reaches the controls, and that is most of this milestone.** `app.css` went from 60
hexadecimal literals to three. It now names `-role-*` (the sixteen) and `-ui-*` (surfaces derived
from them) and defines neither; `mood/PaletteCss` renders the active palette as a stylesheet and
`ui/Theme` installs it. Switching mood restyles every button, table, dialog, tooltip and scrollbar
along with the canvases, which is the whole point — the canvases already followed the palette and
would have been the only things that did.

- **It is a stylesheet, not an inline style on the root, and that is not a detail.** An inline style
  is the shorter way to install looked-up colours and it silently misses every popup: a tooltip and
  a combo box's drop-down are **their own scenes with their own roots**, so a definition on the main
  root never reaches them and every colour in them fails to resolve. A stylesheet is carried to
  popup scenes exactly as `app.css` already is. It is a `data:text/css;base64` URL, so a different
  palette is a different URL by construction and there is no file to invalidate.
- **`Theme` remembers every stylesheet list it has styled, weakly.** It was already the one funnel
  every window went through; it is now also how a mood switch reaches a dialog that is open at the
  time. Weakly, because a dialog is styled, shown, closed and dropped, and holding them strongly
  would keep every dialog of the session alive to restyle windows nobody can see.
- **The derived surfaces are computed in Java rather than with the CSS `derive()` function, so that
  they can be tested.** A bevel needs a face, a lit edge and a shadowed edge; a palette holds
  sixteen colours and must not spend three on that. The check that earns the whole arrangement is
  `bevelKeepsItsDirection`: the lit edge must be **brighter than its face and the shadowed edge
  darker, in every mood**. Lightening by mixing towards white passes in a dark mood and produces a
  bevel with no lit edge at all in a light one, where the face is already near white — every button
  in the application goes flat and nothing anywhere says so.
- **Three derived surfaces had to be re-derived once the light mood existed**, and each failure was
  invisible in the dark one. `-ui-recessed` took a short step towards `SHADOW` instead of most of
  the way, because "recessed" on paper means a shade below it and not mid-grey. `-ui-face-pressed`
  had to go much further towards the shadow, because in a dark mood hover *lightens* the face and
  the two separate on their own, while in a light mood hover darkens it too and pressed landed
  within two levels of hover. And `-ui-selected` is derived from `OUTLINE` rather than by lightening
  the face, which marks a row in a dark mood and washes it out in a light one.
- **`BACKGROUND_ALT` and `SURFACE` were the same colour**, and the retrofit is what exposed it. They
  differ by less than one 5-bit level, so they snap together — the alternating table rows were not
  alternating. It survived because the stylesheet held its own *unsnapped* literals, which did
  differ; routing the table through the palette inherited the collision immediately.
  `rolesAreDistinct` is the check.
- **A light palette breaks on text, and it broke twice.** `PRIMARY` carries the application name,
  the table headings, the now-playing line and every focus ring; `ACCENT` carries the section
  headings and the playback status. Both are *read*, not looked at. The bright amber and blue that
  are perfect on near-black measured **2.0:1 and 3.3:1** against the light mood's own header band.
  Both were darkened until they cleared 4.5:1 on all three grounds they are drawn on, and
  `accentsAreReadableAsText` now checks it for every mood. **The recessed band is where an accent
  runs out of contrast first** — it is the darkest ground in a light mood and the lightest in a dark
  one — so checking against the background alone would have passed both of these.
- **`SURFACE_RAISED` in the light mood is a warm grey, not white**, and that is the one value in it
  chosen for a mechanism rather than for looks. A white face has no headroom above it, so the lit
  edge lands on the face's own colour. `lightFaceHasHeadroom` says so out loud.
- **The four protected roles are checked by test rather than by the validator, which is M11's.** The
  thresholds are the ones the validator will enforce — WCAG 4.5:1 for text, CIE76 ΔE ≥ 25 for
  `POSITIVE`/`NEGATIVE` and `HIGHLIGHT`/`OUTLINE` — plus a brightness separation, because hue alone
  fails for a colourblind viewer and for a projector with bad gamma, and this project is
  demonstrated through both.

**The side rail formalises the middle of the window.** Library, Favorites, History, Racers, Moods,
Settings. The road is not a destination but an alternative to whichever one is selected, so `F6`
still swaps it in and **leaving a race returns to where the user was** rather than always to the
library.

- **Nothing on the rail is focus-traversable.** A focused toggle answers the space bar, and the rail
  sits earlier in the scene than the header whose two buttons were fixed for exactly this — the
  first space of the session would have changed destination instead of starting the music.
- **Favorites is the library with its filter on, not a second table.** A second view would need its
  own search, sorting, rating control and rank badges, and every one of them could drift. The user
  watches the checkbox move, which is honest: the rail did something they could have done.
- **Racer Select needed no artwork whatsoever**, exactly as §8 predicted. Frame 3 of each racer's own
  sheet is the static menu icon, all five have had one since the art arrived, and it is drawn at 3×
  with smoothing off. The speed class sits on the same screen because it is the same decision — it
  changes which course a song generates and therefore which stored score applies.
- **`model/PlayHistory` is backed by the hand-written `SimpleQueue`**, which is the pleasing part: a
  bounded history *is* a FIFO read backwards, the oldest entry being the one that has to go. The
  structure the project is graded on turns out to have a second job, which is a far better answer at
  the defence than one with a single use. Bounded at 60 — unbounded is a memory leak with a nice
  name on an application meant to run through an album.
- **`model/LibraryStatistics` is derived on read and never stored**, the same decision as the
  runner's rank and for the same reason: a stored copy is a second source of truth whose way of
  disagreeing is to be quietly stale after an edit.
- **The history records from the engine, not from a song change.** The moment a play is counted is
  the moment the history wants; a song scrolled past in the library was never listened to, and a
  resumed one did not play twice. `PlaybackEngine.setOnPlayCounted` is the hook, a callback rather
  than a history of its own, because what anybody does with "a song began" is not the engine's
  business.
- **`persistence/SettingsRepository` keeps the mood, the racer and the speed class**, written through
  a temporary file and an atomic move. Every unknown value falls back rather than throwing: a mood
  id from a later build, a racer this one has never heard of. `Racer.byName` and `SpeedClass.byName`
  exist for that — `valueOf` throws, and a profile from a newer version is no reason to refuse to
  open.
- **The smoke test photographs the new views and both moods.** All four rail destinations only exist
  once their button has been pressed, so the base shot proves nothing about any of them; the light
  mood is photographed on the mood screen *and* over the library, because a palette that fails does
  so on the controls rather than on the canvases. Screenshots in `docs/screenshots/`.

### The structure column folds away (`F4`)

The column is 400 of the window's 1440 pixels and it earns them while the structures are being
shown — but it is not always what the user is doing. `F4` folds it, and **measured, the middle of
the window goes from 815 px to 1215 px**: at one em per glyph that is fifty more characters of song
title, and it is the difference between a library table whose artist column reads `Crys...` and one
that reads `Crystal Castles`. Compare `docs/screenshots/sdmk-alphabetical.png` with
`sdmk-dsa-folded.png` — the filter row stops wrapping onto two lines as well.

- **Invisible is not enough; it has to be unmanaged too.** A node that is merely invisible still
  takes its 400 px in the layout, so the column disappears and hands its width to *nobody* — the
  table stays exactly as narrow as it was, beside a blank strip. That photographs as a rendering
  fault rather than a layout one, which is why the smoke test measures the centre rather than
  checking a flag: `dsa fold` prints `815 -> 1215 -> 815 px` and fails if the width went nowhere or
  did not come back.
- **The visualizer stops drawing while it is folded**, and the reason is the one already learned
  behind the companion window: an `AnimationTimer` is driven by the toolkit's pulse and knows
  nothing about whether the node it paints can be seen. `StructureView.isRunning()` exists so that
  this is *observable* — a timer left running is silent forever and costs a repaint per frame for
  the rest of the session, so it cannot be left to assumption.
- **`StructureVisualizer` remembers whether it is meant to be drawing**, rather than the state
  living only at the call site. It builds a *new* view whenever the playback mode changes and that
  view starts its own idle timer as it is constructed — and `Tab` cycles the mode from anywhere, so
  a fresh road scrolling behind a folded column is not a corner case.
- **Three separate things hide the visualizer** — the fold, the companion window, and presentation
  mode showing it in the opposite direction — and any one can be in force while another changes.
  `updateVisualizerDrawing()` decides from the state instead, because the combinations are what
  break: folding during a presentation must not stop the view filling the stage, and leaving a
  presentation back into a folded column must not start one. `F5` out of a fold is the case that
  matters — it is how somebody who folded the column reaches the tree for a question.
- **Both captions are the same width** (`F4 HIDE DSA` / `F4 SHOW DSA`). In a fixed-width font a
  toggle that changes width shoves the two keys beside it along as it is pressed, and those are
  exactly where the pointer already is.
- Nothing about the running order changes: this is a fold in the window, not a change of mode. The
  structure underneath goes on holding the queue and unfolding shows it where it got to.
- **Not persisted, deliberately.** The visualizer is the showpiece and the view used at the defence,
  so every launch comes up with it on screen.

## 9. Milestone tracker

| # | Milestone | Status |
|---|---|---|
| M0 | Maven skeleton, `javafx:run` opens a styled window, font loads | ✅ done |
| M1 | `model/` + three hand-written structures + JUnit tests | ✅ done |
| M2 | Library CRUD, search, 0–100 rating, cover display, JSON persistence | ✅ done |
| — | Asset layer (§8): `AssetRegistry`, `AssetKind`, `SpriteAnimation`, `assets.json` manifest, drop-in folder | ✅ done |
| M3 | Three modes behind the interface, selector, previous disabled in queue mode, `ComplexityPanel` | ✅ done |
| M4 | ⭐ **Structure visualizer** — circuit, starting grid, animated BST traversal, `OperationCounter`, live scatter, Presentation Mode | ✅ done |
| M5 | ⭐ `LocalFileAudioSource`, real playback, PCM tap, independent L/R meters | ✅ done |
| M6 | `BeatmapAnalyzer` + cache + debug view (BPM, onsets on a timeline) | ✅ done |
| M7 | ⭐ 3-lane runner: lookahead spawning, coins, bumps, star, cc classes, scoring, beat pulse | ✅ done |
| M8 | ⭐ Mini companion mode: transparent stage, disk + racer, expand/hide/quit | ✅ done |
| M9 | Sweep: side rail, favorites, history, statistics, keyboard reference, **`DARK` + `LIGHT` moods and a switcher** — the dark-mode bonus ships as moods, not a boolean | ✅ done |
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
- **`java.time.Duration.toSeconds()` truncates to a whole `long` second and widens to `double`
  without a warning.** Its JavaFX namesake returns a `double`, so the call site looks correct and the
  compiler agrees. Read the clock through `positionSeconds()`; the only place `toSeconds()` belongs
  is formatting `m:ss`. This cost a milestone's worth of "the game feels laggy" — see §7, M7.
- **Measuring a `Canvas` by timing `redraw()` measures nothing but the command recording.** The
  frame-to-frame interval is the honest number; `-Dsdmk.diag` prints it.
- **Dequeuing in mode 2 must not delete songs from the library.**
- **BST deletion with two children, and duplicate song titles** — the two places this breaks.
  Both are covered by tests; keep them passing.
- Idle meters and an empty course before playback starts; a clear message on load failure.
- Flattening the BST for in-order navigation is the single most likely shortcut to get caught.
- Clean shutdown: stop the game loop, stop playback, drain and close the `SourceDataLine`,
  release the stage.
- **Hiding the last visible window exits the application**, and swapping between the main window
  and the companion is exactly where that bites. Show the new one first, always.
- **An owned window is hidden with its owner.** The companion must not call `initOwner`.
- **A control that duplicates a function key must not be focus-traversable**, or it answers the
  first `Space` of the session instead of play/pause.
- **CSS beats code.** An author stylesheet outranks a value set programmatically; only an inline
  style outranks the stylesheet. A `setTextFill` that silently does nothing, and a `-fx-padding` that
  silently replaces the one a layout measured against, are the two shapes this takes.
- **A border is part of a region's insets**, so children get the width less padding *and* less the
  border, twice.
- **`-fx-background-color: transparent` is not the same as no background**, and *omitting* it is not
  the same as either — Modena's `.root` then paints `#f4f4f4` over the lot. A transparent fill is
  still picked, so an undecorated window's invisible corners eat clicks meant for the desktop. Use
  `null`, and check the corner pixels' alpha in a screenshot: all three states look identical in any
  viewer that composites onto white.
- **A sprite frame is not the same shape as what is drawn in it.** Standing one sprite on another by
  frame rectangle puts it in the air; ask `SpriteSheet.opaqueBounds(frame)` instead — and never per
  repaint.
- **`ImageView` is not resizable.** `resizeRelocate` positions it and leaves it at the artwork's own
  size. Use `setFitWidth`/`setFitHeight`.
- **Not every asset is pixel art.** Check before applying ground rule 8 to it: a flat-block sheet
  wants an integer scale with smoothing off, a 1830-colour illustration wants to be fitted like
  album art. `Cartridge.png` is the second kind and is the only one so far.
- **A `Canvas` in front of a control eats its clicks**, over the canvas's whole rectangle, however
  little it has drawn. The control still hovers and still does nothing, and the keyboard path keeps
  working so every key-driven test stays green. `setMouseTransparent(true)` on any canvas that
  overlaps something clickable.
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
