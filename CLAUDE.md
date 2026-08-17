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
   `audio/`, `analysis/`, or `spotify/` may import `javafx.*`. The UI observes; it does not own
   state. (`javafx.util.Duration` included — `model/` uses `java.time.Duration`.)
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

   **Paid off in M11, and the prediction held exactly.** The mood system was new code rather than a
   refactor: the sixteen roles, the palette, the accessor and the stylesheet pipeline were all
   already there, so M11 built the layers, the customizer, the pixel editor, the validator, the
   importer and the persistence and touched not one drawing call. The one CSS change it needed was
   `.root-pane` giving up its background so a layer behind the content could be seen at all.

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
| `-Dsdmk.screenshot=out.png` | During a smoke test, snapshot the window to a PNG. This is the only way to check layout without a person watching. It also writes one shot per view beside it — `out-shuffle`, `-arrival`, `-alphabetical` (the three structure views), `-presentation`, `-history`, `-racers`, `-spotify`, `-settings`, `-moods`, `-moods-light`, `-library-light` (the light palette on the controls, where a bevel drawn the wrong way round shows), `-dsa-folded` (the table with the structure column folded away), `-race`, `-mini` / `-mini-compact`,
**`-mood-sunset`, `-mood-bowser`, `-mood-sky`** (three presets whose overlay layers are the point of
them, photographed over the library rather than beside their own switcher), **`-mood-customizer`**,
**`-mood-layers`**, **`-pixel-editor`**, **`-spotify-add`** (the library's add-from-Spotify modal, with a
track already picked — the form is hidden until something is, so a shot of it as it opens would be missing
the half worth looking at; the results are made up rather than searched for, so a layout check does not
stop running when the machine is offline), **`-shutdown-glitch`, `-shutdown` and `-shutdown-eject`** (photographed by
`captureShutdown`, which tears nothing down — **three** shots because no two of the three things on that
screen are ever there at once: the tear is over before the cartridge moves, and the name is on the screen
or on the cartridge's label and never both, so one picture would be missing whichever two it caught. The
instants come off `ShutdownScreen.Moment` rather than being written down in the smoke test),
and **`-boot`, `-boot-partway`, `-boot-flash`, `-boot-glitch`, then one per movement of the fifteen-second
show — `-boot-presents`, `-boot-title`, `-boot-hold`, `-boot-loading`, `-boot-fade`** — the boot screen,
which exists only until the cartridge goes in and is therefore photographed *first*, before any other
check runs. Each is asked for at a stated instant through `BootScreen.previewGlitch` / `previewShow`,
because the sequence's own timer never ticks while the smoke test holds the interface thread. **Every
movement is a fade, so the instant matters**: the shots are taken at the *middle* of each one
(`BootScreen.Movement` carries them), since a still of the edge of a fade is a still of an empty screen
and looks exactly like a screen that failed to draw. The flash needs its own shot for the same reason —
it is over in `FLASH_SECONDS` of a `GLITCH_SECONDS` tear, so the glitch shot is taken after it. **Every one of them exists only once a mode has been selected or a key pressed**, so one shot of the opening state proves nothing about any of them. The companion shots are taken from **its own scene** (it is a separate window, so the main one's snapshot contains none of it) and after a seek a third of the way in, because a progress line at zero is a picture of an empty line. |

**The smoke test plays about three seconds of the current song** and prints the measured L/R levels,
so a run is audible. That is the point: the base screenshot is taken while audio is still flowing,
and a picture of two meters that have already fallen to silence says nothing about either. The
`channels differ` line is the check for the mistake that matters most — two channels reading
identically because they were never deinterleaved.

**It also analyses the current song and prints the beatmap**, waiting for it if the cache is cold.
The line to read is `grid deviation`: a tempo is always a plausible number, but beats sitting a few
milliseconds off the grid means the detected beat is the one in the music, and a figure approaching
a quarter of the beat means the histogram picked a tempo the track does not have.

**Then it builds the same beatmap the way a streamed track has to, and compares the two.**
`stream vs file` is how the Spotify course path is verified **without a Spotify account**: a
streamed track has no file, so its course is built from the audio going past the playback tap, and
the only property that matters is that it comes out identical to what the file analyser produced.
The blocks are read through `PcmFormat`, which is the same decode playback uses, so they are the
bytes the tap would genuinely see. A synthetic click track cannot establish this — a tempo fit that
disagreed only on real music would pass every unit test — and no screenshot can show it. Anything
other than `identical` on that line means a score earned on a streamed track is meaningless on a
local copy of the same recording.

**And it generates the course at all four speed classes and drives each one.** Three things on
those lines cannot be checked any other way. The entity counts are the claim that difficulty comes
from the music rather than from a timer, and only mean something against a real track's onsets.
`reproducible` regenerates each course and compares it — every stored high score rests on that
holding. And `lap` runs `game/ScriptedDriver` over the whole course at sixty frames a second
through the real collision rules; a course a competent driver cannot rank well on is a generated
course the rules cannot survive, and that is what the line catches, over four minutes of beatmap,
on every launch. It also reports **the best combo reached**, which is the only check that the
multiplier is reachable on real music rather than only in a unit test — a meter that never leaves
`x1` over four minutes of real beatmap is decoration, and that is a property of the generator against
a particular track rather than of the scoring rules, so nothing but driving one can establish it.

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
| `F11` | **true fullscreen for the runner and nothing else** — starts the race if one is not running |
| `Esc` | leave a fullscreen race; leave Presentation Mode; **leave the fullscreen window**; on the companion strip, expand; **on the boot screen, quit** — it draws no title bar, so it draws no close button |
| `< >` / `> <` *(title bar)* | give the whole display to the **application**, and hand the window back. **This is not `F11`** — see below |
| any key *(boot screen, cartridge already in)* | skip the rest of the fifteen-second start-up sequence. `Esc` still quits rather than skipping |
| `→` / `Space` *(tree view focused)* | step through one edge of a traversal |
| `←` / `→` / `A` / `D` *(road focused)* | change lane |
| `Space` / `↑` / `W` *(road focused)* | jump |
| `F3` *(road on screen)* | cycle the frame-pacing readout: off → drawn → printed only |

### There are two fullscreens and they are not the same thing

**The application launches into the whole display, and `F11` is still the runner alone.** They nest,
so they are two flags rather than one — `App.windowFullscreen` and `App.fullscreenRace`.

| | `windowFullscreen` (launch, `< >` button, `Esc`) | `fullscreenRace` (`F11`, `Esc`) |
|---|---|---|
| What fills the display | the **whole application** — side rail, library, meters, title bar | the **road and nothing else** |
| Title bar | **kept** — it is the only way back | off |
| Window frame | off | off |
| Other shortcuts | all live | dead |
| Starts a race | no | yes |

- **The title bar has to stay in the window mode, and that is the load-bearing difference.** There is no
  system chrome on a display with no window on it, so the `< >` button is the only visible way back out.
  Taking the header off — which is exactly what a fullscreen race does — would leave an application that
  cannot be un-fullscreened, moved or closed.
- **Leaving a race returns to whatever the *window* was doing**, not flatly to a window:
  `exitFullscreenRace` calls `setFullScreen(windowFullscreen)`. A single flag would drop the display as a
  side effect of leaving the game, so `F11` and `Esc` would quietly be a way of resizing the window.
- **The frame is computed, never toggled.** Three states want `no-frame` on — booting, a race, a
  fullscreen window — and any one can end while another is in force, so `updateWindowFrame()` decides it
  from all three. Toggling it at the six call sites is how `finishBooting` ends up putting a border back
  around an application that is filling the display.
- **The toolkit's own fullscreen exit key is switched off once, for the session**, in `start()` rather
  than per mode — both rely on it, and the failure it prevents is the same in both: the stage leaves
  fullscreen and every flag, style class and button caption here stays exactly as it was.
- **`Esc` is the last of three**, after presentation mode and after a race. Leaving a presentation has to
  win, or one keystroke would give up the display and leave the visualizer still holding the stage.
- **Clicking `F7 MINI` takes the window out of fullscreen first.** A 224 px strip is the opposite of a
  fullscreen application, and on macOS a fullscreen window lives in a Space of its own — hiding it from
  inside one leaves that Space on screen, empty, with the companion stranded on the desktop behind it.
  It does **not** put it back on expanding: "taken out" is the whole of what was asked for, and a window
  that silently re-took the display on the way back would be a surprise.
- **`setFullScreen` must not be called from inside `start()`, and this froze the whole application.**
  It enters a nested event loop on macOS and does not return until the platform's transition finishes —
  and that transition can never finish while `start()` is still on the stack, because the launcher has
  not handed the thread back to normal event dispatch yet. **Measured** with `jstack` on the real run:
  the FX Application Thread sat `RUNNABLE` in `MacApplication._enterNestedEventLoopImpl` for 574
  seconds, entered from `MacView._enterFullscreen` ← `Stage.setFullScreen` ← `App.start`.

  **The symptom is the worst shape this could take: a window that draws perfectly and accepts no
  input.** The boot screen was painted before the call, so it looked completely normal — and then the
  cartridge could not be dragged, no key did anything, and the only way out was to kill the process.
  Nothing threw, nothing was logged, and it was reported as "it won't let me drag down the cartridge",
  which sounds like a hit-testing bug and is not one.

  `Platform.runLater(() -> setWindowFullscreen(true))` is the fix: a later pulse, once `start()` has
  returned and the outer loop is running, is what lets the nested one complete. **This is §"the smoke
  test" deadlock reached by a different road** — the same call, the same nested loop, a different
  reason the thread was busy — and it was written *after* that warning was already in this file, which
  is worth knowing: the note said "during a smoke test" and the real constraint is "while anything is
  holding the FX thread", `start()` included.

  Confirmed after the fix, at the real fullscreen geometry (stage 1800x1130, `fullScreen=true`), by
  driving the gesture with real mouse events: `short drag: refused`, `full drag: SEATED`.
- **And deferring it by one pulse was not enough, which is a different fault with the same cause.**
  Reported as *"why does it sometimes not launch as fullscreen — closing and opening back to back; if
  I wait it boots up correctly"*, and the wait is the whole diagnosis. `setFullScreen` reaches AppKit's
  `toggleFullScreen:`, which is a **request rather than a setter**: the window server builds a Space
  and animates into it, and a request arriving while the application is already mid-transition is
  **discarded in silence** — no exception, no log, no property change. Two transitions are in flight
  exactly there: the zoom `setMaximized(true)` starts at `show()`, and on a relaunch that follows a
  quit closely, the *previous* instance's fullscreen Space still being torn down. A `Platform.runLater`
  is one pulse, about 8 ms, which is nowhere near either. `LAUNCH_FULLSCREEN_SETTLE` is 400 ms and
  costs nothing anybody can see — the boot sequence behind it runs for fifteen seconds — and
  `LAUNCH_FULLSCREEN_RETRY` asks **once** more, bounded rather than looping, because `setFullScreen` is
  the call this project has measured wedging the interface thread and a loop would turn an intermittent
  freeze into a reliable one.
- **The real bug underneath it was that this class never asked the window anything.**
  `setWindowFullscreen` set the flag, stripped the frame and flipped the button's caption and *then*
  called `setFullScreen` — so a refused request left a state no user could make sense of: a merely
  **maximised** window, with **no border**, and a `< >` button reading as though it were already
  filling the display. Pressing it appeared to do nothing, because its `setFullScreen(false)` is a
  no-op on a window that was never fullscreen, and only the **second** press worked. Nothing threw and
  nothing was logged, so it read as the launch being unreliable rather than as one boolean being out of
  step. `syncFullscreenFromStage` reconciles against `mainStage.isFullScreen()` after every request and
  from a listener on `fullScreenProperty`, which is also how a change the platform makes on its own
  arrives.
- **It must never confuse a fullscreen race with a fullscreen window, and without a guard it would.**
  `enterFullscreenRace` sets its own flag and then puts the *stage* fullscreen, so the property change
  would come back and be recorded as the user having asked for a fullscreen **window** —
  `exitFullscreenRace` would then hand the display straight back to itself through
  `setFullScreen(windowFullscreen)`, and `F11` would have become a one-way door. The two modes nest,
  which is exactly why they are two flags; `syncFullscreenFromStage` returns early on `fullscreenRace`.
- **None of this is reachable by the smoke test**, which skips `setFullScreen` outright — so
  `[smoke] window fullscreen` proves only that the guard did not break what was already asserted. What
  the fix addresses is a platform race that needs a real window server, and it is verified by launching
  twice in quick succession rather than by any check in the run.
- **The launch into it is skipped whole during a smoke test**, which is a stronger exemption than the
  `setFullScreen` one below. The frame comes off in this mode, so launching into it would take the
  border out of *every screenshot the run photographs* and leave the layout checks measuring a window
  that is not the one a user opens. `reportWindowFullscreen` enters and leaves it on purpose instead,
  mid-run, and checks the flag, the frame, the header staying and **the button's caption** — a button
  that went on saying "fill the display" while already filling it reads as a control that did nothing.

**True fullscreen (`F11`) is the runner and nothing else** — no title bar, no side rail, no meters, no
playback bar, no window frame and no desktop. It is presentation mode's idea taken one step further:
`F5` gives the whole *window* to the visualizer, `F11` gives the whole *display* to the game. It
**starts the race** if one is not running, because a fullscreen kart player with no kart in it is a
black screen with a rank of D in the corner.

- **The road is moved into the overlay pane's slot**, exactly as `F5` moves the visualizer, so it keeps
  the mood's layers instead of losing its wallpaper at the moment the game fills the screen.
- **`.pixel-window.no-frame` drops the border.** A frame is what tells you where a window ends, and on
  a screen with no window on it three pixels of amber is the only thing on the display that is not the
  game. The boot screen wears the same class for a related reason — see §"the boot screen" below.
- **The toolkit's own fullscreen exit key is switched off** and `Esc` is handled here instead. They are
  the same keystroke and not the same thing: the toolkit's would take the stage out of fullscreen and
  leave every other change in place — no title bar, no border, a runner parented to the wrong pane, and
  a window that cannot be moved or closed. That is the `scene.setRoot` trap in a different costume, so
  there is exactly one way in and one way out. The hint the toolkit prints goes with it; the road
  already draws its own controls line.
- **Every other shortcut is dead while it is up**, and not because it would throw — because it would
  *work*: `F6` would swap the library into a pane that is not on screen and leave the road showing,
  which reads as a key doing nothing while quietly having done something. Same shape of early return as
  the boot screen's, in the same filter. The driving keys are untouched: the runner installs those as
  its own scene filter, which runs after this one.
- **Leaving fullscreen is not leaving the race.** A run lost to a keystroke about window management
  would be a run lost for no reason.
- **`Stage.setFullScreen` is skipped during a smoke test, and that is not a shortcut — it deadlocks.**
  On macOS it enters a *nested event loop* and does not return until the platform's transition has
  finished, and the smoke test runs synchronously on the interface thread inside a synthesised key
  event, so that transition can never complete. Measured: the run wedged inside
  `MacApplication._enterNestedEventLoopImpl` and printed not one line after `window shrink`. A nested
  loop also pumps the event queue, which would re-enter the very check that is running. Everything the
  *application* decides is still driven and still asserted, and `[smoke] race fullscreen` says out loud
  that the stage's own call was left out. **Do not call anything that enters a nested event loop from
  inside `runSmokeTest`** — `showAndWait` is the other one.

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

**The main window joined them last, and until it did this section was describing an intention
rather than the code.** `App` never called `initStyle`, so the primary stage was `DECORATED` while
`app.css` and this file both asserted that nothing was. It is `StageStyle.TRANSPARENT` now, with
`scene.setFill(Color.TRANSPARENT)` and a `.pixel-window` shell drawing the border, exactly as the
dialogs do.

- **The header *is* the title bar** — one strip, not two. It already had the name, the version and
  the three view toggles, and it was already a `.pixel-titlebar` in everything but name (same
  `-ui-recessed` ground, same `-role-primary` underline); it gained `-fx-cursor: move`, a
  `PixelDialog.dragBy`, and **minimise / maximise / close** at its right end. Stacking a second
  full-width bar above it would have cost 40 px of an 800 px window to draw the name twice.
- **The close button is not a nicety.** Before it, the main window had *no* way to quit: the only
  `Platform.exit()` a user could reach was the companion window's. Undecorating without it would
  have left the application closable only by collapsing to the companion strip first.
- **The three window buttons are `setFocusTraversable(false)`** for the same reason the view toggles
  are, and they are more exposed to it: they sit at the *end* of the strip, where a traversal
  arrives.
- **Presentation mode swaps the shell's centre, not the scene's root.** `togglePresentation` used
  `scene.setRoot`, which was harmless while the operating system drew the chrome and takes the title
  bar away with it now — leaving a window that cannot be moved or closed until `F5` is pressed a
  second time. The header was hoisted out of `root` into a persistent shell for this. The visualizer
  gets the stage less the title bar, which is the right trade.
- **The window comes up in true fullscreen**, and maximised underneath it — so `MAIN_WIDTH` /
  `MAIN_HEIGHT` are what it restores to after the display is handed back twice over. All three sizes
  have to lay out, which is what the maximise and `< >` buttons are for. The smoke test does neither:
  it measures the middle of the window against `SIDE_COLUMN_WIDTH` and photographs every view, and
  both want the size the constants describe rather than whatever display the run is on. See
  §"There are two fullscreens" — the fullscreen *window* keeps its title bar, which is what stops the
  launch state from being one nobody can get out of.
- **Dragging is ignored while maximised**, consumed in a mouse *filter* so it runs before
  `dragBy`'s handler. The filter checks `event.getTarget() == header`, which is what keeps the
  buttons on the strip working — a press on one of them is targeted at the button, not the strip.

### The boot screen: insert the cartridge (`ui/BootScreen`)

**The application opens on a black screen with a cartridge on it, and the user drags it into a
slot.** The name is a ROM filename, the interface is 8-bit, and the companion window is already
literally a game cartridge standing on a record — so the launch is the ritual the whole thing is
dressed as. It **replaced** the `START YOUR ENGINES` dialog: inserting the cartridge *is* that
answer, and a second modal question straight afterwards is one too many. Boot lands on the library,
**paused**; pressing play still brings the road up on its own, exactly as before.

- **The name is on the cartridge while the cartridge is on screen, and on the screen once it is not.**
  That is what a cartridge is — the label is where the title goes — so during the drag it is placed by
  `SpriteSheet.darkRegion(0)`, measured at **238x389 at (204,3)**, and wrapped on the name's *own*
  separators (`_` and `-`) so it reads as a ROM label rather than as a sentence chopped mid-word.
  `labelFontSize` fits it both ways — no line wider than the label, no block taller.

  **Once the cartridge is *in* the machine there is no label left to read, and the loading screen
  prints the name across itself as a splash** — which is where a console has always put a title, and
  the one screen in this application with room for a 43-character joke at a size worth reading. The
  cartridge's label is 48% of a cartridge, the title bar shares a strip with three toggles, and the
  companion window is measured to have room for none of it. It is safe here *precisely because* the
  cartridge has gone: the two can never be on screen at once, which was the whole reason the splash did
  not exist before. Measured on this window: **44px over two lines, widest line 23 characters = 1012 px
  of 1440**. `splashFontSize` searches down from 44px for the largest size that wraps to no more than
  `SPLASH_MAX_LINES` = 3 — the name breaks into seven runs, so with more lines allowed it would happily
  come out as a narrow column of them at full size, which is the one arrangement that reads as a
  wrapping accident rather than as a logo. `BootScreen.splashAt` reports it and the smoke test measures
  it, because a splash that silently fell back to its minimum still draws perfectly well; it just stops
  being a splash. `SPLASH_GAP` exists because leaving it out put the LOADING caption inside the
  descender space of a 44px glyph — nothing overlapped and it still read as a collision.
- **The cartridge disappears the moment it is inserted, and getting that wrong left a sliver of it on
  screen under the loading bar.** A `Pane` does not clip its children and the cartridge is deliberately
  taller than the travel it makes, so near full insertion its foot hangs *below* the pane's own bottom
  edge — where the three canvases end and nothing can paint over it. Everything else went black and the
  one thing that had just gone into the machine was still visible. Two fixes, both needed: the pane is
  **clipped** to its own bounds, and the `ImageView` and the name plate are **hidden whenever the phase
  is not `INSERT`**, decided in `layoutChildren` because four different things enter a phase (the drag,
  `settle()`, `previewAt()` and the sequence's own timer) and that is the one place all four pass
  through. `[smoke] boot cartridge in` reads the node's visibility, because the sliver hung below the
  pane and is off the shot on a tall window.

  **And the glitch no longer draws a torn copy of the artwork either, which is where the last of it was
  hiding.** This section used to end "the glitch still draws its own *torn* copy of the artwork from the
  sheet — that is the picture breaking up, not the object still sitting there", and looked at rather than
  reasoned about, it is not: the tear is half a second long and twenty-two displaced bands of a cartridge
  are still recognisably a cartridge, so what the eye reports is the thing that was just pushed into the
  machine hanging about on screen. That is the same sliver arriving by a different route, and it is what
  was asked for by name — *"kill the sprite just when it happens, it's showing in the glitch screen"*.
  **What tears now is the raster**: the tube's own scanlines, lit and thrown sideways by the same
  `tearOffset`, decaying to nothing. `[smoke] boot glitch` reads the node's visibility *at the tear*
  rather than only after the show, which is where the complaint actually was.
  - **Tearing `CrtEffect`'s mask instead is the obvious move and it is a no-op.** The mask darkens
    towards `SHADOW`, and on this screen `SHADOW` *is* the ground — black torn over black changes not one
    pixel. It was written that way first and the screenshot came out **identical to the one before the
    change**, which is the worst way for an effect to fail: the code reads correctly and there is simply
    nothing there. A raster drawn momentarily *brighter* than the room has somewhere to go, and it is
    also the truer picture — a tube handed a live signal lights up. The lit rows are offset onto the ones
    the grille leaves clear (`CrtEffect.SCANLINE_PERIOD - 1`), or the two cancel and the glitch comes out
    as a flat wash. Now that the grille bows with the glass that alignment is exact down the middle of
    the tube and drifts by a row towards the sides, which is left alone deliberately: these rows are
    being thrown up to a sixth of the screen sideways at the time, so a raster lining up perfectly with a
    curve it is not sitting on would be the odd thing.
  - **The glass is drawn over the tear too, and that is not cosmetic.** The tube's rounded corners are a
    property of the *screen* rather than of what is on it, so leaving them off for the half second of the
    glitch had the display visibly change shape at the moment it is meant to be announcing what it is —
    square, then round again. `drawFront` no longer returns early for the glitch phase; what is torn is
    the signal, and the glass it arrives on does not move. It is also the shot where the curvature reads
    most strongly, because the torn raster fans out along the bow — see `-boot-glitch`.
- **The picture fades up rather than arriving all at once** (`BootScreen.sleep` / `wakeUp`), which is
  what "it just pops out" was about: the window used to appear with the whole boot screen already drawn
  on it. **What fades is the cartridge and the prompt, and deliberately not the room, the slot or its
  lit rim** — so the display is already on with an empty slot in it and the thing you are meant to pick
  up rises out of the dark in front of it. That reads as what the screen is *for*, where a flat
  crossfade reads only as something fading. It is also two node opacities and a multiply on two
  captions rather than a full-canvas alpha fill, which on the machine §7 describes is the difference
  between free and ten milliseconds a frame.
  - **`sleep()` is called as the screen is constructed, before the scene exists**, because nothing can
    fade in from a state it was never in and because that way the first frame the window paints is
    already the faded one — no extra layout pass lands anywhere near the launch.
  - **It starts *awake* and the launch path is what puts it to sleep**, never the other way round.
    Asleep by default, every screenshot of this screen would come out black — and a black picture is
    exactly what a screen that failed to draw looks like, which is the one photograph that would be
    believed. `settle()` and both previews force it up as well, and **`[smoke] boot wake` is the line
    that would catch it**: nothing else in a run notices, because a black screenshot is still a
    screenshot.
  - **Fading the *window* was tried four ways and abandoned** — see §11, where the finding is not about
    fading at all.
- **Nothing else is on the screen, and it is black and white rather than in the user's mood.**
  `Palette.hardware()` — black ground, white light, nothing else — because at this point the system has
  not started: a mood is something the *software* chose, so a boot screen in Sunset Wilds is the console
  admitting it was running all along, and the flash at the moment of contact stops being a flash of
  light and becomes a flash of somebody's colour scheme. **The window frame goes too** (`.no-frame`, the
  same class true fullscreen uses), because an amber border around a black screen is the software's look
  arriving before the software does. **The title bar is not attached yet** either: it is built during
  `start()` (it wires the window's drag and buttons) and attached in `finishBooting()`, along with the
  frame and the mood's layers.

  **This is still inside ground rule 7**, and that is why it is a `Palette` rather than a handful of
  literals in `ui/`. The rule is about *where colours are defined*: these screens still name a
  `PaletteRole` for every colour they draw and still ask a palette for it — they just ask a different
  one — so the hex values live in the one file the project allows them in, and a screen that wanted the
  mood back would need no change but which palette it reads. It is monochrome, so the roles carry
  **lightness** instead of hue: `ACCENT` and `NEGATIVE` are the glitch's two interference bands and are
  a long way apart on that axis for the same reason they are a long way apart in hue everywhere else.
  It is never offered in the switcher and never reaches `MoodValidator` — the protected roles'
  guarantees are about a look a user can choose, and nobody can choose this one. `HardwarePaletteTest`
  pins the monochrome, the true black, and that the flash is `#ffffff` rather than the `248` a
  `c5 << 3` shift would give.
- **Which means Escape quits, and it has to.** With no title bar there is no close button, and a
  window with no visible way to shut it needs an invisible one. Escape is what every other frameless
  window here already uses to say no. The black itself is the drag handle (`dragBy` on the pane);
  the cartridge consumes its own presses, so dragging it never moves the window.
- **Every other shortcut is dead while booting.** Otherwise the first key of the session could
  collapse the application to a companion strip over a boot screen, or swap in a road nobody can
  see. Both the filter and the handler return early on `booting`.
- **`suspendMainViews()` is called before the first frame.** Five canvases start their timers as
  they are constructed, and an `AnimationTimer` does not stop because the node it paints cannot be
  seen — the identical fault the companion window and the `F4` fold each had to fix. The existing
  suspend/resume pair is reused unchanged.
- **Insertion is a threshold, not a destination.** `INSERT_THRESHOLD` is 0.6 of the travel: past
  halfway because the gesture is deliberate and being made to repeat it reads as the drag not having
  worked, short of 1 because insisting on the last pixel makes the slot a target rather than a
  direction. Short of it, the cartridge springs back. The travel is `SEAT_SHARE` of the cartridge's
  own drawn height, so replacement art seats to the same *place* rather than the same number, and
  the mouth's width comes from `SpriteSheet.footprint(0)` — the artwork's real inlet, 454 of 500.
- **Then a white flash and a tear** - what a console did when a cartridge went into a live slot. The
  picture tears into `TEAR_BANDS` horizontal bands thrown sideways by `tearOffset`, decaying to nothing
  across `GLITCH_SECONDS`, with interference bands in `ACCENT` and `NEGATIVE` (the two roles furthest
  from the room's own colour, and in the console palette the two furthest apart in lightness, so they
  read as interference and never as part of the artwork). **The flash is `TEXT_PRIMARY`, which in
  `Palette.hardware()` is `#ffffff`** - genuinely white rather than nearly white in whichever direction
  a mood happened to lean. It lasts `FLASH_SECONDS` of the tear, so it is the *start* of the glitch
  rather than the whole of it, and it needs a screenshot of its own (`-boot-flash`): a picture taken a
  third of the way in is taken after the flash is over.

**Then the show: a fifteen-second start-up sequence that runs for exactly as long as the fanfare.**

This is the PlayStation shape rather than a progress bar - a publisher line, a title arriving on the big
hit, a long hold, the loading bar, and a fade to black - and the point of the length is that
**`setSequenceSeconds` takes the fanfare's own decoded duration**, so the picture and the sound end
together and neither is cut off by the other. `SEQUENCE_SECONDS` = 15.0 is only the fallback for a
missing sound; replace the audio and the sequence re-times itself with nothing else to change.
`[smoke] boot sequence` prints both numbers side by side, because the two drifting apart is exactly the
sort of thing that looks fine in every screenshot.

- **One phase, one clock, and every element a pure function of it.** `Phase.SHOW` holds a single
  normalised progress and `presentsAlpha`, `titleAlpha`, `titleScale`, `seamWidth`, `rayAlpha`,
  `raySweep`, `barAlpha`, `barProgress` and `blackout` are all static functions of it. A chain of five
  phases each with its own timer is five places for the sequence to get out of step with itself and
  nowhere to ask "what does it look like at eleven seconds" - which is the question every screenshot and
  every test here needs to ask. `drawShow` decides only *where* things go, never *when*.
- **The stages are fractions of the show, and they come off the audio's own measured envelope.**
  Quarter-second RMS blocks over the decoded fanfare: a quiet opening chime to 2.75 s, the big hit at
  3.00-4.25 s, a sustained passage to 9.0 s, a decay to 14.0 s, then silence. Divided by the 14.45 s
  show that follows the tear, those land on `PRESENTS_*`, `TITLE_IN`/`TITLE_FULL`, `LOADING_IN` and
  `FADE_OUT` - which is why **the title arrives *on* the swell** rather than near it.
- **The fades are smoothstep, not linear** (`ramp`), and that is most of why they read as dramatic: a
  linear fade starts and stops abruptly at both ends and the eye catches the corner.
- **The title fades in and settles.** It arrives `TITLE_OVERSHOOT` too big and eases down to the size
  `splashFontSize` measured, so it *lands* instead of appearing. `titleScale` never goes below 1 - a
  title that faded in already overflowing the screen and then shrank into it would be a fade in the
  wrong direction, and a test walks the whole sequence to check it. **The wrap is computed at the
  resting size and never at the scaled one**, or the line count would change mid-fade and the block
  would jump.
- **The title cycles a rainbow and settles to white as the fanfare dies** (`titleColor`). This is the
  one thing on either bracket screen with a hue in it, and it is a deliberate exception rather than the
  monochrome rule quietly slipping: `Palette.hardware()` is the console with the power just switched on
  and everything else in the sequence still asks it, but a machine running a **colour test across its own
  name** is a different statement from a machine wearing somebody's mood — it is the hardware showing
  what it can do before any software has chosen anything. The two palettes are used in the same frame
  and never for the same thing.
  - **It is `Palette.bootRainbow()`, so ground rule 7 is untouched.** Six hues on the six roles the
    runner's star already cycles, in the same order, snapped to the 5-bit grid by `GbaColor.web` like
    everything else. `BootScreen` names a `PaletteRole` and calls `Palette.mix`, exactly as it does for
    the console's own colours — it just asks a different palette, which is the same arrangement
    `hardware()` established. **Cycling roles through `hardware()` instead would give six greys**, and a
    title "cycling" between them is a static white title that no screenshot would flag.
  - **`TITLE_RAINBOW_HZ` is 0.4** — one full sweep every two and a half seconds, so 2.4 colour changes a
    second against §8b's 3 Hz cap, and deliberately slower than the star's `RAINBOW_HZ` of 1.6: the star
    is a power-up going off mid-race and this is a title being *read*. Interpolated rather than stepped,
    so it never flashes.
  - **It resolves to white rather than stopping.** From `TITLE_WHITE_IN` (= `LOADING_IN`) it eases to the
    console's own `TEXT_PRIMARY`, a true `#ffffff`, arriving exactly at `FADE_OUT` — so the colour
    finishes at the instant the blackout starts and the sound runs out. A rainbow cut off mid-sweep at
    the fade reads as an effect interrupted rather than one that ended.
  - **The test for the settle is an envelope, not a monotonic distance from white**, and getting that
    wrong first was informative: the rainbow goes on cycling *underneath* the settle and the six hues are
    not equally far from white — yellow is much closer than blue — so the measured distance genuinely
    wobbles while the settle never reverses. What the interpolation guarantees is that the room the hue
    has left shrinks to nothing, and that is what `theSettleIsMonotonic` asserts.
  - Visible in `docs/screenshots/sdmk-boot-title.png` (yellow-green, mid-fade), `-boot-hold` (green, full
    strength), `-boot-loading` (pale yellow, half settled) and `-boot-fade` (neutral, all colour gone).
- **A seam of light, and it had to be moved.** A line at the centre that opens sideways is the cheapest
  dramatic reveal there is and the only one that suits hard edges - one rectangle, no blur. But it sits
  at the height of the middle of the title block, so while both were half-way up **the line ran straight
  through the words and read as a strikethrough**. It now fades out over the first part of the title's
  own fade-in, so the two hand over rather than overlap. Caught by looking at the picture.
- **Rays that start at a radius, not at a point.** Sixteen wedges converging on one pixel fill the
  middle of the screen with solid grey - a blob with spikes on it rather than light from behind the
  title, and exactly where the title has to be readable. `RAY_INNER` leaves the centre clear. They are
  gone by `RAYS_DONE`: this is an arrival, and a starburst that stayed and span would be a screensaver.
- **A seeded starfield during the hold**, through the same SplitMix64 finalizer as the tear and for the
  same reason. Five seconds on a static frame reads as the application having frozen at the moment it is
  meant to look most alive - the same problem the companion window's spinning record solves. One pixel
  each and capped at `STAR_ALPHA`, so they cannot compete with the title.
- **The one thing that repeats is `BREATH_HZ` = 0.4**, a slow swell on the held title. Section 8b caps a
  full-screen rhythmic effect at 3 Hz; this is nowhere near it, deliberately, and a test says so.
  Nothing else in the sequence repeats at all.
- **The caption says what is actually being waited for.** The bar has always been a beat rather than a
  measurement, and now that it runs for fifteen seconds a stale `GO-LIBRESPOT READY` sitting there while
  it carries on filling reads as a machine that has lost track of itself. Every *resolved* daemon state -
  connected, needs a login, did not start, not installed - gets `WAITING_FOR_THE_SOUND` appended; only
  `STARTING`/`READY_TO_CONNECT` still says it is loading. Honest, and the line under it says how to get
  past it.
- **`skip()`: any key cuts it short, and that is not a compromise on the sequence.** Running for the
  length of the fanfare is what makes the *first* launch an event; the tenth launch of an afternoon is a
  wait, and somebody demonstrating this will start it many times - every console this is dressed as let
  you press through its own logo. **Escape still means "no" rather than "hurry up"**, so it is handled
  first and returns; everything else skips. Refused while the cartridge is still outside the machine, or
  a key would boot the application without the gesture and make the gesture optional. It runs `settle()`,
  so a user in a hurry and the smoke test take the same path out.
  **`[smoke] boot skip` checks it from inside a running sequence** - and it has to, because a skip that
  did nothing would be *invisible*: the sequence would simply run its own length, which is what it does
  anyway, so nothing would look wrong for fifteen seconds.
- **`previewGlitch` and `previewShow` replaced `previewAt(glitch, loading)`.** Every movement of the show
  is a fade, and a fade is the single most unphotographable thing there is: a still of the edge of one is
  a still of an empty screen, which looks exactly like a screen that failed to draw and is the one
  picture that would be believed. `BootScreen.Movement` names the five and carries the instant in each
  worth photographing - the **middle** of the fade, never an edge - so the list lives beside the timings
  instead of being copied into the smoke test where the two could drift, and the timing constants stay
  package-private.
- Screenshots: `-boot-presents`, `-boot-title`, `-boot-hold`, `-boot-loading`, `-boot-fade`.
- **And the machine makes two noises: `assets/sounds/Cartridge_In.mp3` on the release, and
  `assets/sounds/psx.mp3` on the tear.** They overlap, and they are **two moments rather than one** —
  which they were not to begin with, and the gap is the whole point. The clunk is the sound of the thing
  *moving*, so it goes on `BootScreen.setOnSeating`, fired the instant the drag commits; the fanfare is
  the machine noticing, so it stays on `setOnGlitch`, before the first frame of the tear, because the
  flash and the fanfare are one event and a fanfare a frame late reads as a sound effect rather than as
  the machine coming on.
  - **Fired together, both began after the seat animation had already finished.** `seatTo` runs a
    `SETTLE` = 200 ms timeline and `startSequence` hangs off its `onFinished`, so the cartridge slid home
    in silence and then landed with a noise once it had stopped — the picture and the sound describing
    different moments. Two tenths of a second is small and it is exactly the length of the only motion
    the sound is meant to be describing.
  - **The clunk is guarded against the smoke test and the fanfare is not, and that asymmetry is real.**
    `onGlitch` is unreachable in a screenshot run — the seat timeline never gets a pulse, so
    `startSequence` never runs — while `onSeating` hangs off the gesture, which the smoke test genuinely
    performs. Without the guard in `App`, taking a screenshot would play two seconds of audio into a
    build log.
  - **`[smoke] cartridge clunk` is the only thing that can see when it fires**, because the sound is
    suppressed in exactly the run that could check it: what is read is the callback rather than the
    audio. A clunk that slipped back onto the tear would simply never fire there and **no picture would
    differ**.
  - **Adding that check found the full drag had been a no-op for a milestone.** `fireDrag(handle,
    travel)` sat *after* the glitch and show previews, which put the screen into a phase that correctly
    refuses the gesture — so every press, drag and release was dropped on the floor and the line beside
    it read as a check. It runs before the previews now, while the screen is still waiting for a
    cartridge, and `[smoke] boot full drag` says whether it was accepted.
  - **Two `SoundEffect`s rather than two calls on one, and that is forced rather than chosen.** One
    effect is one output line, and `play()` begins with `stop()` — so asking a single one for the second
    sound would cut the first off a millisecond after it started. They are separate fields on `App` and
    therefore separate lines, which is the only way they can overlap at all.
  - **They do not fight, and that was measured rather than hoped for.** The clunk is **2.06 s** and the
    fanfare's own envelope opens with a quiet chime out to 2.75 s (the same measurement the show's
    timings come off), so the mechanical noise lands entirely inside the quietest part of the music —
    with the extra 200 ms of head start it now has, more so rather than less. That is also what a console
    sounds like: the cartridge going home and the chime starting are one event with two sounds in it.
  - **Both cartridge sounds are mono where the fanfare is stereo**, so they are the only things shipped
    in the jar that take `PcmFormat`'s **two-stage** conversion — decode, then resample and mix up to
    44.1 kHz stereo. §6 documents that path and notes that "almost every real file" takes the one-step
    one, which means nothing else here would ever notice it breaking. `[smoke] cartridge sounds` decodes
    both by name and prints their lengths for exactly that reason.
  - **`audio/SoundEffect` opens a line of its own**, and that is the whole design rather than a
    convenience. The application's existing `SourceDataLine` belongs to the *music*: it is the clock the
    runner's entire lookahead is read off (`position()` counts the frames that line has rendered), so
    pushing a fifteen-second fanfare through it would move that clock by fifteen seconds. Nothing in it
    touches `AudioSource`, the taps, the meters or the beat analyser — a sound effect is not part of the
    track and must never appear in its beatmap.
  - **It decodes through `PcmFormat`, not through a decoder of its own.** `PcmFormat.open(InputStream,
    String)` was added for it: a resource in the jar has no `Path`, and copying it out to a temporary
    file to be allowed to decode it would be a second decode path to keep in step with the first. The
    stream is wrapped in a `BufferedInputStream` because that is how a decoder probes a format — it
    reads a header, decides, and rewinds. Verified against the resolved jars: the fanfare is 44.1 kHz
    stereo already and takes the one-step conversion.
  - **Measured: 15.0 seconds**, and it now **fades out as the library comes up** rather than ringing on
    over it — see §"the handover" below, which owns the decision and the length. It used to ring on
    deliberately, the way a console's fanfare does over a game's first screen, stopped only by
    `PlaybackEngine.setOnPlayCounted`; what that hook could not do is describe the *end of the boot*,
    which is a moment the sequence knows about and the transport does not. It is still registered, as a
    backstop that is a no-op on every path there is today, and the reason it is exact is unchanged: the
    application boots *paused* and no song has ever played, so a first play is always a counted one and
    can never be a resume that slips past.
  - **`stop()` fades rather than cutting**, because stopping a line mid-block leaves the cone off zero
    and that step is an audible tick. `stop(double)` is the same fade at a stated length, and it exists
    for exactly one caller: a fade that is *accompanying* something has to last as long as the thing it
    accompanies, and `FADE_SECONDS`' quarter of a second under a 650 ms fade to picture reads as the
    sound having been cut early rather than as the two ending together.
  - **The faded tail is `drain`ed and not `flush`ed, and at 0.25 s that distinction was invisible.**
    `SourceDataLine.write` returns once the audio is in the line's own buffer, not once it has been
    heard, so flushing straight afterwards discards however much of the fade is still queued and cuts it
    off part way down its own ramp — a click, and precisely the one `FADE_SECONDS` exists to prevent.
    While the fade was shorter than the line's buffer that loss was the inaudible tail of a ramp already
    near zero, which is why the old code was right and stopped being right the moment `stop(double)`
    made a longer fade possible. The wait is bounded by the fade and is spent on the effect's own daemon
    thread, which no caller is holding.
  - **Nothing about it can throw.** A missing resource, a format no decoder can read and a machine with
    no free output line are all ordinary; the sound does not happen and a line goes in the log (ground
    rule 5). `isReady()` exists so the *absence* can be reported, which is the only reason to know — a
    sound that failed to decode is byte-for-byte as silent as one that was never triggered, and
    `[smoke] boot fanfare` is the one check that tells those apart. It decodes without playing, because
    a build log is not a place to play fifteen seconds of audio.
  - **`previewAt` and `settle()` never fire it**, which is why the smoke test is silent: a screenshot
    must not make a noise, and a check that ran the whole sequence quietly is worth more than one that
    does not.
- **The tear is seeded, not random**, by the **SplitMix64 finalizer** over band and frame. Same
  decision as `Course`'s own hash and for a related reason: an effect nobody can reproduce is an
  effect nobody can check, and the smoke test drives this with no pulses at all. **FNV-1a was tried
  first and was measurably wrong here** — it avalanches poorly in its high bits over two-word inputs,
  so *every* band cleared the displacement threshold, which is static rather than tearing.
  `BootScreenGeometryTest.notEveryBandMoves` is what caught it.
- **`settle()` and `previewAt(glitch, loading)` exist because none of this can be photographed or
  waited for.** `runSmokeTest` runs synchronously inside `start()`, so no pulse ever arrives: the
  seat timeline, the glitch and the loading bar would all sit frozen at whatever the last synthesised
  drag left. Same reason `StructureView.settle()` and `MiniPlayerView.previewAt` exist.
  **`previewAt` must call `requestLayout()` itself** — a `Parent` only lays out when something marked
  it dirty and none of the phase fields are observable, so without it a second preview silently
  redraws the first one, which looks like the phase never changed rather than like the canvas was
  never asked to. That was a real bug, caught by looking at the picture.
- **The smoke test drags it with real mouse events**, twice: once short of the threshold, asserting
  it is *refused*, and once past it. A threshold that accepts everything is not a threshold, and a
  drag is exactly the kind of routing fault no screenshot and no unit test can reach — a canvas left
  pickable in front of the cartridge would swallow every press while still hovering correctly, which
  is the fault that ate the companion window's transport clicks. `fireDrag` aims at the **node**,
  unlike `fireKey`, which aims at the scene.
- Screenshots: `docs/screenshots/sdmk-boot.png`, `-boot-partway`, `-boot-flash`, `-boot-glitch`, and
  one per movement of the show - `-boot-presents`, `-boot-title`, `-boot-hold`, `-boot-loading`,
  `-boot-fade`.

### The handover: the application comes up out of the black, picture and sound together

**The show ends on a full blackout and until this existed the library then simply *appeared*.**
`blackout` ramps to 1 over the last few percent of the sequence, so the last frame the boot screen
draws is an entirely black window — and the very next pulse had the library, the title bar and the
window frame all on screen at full strength. The machine had spent fifteen seconds fading everything
it drew and then handed over with a cut. `App.handOverFromTheDark`, called at the end of
`finishBooting`, is the one gesture that replaces it.

- **One length for both halves**, `App.HANDOVER_FADE` = 650 ms, and the constant exists so there
  cannot be two: the picture coming up and the fanfare letting go are two descriptions of a single
  moment, and two numbers meant to describe one moment are two numbers free to drift. It is the same
  650 ms as `LAUNCH_FADE`, deliberately — the console fades up out of the dark at exactly the rate the
  application later fades up out of it, so the launch opens and closes on the same gesture.
- **It is the end of the loading bar, however that is reached.** A sequence that ran its full length
  and one cut short by `skip()` are the same event here, which is what makes the skip stop being a
  compromise: before this, a key pressed at three seconds left twelve seconds of fanfare playing at
  full strength over a library that was already up. A fanfare that ended naturally is a `stop()` on a
  retired player thread, i.e. a no-op, so the same call covers both.
- **The whole window fades, not the centre.** `finishBooting` has just attached the header and the
  frame, and those arrive *with* the application — a title bar snapping in at full strength over a
  view still coming up would be the cut moved rather than removed. So what fades is `shell`, the
  scene's root.
- **Which forces the scene fill, and the fill has to be that black.** The stage is `TRANSPARENT`, so a
  half-faded root over the default fill is a half-transparent application over the user's desktop —
  not a fade from black but a window that failed to draw. The value comes from `Palette.hardware()`
  because that is the palette the boot screen faded *to*: the first frame of this fade is then exactly
  the colour of the last frame of that one. Ground rule 7 is untouched — a role named, a palette
  asked — and it is the same argument §"the boot screen" makes for `hardware()` existing at all.
  It is restored to `Color.TRANSPARENT` when the fade finishes, in the same handler that forces the
  opacity to 1: an application left permanently behind a black veil, in a window whose corners have
  quietly stopped being see-through, is a far worse fault than anything that could have interrupted
  the fade, and it has no symptom to search for.
- **The cost is a full-scene composite for 650 ms**, which on the software pipeline §7 documents is
  not free — node opacity on a `Parent` renders the subtree to a buffer and blends it. It is one-shot,
  at launch, and it has **not** been measured; if the fade is ever reported as stuttering, that is the
  thing to measure first, and §7's own warning applies about asking what else is on the machine before
  believing the answer.
- **Skipped whole during a smoke test**, and this is the strongest reason the run has a line for it:
  the screenshots are taken on the interface thread immediately after `bootScreen.skip()`, no pulse
  arrives to advance a timeline, and a window left at opacity zero over an opaque black fill would put
  out **an entire run of black screenshots that still passed every other check**. A black picture is
  exactly what a view that failed to lay out looks like, which is the one photograph that would be
  believed — the same argument `[smoke] boot wake` makes one screen earlier. `[smoke] boot handover`
  reads the opacity and the fill back and fails the boot check on either.
- **The fade itself is unphotographable and is not photographed.** Every criticism this file makes of
  a still taken at the edge of a fade applies here in full, and there is no `previewAt` for it: the
  quantity worth checking is that the fade *finished*, which is what the smoke line asks.

### The glass on the two bracket screens (`ui/CrtEffect`)

**A curved tube — rounded corners, a bowed raster, a lit rim, a sheen and a slow sync roll — over the
boot and shutdown screens and nowhere else.** Those two are the only places in the application that draw
no interface at all — they are the console rather than the software — which is exactly what makes them
the only two where a picture *of a display* is the right idea. Everywhere else the 8-bit look comes from
hard edges and sixteen colours; here it comes from the tube those colours would have arrived on.

- **Fenced deliberately.** The same treatment over the library would be a filter somebody chose rather
  than the hardware, and over the runner it is a full-canvas alpha composite competing with the game for
  a frame budget that has no GPU behind it (§7). This is a property of the two screens, not a mood
  option and not a setting.
- **The curvature is the shape of the *mask*, and the picture behind it stays flat.** Barrel-distorting
  the drawn frame is a full-canvas pixel remap per frame, on the machine §7 established has no GPU — the
  single most expensive thing this application could ask for, in front of a screen whose whole job is to
  appear instantly. What the eye actually reads on two mostly-black screens is the **corner shape** and
  the **bow of the scanlines**, and both of those are properties of the glass, so both are baked. Do not
  "finish the job" by warping the content.
- **`CURVATURE` and `CORNER_SHARE` are two knobs, and they were one to begin with.** Reading the
  silhouette off the raster's own warp gives a region that meets the window at exactly four points — the
  middles of its edges — and falls away from all four. That is a **lens, not a television**: the sides
  bowed in over their whole height and the corners closed to points. The screenshot said so immediately
  and nothing else would have. Split, the raster can sag hard enough to see (0.12) while the outline
  stays a rounded rectangle (0.13 of the shorter side ≈ 104 px of corner here) — and the warp still
  feeds `edgeDistance`, so the sides bulge gently the way the front of a tube does.
- **The mask is rasterised once and blitted, never drawn row by row.** Scanlines are the obvious thing
  to write as a loop of one-pixel `fillRect`s, and at this window's height that is around four hundred
  calls a frame on a software rasteriser. Every part of the glass — curvature, grille, vignette, glass
  edge, rim, case and sheen — is static for a given size and palette, so all of it bakes into one
  `WritableImage` and costs exactly one `drawImage` afterwards. Same lesson `MoodOverlayRenderer` learned
  pre-tiling its layers, applied before it could cost anything. The cache is keyed on the palette **by
  identity**, exactly as the level meters' colour ramp is: a `Palette` is immutable, so a different look
  is always a different object.
- **Two colours in one blit, and the arithmetic is exact rather than close.** The glass both darkens
  (towards `SHADOW`) and *lifts* (towards `TEXT_PRIMARY`, for the rim and the sheen). Source-over of a
  shade `s` then a lift `t` collapses to one fill: `alpha = 1 - (1-s)(1-t)`, carrying `s(1-t)` of the
  shadow and `t` of the light, which sum to a whole colour. Two images would have been the obvious way
  and would cost twice the fill rate on the one machine that cannot afford it.
  `theTwoDirectionsCollapseToOneFill` pins it against ideal sequential blending.
- **No colour is named.** Everything darkens towards `SHADOW` and every lift goes towards `TEXT_PRIMARY`,
  both out of whatever palette the caller passes — `Palette.hardware()` for both screens. Ground rule 7
  is untouched.
- **`SCANLINE_PERIOD` is 3, not 2.** At a fifty percent duty cycle half of every screen is darkened and
  the effect stops reading as a grille and starts reading as the picture being dimmed, which is a
  different thing. It also beats against the pixel font's own two- and three-pixel strokes and puts a
  moire pattern through the title. Measured down a stroke of the 44px title on the real shot: **195
  clear, 173 soft, 126 dark**, which is the 0.38 and 0.12 the constants ask for. The rows are counted
  **on the tube rather than on the window**, which is what makes them bow.
- **A vertical grille was deliberately left out.** An aperture mask is the obvious next cue and it is the
  one that cannot be added safely: at a period of 3 it aligns with the font's three-pixel strokes and
  alternates against its two-pixel ones, which is the same moire the paragraph above rejects a two-row
  cycle for — in the axis where the strokes are thickest. The drama is spent on the curve instead.
- **`RIM_LIFT` is load-bearing and is the only reason the corners are visible at all.** The case is
  `SHADOW` and so is the room, so a corner blacked out is a corner nobody can see — the same trap that
  ate one attempt at the boot glitch. What the eye reads is a **lit curve where the glass ends**, easing
  back to `BEZEL_LIFT` over 16 px of moulded plastic. Set the lift to zero and the whole curvature
  becomes invisible while every test about its geometry still passes.
- **The other darkenings are invisible over bare room, and that is correct rather than a bug.** The
  grille, the vignette and the glass edge all shade towards `SHADOW` — so they appear only where
  something is drawn, which is what a tube does. See §"the boot screen", where tearing this image
  sideways turned out to change not one pixel.
- **`ROLL_SECONDS` is 9 — 0.11 Hz against §8b's 3 Hz cap on anything full-screen and rhythmic.** It is
  the one thing on either screen that moves without being asked to, which is what stops a held frame
  reading as an application that has frozen — the same job the boot screen's starfield and the companion
  window's spinning record do. `ROLL_BANDS` went from 6 to 16 off a screenshot: the room is pure black,
  so every step in the band's ramp is against nothing at all, and at six it read as three or four
  horizontal bars rather than as a swell. Each band is now cut to `tubeHalfWidth` at its own row, so the
  roll stops at the glass instead of running out over the case — which would say out loud that the
  rounded corners are painted on.
- **Anything laid out near the top or bottom of either screen is measured against `tubeHalfWidth`, not
  against the window.** The corner arc costs real screen area and both screens' captions are centred, so
  the prompt row and the skip-hint row are pinned by test (`theContentClearsTheCurve`): running a caption
  into the case throws nothing and simply cuts it off.
- Every quantity is a static pure function (`curveX`, `curveY`, `cornerRadius`, `edgeDistance`,
  `insideTube`, `tubeHalfWidth`, `scanlineShade`, `vignetteShade`, `edgeShade`, `bezelLift`, `glareLift`,
  `rollCentre`) for the usual reason: a one-pixel scanline looks the same in a still whether the period
  is right or wrong, a vignette on a black ground is invisible except under something drawn on top, and
  the roll is the one thing here that moves. `CrtEffectTest` is what can actually be checked — and it
  earned its keep immediately, catching a `>=` that should have been `>` in the corner chord, which made
  the outermost row of the tube report as *no* glass rather than as the straight span between the two
  corners.

### The shutdown screen: ejecting the cartridge (`ui/ShutdownScreen`)

**The application closes on a black screen with a sweeping bar on it, and it exists because closing used
to look exactly like a crash.** JavaFX runs `Application.stop()` on the interface thread *after* the last
window is hidden, and the slowest thing in it is by far the go-librespot child: `SpotifyDaemon.stop()`
asks it to exit politely and then gives it `GRACE_SECONDS` = **5** before killing it. So pressing close
made the window vanish and then left the process sitting in the dock, unresponsive, for up to five
seconds — on macOS long enough to earn a spinning cursor. Nothing was wrong, and there was no way at all
to tell that from outside.

So the order is inverted. The window **stays up**, shows the screen, and the teardown runs on a thread of
its own; the interface thread is free the whole time, so the window still paints, still moves and still
names the step it is on. `Platform.exit()` is called at the end, which reaches `stop()` and finds the
work already done.

- **`stop()` split into `stopDrawing()` and `releaseResources(report)`, both guarded and idempotent.**
  The split is the whole safety argument, and it is **ground rule 3 rather than luck**: everything
  `releaseResources` touches lives in `playback/`, `audio/`, `analysis/` and `spotify/`, none of which
  may import `javafx` (`LayeringTest` enforces it), so none of it *can* reach the scene graph whichever
  thread calls it. What genuinely is the scene graph — every `AnimationTimer`, plus `runner.stop()`
  filing the run in progress — stays on the interface thread and happens before the screen goes up.
- **`requestQuit()` is the one door**, and all four ways out go through it: the header's close button,
  the companion window's quit button, `Esc` on the boot screen, and `stage.setOnCloseRequest` (which is
  how the platform asks — Cmd-Q on macOS). `stop()` still works for the paths that never reach it.
- **The companion strip is 224 px wide and has nowhere to draw this**, so `requestQuit` brings the main
  window back for it — shown *before* the companion is hidden, because JavaFX exits when the last window
  goes and that would close the application at the exact moment it is trying to say it is closing.
- **The bar sweeps rather than fills.** Nothing here knows how long a subprocess will take to exit, and a
  bar that filled to 90% and stopped would be claiming progress it cannot have — which reads as precisely
  the hang this screen was built to stop looking like. It wraps rather than bounces, so the motion is
  always forwards: a bar that reversed would read as progress being undone.
- **Same `Palette.hardware()` as the boot screen**, and the layers are cleared (`setMood(null, null)`)
  so an `ABOVE_CONTENT` scanline layer is not the one thing still drawn in somebody's palette. The two
  screens bracket the application: at one the system has not started and at the other it has stopped.
  The splash is deliberately smaller than the boot screen's — this is a goodbye rather than an arrival,
  and equal weight would make closing feel like as much of an event as opening. It goes through
  `BootScreen.wrapName`, so the two cannot break the title in different places.
- **There is no way to cancel.** By the time this is on screen the audio line is closing, and a shutdown
  that could be called off would be a state every view behind it would have to know about. The title bar
  goes with it, because a close button on a screen that is already closing can only be a second press
  that does harm.
- **The picture tears as the contact breaks, and that is the same tear the boot screen draws.**
  Pressing close is a cartridge being pulled out of a live slot, which is the insert's own electrical
  event backwards, so `BootScreen.drawTear` is now one implementation called by both screens — a second
  copy of it would be free to drift, and the way it would drift is the two ends of one session looking
  like different machines. Three things differ, all of them deliberate:
  - **It goes *over* the picture rather than instead of it.** The boot screen clears the room first
    because its glitch replaces a phase; here the screen is already up, and a signal breaking up breaks
    up whatever was being shown — the name, the bar and the caption.
  - **There is no white flash.** A flash of light is *power arriving*; a screen that lit up as it was
    cut off would be saying the opposite of what happened. That one paragraph is the whole of what the
    shutdown does not borrow.
  - **It finishes before the cartridge moves** (`GLITCH_OUT` = 0.15 against `EJECT_IN` = 0.18), because
    the tear is the cause and the eject is what the machine does about it. Overlapping them puts both on
    screen at once and reads as one confused event; in sequence it reads as two. At `EJECT_SECONDS` that
    is 0.45 s against the insert's 0.55 s, and a test pins them to within a tenth of each other.
- **And the cartridge comes back out, with `assets/sounds/Cartridge_Out.mp3` under it.** The boot screen
  opens on a cartridge being pushed into a slot; this hands it back. They are one gesture and its
  reverse, so this screen borrows the boot screen's geometry rather than inventing its own — the same
  slot, the same hover gap, the same inlet measured off the artwork by `SpriteSheet.footprint` — and it
  borrows the boot screen's rule about the name as well: **the title is on the screen while the cartridge
  is not, and on the cartridge's own label once it is**. So the splash fades out as the cartridge rises
  and the name reappears where a cartridge has always carried it, wrapped by the same `BootScreen.wrapName`
  in the same places. The two are never on screen at once, in either direction. The rim of the slot cools
  from `HIGHLIGHT` to `OUTLINE` as it leaves, which is the exact reverse of the boot screen's warming.
  - **It starts halfway out, where the boot screen left it** — `BootScreen.seatedY`, asked rather than
    re-derived, so the two screens are one continuous object instead of two animations that agree by
    coincidence. Seated means `SEAT_SHARE` of the cartridge inside and the other 45% still above the lip;
    beginning from entirely swallowed had the machine hand back something it had *eaten*, and spent the
    first third of the travel climbing to a position nobody ever moved it away from.
  - **Which forced the label to fade in**, because the cartridge is now on screen from the first frame
    and its label is 68% of the artwork — most of it above the lip. Drawn unconditionally the name would
    be across the screen *and* on the label at the same instant, which is the one thing both bracket
    screens promise never happens. `plateAlpha` is written as `1 - splashAlpha` rather than as a second
    ramp, so the handover holds by construction: two numbers meant to add to one are two numbers free to
    drift, and the way they would drift is the title appearing twice.
  - **One clock, every element a pure function of it** — `emergence`, `splashAlpha`, `blackout` — for the
    reason the boot show has one: five timers is five places for the sequence to get out of step with
    itself, and nowhere to ask what this looks like at a stated instant. Which is what `previewAt` needs,
    since a still of a fade taken at the wrong moment is a still of an empty screen.
  - **`EJECT_SECONDS` is 3.0 and is deliberately *not* the sound's length, which is the opposite of the
    boot screen's arrangement.** There `setSequenceSeconds` takes the fanfare's own measured duration
    because a first launch is an event worth standing still for. The eject sound is **7.71 s**, and
    making somebody wait nearly eight seconds to close an application is precisely the hang this screen
    exists to stop looking like. So the picture is fixed at three and the sound is faded out under the
    blackout instead.
  - **Quitting therefore waits for the animation as well as for the teardown**, `App.requestQuit`
    exiting only when both are done. That is the cost of the feature and it is stated rather than
    discovered: with a daemon running the teardown's five-second grace period dominates and the eject is
    free; with none it is the whole of the wait, and the measured 8 ms below becomes **3007 ms**. Exiting
    on the teardown alone would put a three-second animation up and close the window a frame into it,
    which is the frozen dock this screen replaced with an extra class in front of it.
  - **The sound is stopped at the blackout, not at the exit, and the two are separate callbacks for
    that.** `setOnFading` fires at `FADE_OUT` and `setOnFinished` at the end; `SoundEffect.stop()` fades
    over a quarter of a second, and the six tenths between them is the room that fade needs. Stopping it
    at the exit would fade into a process that has already gone, and the tick that leaves is exactly what
    `FADE_SECONDS` exists to prevent.
  - **The window frame comes off too**, through `updateWindowFrame()` — which now decides from *four*
    states rather than three. Same argument as the boot screen's, run backwards: three pixels of amber
    around a black screen is the software's look outliving the software, and here there is not even a
    title bar left for the border to be marking the edge of.
- **The smoke test now closes through `requestQuit()` rather than `Platform.exit()`**, which is the only
  way to exercise it: the failure mode of getting a background teardown wrong is a process that never
  exits, and a run that closed itself by a different route than the close button uses would report
  nothing about the route the user takes. Measured, with no daemon running: **8 ms of teardown**, inside
  a **3007 ms** wait that is now the eject animation. The screen itself is photographed separately by
  `captureShutdown`, which tears nothing down — running the real teardown mid-run would close the sound
  card out from under every check after it. **It takes three shots**, `-shutdown-glitch`, `-shutdown`
  and `-shutdown-eject`, because no two of the three things on this screen are ever there together and a
  single picture would look like whichever two it missed had failed to draw. The instants live on
  `ShutdownScreen.Moment` beside the timings, exactly as `BootScreen.Movement` does, rather than being
  copied into the smoke test where the two could drift — and each is the **middle** of its movement,
  since a still of the edge of a fade is a still of an empty screen.
- Screenshots: `docs/screenshots/sdmk-shutdown.png`, `-shutdown-glitch`, `-shutdown-eject`.

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
- **Side rail** — Library, Favorites, History, Racer Select, **Spotify**, **Moods**, Settings.
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
│                 PcmListener, Levels, LevelAnalyzer, SmoothClock, AudioMetadata, AudioException,
│                 SoundEffect (the boot fanfare, on a line of its own)
├── analysis/     BeatmapAnalyzer, Beatmap, BeatmapCache, OnsetDetector, Fft, BeatmapService,
│                 BeatmapIndex
├── game/         RunnerGame, RunnerListener, Course, Lane, Entity (sealed), Obstacle, Coin,
│                 Star, EntityState, ScoreKeeper, Rank, ScoreEntry, SpeedClass, ScriptedDriver
├── spotify/      SpotifyBinary, SpotifyConfig, SpotifyDaemon, SpotifyApi,      ← M10
│                 SpotifyEvents, SpotifyTrack, SpotifySession
├── persistence/  Repository<T> (interface), LibraryRepository, ScoreRepository
├── assets/       AssetRegistry, SpriteSheet, SpriteAnimation, RacerFrame
├── mood/         Palette (+ hardware(), the console's black and white,
│                 + bootRainbow(), the colour test the start-up title cycles), PaletteRole (enum),
│                 GbaColor                                        ← built in M4
│                 PaletteCss                                       ← M9
│                 Mood, Moods, MoodLayer (sealed), LayerStyle, ZBand,
│                 LayerBlend, GradientLayer, GradientStop, ImageLayer,
│                 ProceduralLayer, PixelTile, Bayer, MoodRepository,
│                 PaletteImporter, PaletteBuilder, ImageQuantizer,
│                 MoodValidator, MoodIssue, ColorMath, MoodReactivity ← M11
└── ui/           MiniPlayerView, FullscreenView, LibraryView, BeatmapTimeline, RunnerView,
                  RacerSelectView, LevelMeterView, ComplexityPanel,
                  MoodSelectView, MoodCustomizerView, PixelEditorView,
                  MoodOverlayRenderer, GbaColorPicker,
                  BootScreen, ShutdownScreen (the two ends, both on Palette.hardware()),
                  CrtEffect (the glass those two are seen through, and nothing else),
                  SpotifySearchDialog (add from Spotify, from the library's own header)
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
- **Combo** — every pickup or cleared obstacle takes it up one, holding at **`ScoreKeeper.MAX_COMBO`
  = 10**, and it multiplies what the next one pays. **Only a bump breaks it.** Not a coin left in
  another lane: there are three lanes and one racer, so a combo broken by an uncollected coin would
  be a combo nobody could ever build. What it counts is *mistakes*.
  - **It multiplies the balance and can never touch the rank.** `coins` takes the multiplier;
    `coinsCollected` takes exactly one, because the course held exactly one. That is the split
    `ScoreKeeper` was already built around — the rank is a fraction of what the generator put on the
    course, and a multiplier on its numerator would let a good streak read as more coins than the
    course ever held, straight past 100%.
  - **The bump penalty is flat and deliberately not scaled by it.** Losing a multiplier that took a
    minute to build is already much the larger of the two costs; five coins a level on top would
    make one mistake at the top of the meter unrecoverable, which is the opposite of what the
    invulnerability after a bump exists to do.
  - **Jumping a wall builds it and pays nothing else**, which is the only reward the jump has ever
    carried. The one control the player has to learn now feeds the one number that makes the rest of
    the course worth more, instead of merely avoiding a loss.
  - **The multiplier is applied before the coins are added**, so the pickup that takes the meter to a
    new level is paid at that level. Rewarding it at the old one makes the meter and the number it is
    multiplying disagree in the one frame the player is looking at both.
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
- **The combo heat: the screen gets excited as the meter fills, and it is `PRIMARY`.** Two parts over
  one colour, both wash-only — nothing about the combo reaches the geometry, same rule as the beat.
  The **standing** tint is squared and tops out at `COMBO_TINT_ALPHA` = 0.13; the **beat** adds
  `COMBO_BEAT_SURGE` = 0.20 on top, linear in the heat, for a heaviest frame of **0.33** against the
  0.35 ceiling. The role is named once as `RunnerView.COMBO_ROLE` and used by the wash, the meter,
  the multiplier and the horizon, so the four cannot drift apart and the test measures the role the
  road is actually seen through rather than one written down twice.
  - **It was `ACCENT` until 2026-08-16 and the change was asked for.** The old argument was that the
    horizon already flashes to the accent on every beat, so a screen sliding that way reads as the
    beat taking over the picture rather than as a colour arriving from nowhere. What that missed is
    that the accent is the palette's *cool* bright role: over this backdrop a cyan wash reads as the
    light changing, where the yellow reads as the picture being **lit**, and at the alpha this is
    capped at that difference is most of the effect. The yellow is also already the colour of
    everything the combo is multiplying — the coin tally, the score plate, the star's own timer.
    `PRIMARY` is not one of the four protected roles, which is what keeps the swap safe.
  - **The one cost is that `PRIMARY` is also the pickup flash's colour**, so the two would compete if
    the standing tint were strong. It is another reason it is not.
  - **The two knobs were split apart for a measured reason.** Multiplying the surge *by* the standing
    tint makes the middle of the meter a cube of a small number, so a combo of five showed nothing —
    which is exactly where the player most needs to see something being built. Adding it keeps the
    standing part faint and lets the beat carry the effect: excitement is motion, not a filter.
  - **`COMBO_TINT_ALPHA` came down from 0.20 to 0.10 on a measurement, not on taste** — and back up
    to 0.13 with the move to yellow, on a request for a combo that shows more. The scripted driver
    spends **about ninety percent** of a clean 50cc or 100cc run pinned at the top of the meter
    (against a third of it at 150cc and 200cc). That is the combo working — a clean run is the whole
    thing it rewards — but it means the *standing* tint is what the game looks like nearly all of the
    time for anyone driving well, and at 0.20 that stopped reading as an earned state and started
    reading as a mood nobody chose, burying the beat's own washes underneath it. **So "more
    noticeable" was spent on the surge rather than on the tint**: the standing part went up by three
    hundredths and the beat by six, and the surge took the whole of the headroom left under the cap.
  - **It is drawn over the beat wash, not under it, and the order is load-bearing.** It was the other
    way round first and the two cancelled: the beat wash darkens hardest at the instant of the strike,
    which is exactly when the combo's surge peaks, so the accent was dimmed away precisely when it
    was meant to be seen and the beat looked identical at every combo level. Over the top, a strong
    beat at a full combo is a dark frame *in the combo's colour*. The bump's alarm still goes last —
    it is the one event the player may have missed the cause of.
  - **The horizon's beat floor rises with the heat**, capped at `COMBO_HORIZON_SHARE` = 0.75 of the
    way. Let it reach the whole way and a full combo leaves the line permanently lit, with the beat
    flash having nowhere left to go — it disappears at the moment the game is most exciting.
  - **The horizon's *colour* migrates with the heat too**, from `ACCENT` at no combo to `COMBO_ROLE`
    at a full one, rather than the line flashing in one colour over a floor in another. Measured on
    the race shot at a full combo, the line reads `#d6ba4f` where it used to read `#4f4c78` — the
    widest thing on screen is the combo's colour, which is exactly the point. The alternative, a
    yellow floor flashing to cyan, puts a hue swing on that line twice a second and reads as a fault
    rather than as a beat; here the hue only moves as fast as the heat glides.
  - **The heat glides, timed off the game clock from a start value and a start time** — the same
    arrangement as the lane glide and the event flash, never a per-frame accumulator, so it pauses
    with the music for free. Without it the tint steps once per coin, which on a fast class is a
    visible jolt several times a second in the corner of the eye.
  - **Read off the tally per frame rather than pushed by a listener**, which is the opposite of what
    every other effect here does and is right for this one: it is a continuous value being glided
    towards a target, so a dropped frame costs it a frame of glide and nothing else.
  - **`RunnerComboHeatTest` holds the bar**, because this wash goes *over* the entities: coins and
    bumps must stay ≥ 25 ΔE apart through the heaviest wash **in every mood**, and the whole thing is
    capped at 0.35 — the same ceiling `ABOVE_CONTENT` overlay layers get, for the same reason.
    Confirmed to fail when the alpha is raised, and it fails on the **light** mood first. Measured at
    0.33 in `PRIMARY`: coins against bumps 99 in the dark mood and 81 in the light one.
    **The pair with the least room is now the star against the road** — 75 and 46 — because the wash
    *is* the star's own colour, so the star cannot move at all while the road is pulled towards it.
    That is the one that would go first if this were raised again.
  - The rate is the track's own beat and nothing else, so it is inside §8b's 3 Hz cap without needing
    an oscillator of its own to clamp. **M11 wired it to "Reduce motion" along with the rest**, and
    it came for free: the surge is a function of the beat pulse, and that is one of the two things
    the switch gates.
- **The combo meter is ten 32×18 blocks standing up the right-hand edge**, centred on the height of
  the canvas, captioned `COMBO`, filling **upwards**, with the multiplier under it at 16px. Blocks
  rather than a continuous bar — the same convention the library's rating meter uses, and the
  readable one at this size, because a small whole number should be *counted* rather than measured
  against a track. Upwards because that is the direction the L and R level bars flanking the same
  road fill, so the one gauge between them needs no second convention. Its place is held whether or
  not a combo is running: a meter that appeared when the streak started would be something arriving
  in the corner of the eye at the exact moment the player has stopped looking away from the road.
  Only at the top of the meter does the column itself take the beat.
  - **It sat under `COINS` and `SCORE` until 2026-08-16**, which is the shorter path to reading what
    it does and is what it gave up: a vertical bar is the shape a filling meter actually has, and
    there is no room for one down the left where the rank and the best run already are. **So it
    gained a caption** — a bare column of blocks on an edge that carried nothing but the song title
    says nothing about what it is counting, where under the two numbers it was multiplying it needed
    no label at all. The multiplier moved from beside the meter to beneath it for the same reason:
    to the right of the bar there is nothing but the edge of the window.
  - **Centring it is what keeps it clear of everything else**, rather than a number measured against
    the block above it. The right-hand edge already carries the song at the top, and the bottom
    carries the star timer and the controls line; a meter anchored under the song has to be
    re-checked against all three every time one of them moves or the window resizes, where a centred
    one sits opposite the kart and cannot reach any of them. The caption and the multiplier are part
    of what is centred — leave them out and the column sits high enough to read as hand-placed.
  - **The width costs the road nothing, and that was checked rather than assumed.** The road is
    `ROAD_HALF_FRACTION` either side of centre, so at its widest — the racer's own line — it ends at
    nine tenths of the canvas, and the bar's left edge is past that. Centred, it is beside a part of
    the road that is narrower still.
  - **`RunnerView.settleCombo()` exists because none of this photographs.** The heat glides from
    wherever the picture was, and before a preview the picture was nowhere — so the first frame after
    `previewDrivenTo` drives a whole course finds the combo at ten and the heat at zero and starts
    easing, which in a live run is correct and lasts a third of a second and in a still is the whole
    photograph. Without it `docs/screenshots/sdmk-race.png` is a full meter over a screen with no
    light on it, which is a picture of the effect being broken. Same reason `StructureView.settle()`
    and `MiniPlayerView.previewAt` exist, and it was caught by reading the horizon's pixel out of the
    shot rather than by looking at it.
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

  **Superseded on 2026-08-16, and the last sentence of that paragraph is now false: there is no GPU.**
  Reported as the game being "very laggy" since the combo landed. `-Dprism.verbose=true` on the real
  application says what nothing else would:

  ```
  Prism pipeline init order: es2 mtl sw
  GLFactory MacGLFactory could not be initialized. ES2Pipeline not available.
  error initializing pipeline com.sun.prism.mtl.MTLPipeline — could not create an instance
  *** Fallback to Prism SW pipeline
  ```

  **Every pixel is blended by the CPU**, so frame cost is proportional to canvas area × overdraw and
  a full-canvas fill is one of the most expensive things the runner can do. Checked across versions
  rather than assumed: **21.0.6 and 23.0.1 also fall back, and 25.0.2 does initialise Metal and then
  dies** with `NSInvalidArgumentException: object cannot be nil` the moment it presents a window. So
  there is no working GPU option on this machine today and this is a constraint to design inside,
  not a bug to wait out. Do not "fix" it by pinning an older JavaFX; that trades a slow renderer for
  one that crashes.

  Three things landed together and only the third got the blame:
  1. **`stage.setMaximized(true)`** (§3b) — the canvas went from 1440×800 to 1800×1036, and under a
     software rasteriser that is a straight 1.6× on everything.
  2. **The combo's wash** made a **third** full-canvas alpha fill, on for about ninety percent of a
     clean run. Measured at the maximised size, p50 frame interval by wash count:
     **0 → 29.7 ms, 1 → 29.2 ms, 2 → 32.6 ms, 3 → 42.2 ms.** The third one costs ten milliseconds.
  3. **The transparent stage is innocent**, which was worth measuring rather than assuming: 45.3 ms
     transparent against 46.5 ms opaque, i.e. noise. `StageStyle.TRANSPARENT` costs nothing here.

  **The smoke test cannot see any of this and said so the whole time** — `frame cost: 0.16 ms,
  comfortably inside a 60 fps frame` while the real interval was 42 ms. It measures command
  recording, and it deliberately does not maximise. That is this section's own warning arriving with
  numbers on both sides of it.

  **What was done about it** (see `RunnerView.compositeWashes` and `drawSky`):
  - The beat wash, the combo heat and the event flash are **composited into one fill** instead of
    three. Source-over of constant colours collapses exactly, so the order and the look are
    unchanged; the drawn frame moves by at most one level of 255, in the direction of the *more*
    faithful answer, because the old path rounded to eight bits three times and this rounds once.
    `RunnerWashCompositeTest` pins the arithmetic against ideal sequential blending.
  - The sky's **backdrop fill was removed**: its five bands are whole-pixel and tile `[0, horizonY]`
    flush, so the fill underneath them was painted over in full every frame. Bands tiling rather
    than overlapping is also why *caching the sky to an image would save nothing* — both touch each
    pixel exactly once. The same is true of the verge, which is why it is not cached either.
  - The verge is drawn with **`fillRect` rather than `fillPolygon`**. Both its ends span 0 to
    `width`, so it was an axis-aligned rectangle going through Marlin's edge-list and coverage
    machinery, over the seventy percent of the canvas below the horizon. Verified pixel-identical.

  **The rule this leaves behind: a new full-canvas effect is not free here, it is about ten
  milliseconds.** Anything screen-wide that wants to be added has to go through `drawWashes` and be
  composited with the others, not painted on top of them.

  **Corrected the same day, and it changes what the numbers above mean rather than whether they were
  right: a `flurry` animated-wallpaper process was running on the machine throughout, and that is
  most of what "very laggy" actually was.** The hardware is an **M4 Mac** — not a slow machine, and
  never the constraint this section reads as describing. An animated wallpaper repainting the desktop
  continuously competes for exactly the resource a software rasteriser needs, so every figure above
  was measured under contention nobody knew was there.

  What survives unchanged: **the Prism fallback is real** — `-Dprism.verbose=true` says so whatever
  else is running, and the version sweep stands. The three optimisations are correct, pixel-identical
  and worth keeping; one composited fill instead of three is simply the better way to write it. And
  the *relative* shape of the wash measurement — that the third full-canvas fill costs materially
  more than the second — is a fill-rate result that contention scales rather than invents.

  What does not survive: **the absolute milliseconds, and the conclusion drawn from them.** 29.7 ms
  for an empty frame is a number from a busy machine, and it has **not** been re-measured on a quiet
  one — so do not quote it, and do not treat this project as one that has to be designed inside a
  starved CPU budget. The ten-millisecond rule above is a good habit and a bad law.

  **The lesson is the cheap check that was never run.** Three JavaFX versions were swept, the
  transparent stage was ruled out by measurement, and the wash count was bisected — all of it careful,
  all of it inside the application, and none of it asked what else was on the machine. Before the next
  "it feels laggy" becomes a rendering investigation, look at the process list first.
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
  - **It presses away from whichever edge the racer is on.** A key pressed into the wall is a key
    that correctly does nothing, and reporting that as `DID NOTHING` is a red light nobody can trust.
  - **And it runs against `previewAt`, never `previewDrivenTo`** — see below. The photographed
    moment is chosen to be a *wall*, and a wall is the one thing the scripted driver jumps, so a
    driven preview leaves the racer airborne and off the middle lane **by design**: `game.jump()`
    no-ops while a jump is running, so a working jump key would be reported as a dead one. Both
    lines failed exactly this way once.
  - **A library holding streamed tracks makes these lines non-deterministic**, and that is not a
    fault in them. Shuffle can make a Spotify song current, a streamed song has no audio until the
    daemon is connected, and the runner only claims the keys while the music is running — so both
    lines correctly read `DID NOTHING` and `audio file` reads `null`. Check that line before
    believing a control is broken, and use a local-only profile when verifying one.
- **`game/ScriptedDriver` is the greedy player, and it has two callers.** It was forty lines of game
  policy inside `App`, untestable and about to be copied. It holds no JavaFX, so it is exercised by
  handing it a course, and it is the difference between the smoke test's lap and a screenshot of a
  run in progress being two implementations that can drift.
- **`RunnerView.previewAt` jumps the clock and `previewDrivenTo` drives to the moment**, and the
  split is not tidiness. Jumping resolves everything behind the playhead as skipped — the correct
  rule, and what stops a course being collected by dragging the seek bar — so the race screenshot
  came out with a **zeroed head-up display**: no coins, an empty combo meter and a rank of D over a
  road nobody had driven. Every readout in that corner is only worth photographing once something
  has happened to it. The control checks want the opposite: a known state to fire a key at.
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

**Built. See "As built (2026-08-16, M11)" at the end of this section for what the design below
turned into, what it cost, and the three places it was departed from on purpose.**

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

**Done, and through one flag rather than five.** `RunnerView.setReduceMotion` gates `beatPulse` and
`eventWash`, which are the two places every full-screen effect is derived from — so the zoom, the
wash, the combo surge, the horizon flash and both event flashes stop together and none of them can
be missed by a later change. The road, the entities and the timing are untouched: the game plays
identically. `AppState.reduceMotionProperty` is where the switch lives, because it is a property of
the person watching rather than of a window, and `SettingsRepository` persists it — somebody who
needs it needs it at every launch.

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

### As built (2026-08-16, M11)

**Ten moods ship, and nine of them cost nothing per frame.** That sentence is the milestone: a mood
whose layers all stand still is rasterised once when it is installed and the canvases are never
touched again, so choosing Bowser Castle over Dark changes the whole look of the application and
changes its frame cost by zero. 1017 tests, up from 698.

**The layers reach the window through one pane, and everything that changes what is on screen sets
its content.** `ui/MoodOverlayRenderer` is a `StackPane` holding a backdrop canvas, the interface,
and an overlay canvas; `shell.setCenter` holds it once and `overlay.setContent(...)` is what the
boot screen, the main layout and presentation mode each swap through. Swapping the shell's centre
instead would take the layers away along with the view - the same shape of fault as `F5` taking the
title bar with it.

- **`.root-pane` gave up its backdrop, and it had to.** It painted the three-stop ramp the window
  has always had; a layer drawn `BEHIND_CONTENT` is behind that pane, and a layer behind an opaque
  pane is a layer nobody can see. The pane is `transparent` now and the renderer paints the
  identical ramp on the canvas underneath, so **a mood with no layers comes out exactly as it did
  before**. A transparent background is still a background, so nothing about picking changed.
- **Both canvases are `setMouseTransparent(true)`,** which is not optional and is written down in
  §11 for the third time: a `Canvas` is picked over its whole rectangle whatever it has drawn, and
  these two span the window. Left pickable, the overlay would swallow every click in the application
  while still hovering correctly.
- **And both are `setManaged(false)`, which is a different rule and was missed until 2026-08-16.**
  A `StackPane`'s minimum size is the largest of its children's, and a `Canvas` is not resizable — it
  reports its own width as its minimum. Two canvases sized *to* this pane therefore made the pane's
  minimum **whatever the pane last was**, and this pane is the whole middle of the window: it could
  grow and then never shrink again. Measured, the `StackPane` holding an 1800px canvas answers
  `minWidth = 1800` where a plain `Pane` answers 0 — which is why every *other* canvas view here
  (`BootScreen`, `LevelMeterView`, `BeatmapTimeline`, `ComplexityScatter`, and the `CanvasHolder`
  inside `RunnerView` and `StructureView`) is a `Pane` and never had this. **Reported as the window
  cropping its views instead of resizing them**, which is exactly what it looked like: the
  application comes up maximised, and the restore button then left every view still laid out at
  1800px with the window's edge cutting through it. Unmanaged, the canvases are excluded from the
  minimum *and* from the layout pass, and stay at (0,0) spanning the pane — which is where a child
  nobody positions sits, and `.mood-overlay` has no insets to make that a lie. `resized()` is still
  what gives them their size.
- **`[smoke] window shrink` is the check, and nothing else could be.** A screenshot is taken at one
  size, so it photographs a perfectly good interface — the crop only exists at the *second* size —
  and the quantity is a minimum computed by a live scene graph, which no unit test here can reach.
  It grows the shell, shrinks it back and prints `centre 809 -> 1169 -> 809 px in a window of 1440`;
  before the fix the last number was 1169. It resizes the **root** rather than the stage, because a
  stage resize comes back through the window system on a later pulse and the smoke test is holding
  the interface thread.

**The measurement, and the 58 fps target it fails.** The figure the spec asks for is unreachable on
this machine, and not because of this milestone: **a frame with no layers at all already takes
thirty milliseconds**, because Prism falls back to its software pipeline here (§7). What M11 owes is
not to make that worse, and the smoke test measures exactly that:

```
[smoke] moods that cost 0 : 9 of 10 are flattened to a still picture and never redrawn
[smoke] mood rebuild      : worst 96 ms, paid on a mood change or a resize and never per frame
[smoke] mood frame cost   : 30.4 ms with no layers, 37.5 ms on sky_garden
                            - drifting layers add 7.1 ms, still ones add nothing
```

- **It is measured by `Scene.snapshot`, not by timing canvas calls, and the difference was a factor
  of two hundred.** Timing `repaint()` reported the heaviest mood at **0.05 ms a frame**; rasterising
  the same frames reported **7.1 ms**. That is §7's own warning arriving with numbers on both sides
  of it: a `Canvas` call records a command, and on a machine with no GPU the command is three orders
  of magnitude cheaper than the pixels. `Scene.snapshot` runs the window through the same pipeline
  the pulse does, so the *difference* between two moods is honest even though the absolute figure
  understates a real frame (no vsync, no layout pass, no competing timer).
- **Pre-tiling a drifting layer took it from 10.7 ms to 7.1 ms.** A tiled layer was being repeated
  across the canvas per frame - several hundred small `drawImage` calls. It is now repeated once at
  rebuild into a picture one whole tile larger than the canvas, so any scroll offset is a *source
  rectangle* and a frame is one blit. The pixel count is identical; what went away is the per-call
  setup. Only worth doing when the picture is at most a sixteenth of the canvas by area
  (`PRE_TILE_AREA_RATIO`): a gradient is rasterised at canvas size already, and pre-tiling one would
  spend four canvases of memory to save three draws.
- **The remaining 7.1 ms is the full-canvas alpha composite itself and cannot be optimised away
  here.** So `RIBBON_ROAD`'s starfield was made **static**: a starfield is a backdrop rather than a
  parallax and loses nothing by standing still, where standing still takes it from 8 ms to 0.
  `SKY_GARDEN` is the one preset that moves, deliberately - a mood system whose scrolling nobody
  ever saw would be a feature nobody knew was there - and it is the one that carries the cost.

**`mood/PaletteBuilder` is why ten moods could ship instead of three.** A preset names the room it
is set in and its two brand colours - four lines - and everything else is derived and then *forced*
to clear the same bars `MoodsTest` holds the built-ins to. All ten passed every existing check on
the first run, which is the whole return on the class: sixteen colours picked by eye takes an hour
and comes out *plausible*, which is worse than muddy. The light mood is left hand-written on
purpose; it is the palette whose failures taught the builder what to check for.

- The corrections are lightness moves, never hue moves. Hue is what a mood *is* - the entire
  difference between Sunset Wilds and Boo Lake.
- It enforces three things a badly chosen palette flattens silently and that nothing else was
  checking: the bevel's lit edge above its face and its shadowed edge below it, a selected row
  distinguishable from an unselected one, and a pressed control darker than a hovered one.

**`DARK` is still the default, and `PEACH_CIRCUIT` is not.** §8b names the latter, and this is a
deliberate departure: the dark purple and amber look is the one the application has had since its
first window, it is what every screenshot in `docs/` shows, and it is the identity
`Palette.defaultPalette()` is documented as. Changing which mood a first launch opens in would have
been a change to what the application *is*, made as a side effect of adding nine more looks.
`MoodsTest.defaultIsUnchanged` pins it.

**The presets ship at `PRESET_WALLPAPER_OPACITY` = 0.4, and the number came off a screenshot.** A
`BEHIND_CONTENT` layer shows through wherever the interface leaves ground visible, and the largest
such area is the library's own filter strip. At full strength Sunset Wilds' orange-to-magenta ramp
put `ACCENT` text on an orange block and made the search box and every filter caption unreadable,
while the table below it - which has an opaque ground - was untouched. That reads as a rendering
fault rather than as a look, and no assertion anywhere would have caught it. A user who wants a wall
of colour still can: the customizer's slider goes to 1 in that band, and it is *their* mood at that
point rather than one the application shipped.

**Overlay layers above the content are the ones that read strongly**, which was not obvious in
advance and is worth knowing before designing the next preset. Three of the eight use them
(scanlines, LCD grid, vignette) and they are visible everywhere including over the album art, where
a wallpaper is only visible in the gaps. The 0.35 cap is what makes that safe, and it is applied in
`LayerStyle`'s constructor rather than in the customizer's slider - so a `.mood.json` a teammate
exported cannot carry a layer at full strength, which is exactly the file that arrives on the day of
a defence.

**Reactivity modulates the palette and deliberately not the stylesheet.** The canvases read
`Palette.active()` on every repaint, so installing a modulated palette reaches the road, the meters,
the tree and the overlays for free; the controls read a generated stylesheet, and regenerating that
means a full CSS pass over the whole scene graph three times a second on a machine that also has to
run a game. It is also the right answer aesthetically - a table whose headings pulse with the music
is a fault rather than a mood.

- **The clamp is computed once per mood, by measurement.** `MoodReactivity.safeLift` searches
  downwards from the ceiling for the largest lift the validator still passes, so "clamp so
  thresholds hold at every point in the modulation" is enforced rather than asserted. Lifting
  `HIGHLIGHT` moves it through the colour space, and in a palette whose `OUTLINE` happens to be
  bright that movement is *towards* the role it has to stay away from. A palette with no headroom
  gets a lift of zero, which is the honest answer.
- **The 3 Hz cap lives in `MoodReactivity.update`,** which takes the clock every frame and decides
  for itself whether to accept a reading. A caller that did its own rate limiting would be a second
  place the cap lived, and a 200 BPM track is 3.3 beats a second - it would breach the cap on its own.

**"Reduce motion" reaches `RunnerView`, which §8b insisted on and is the part that would have been
forgotten.** One flag, applied at the two places every beat effect is derived from - `beatPulse` and
`eventWash` - so the zoom, the dip-and-lift wash, the combo surge, the horizon flash and the pickup
and bump flashes all stop together and none of them can be missed. The road, the entities and the
timing are untouched: the game plays identically. The smoke test drives the switch on a moving mood
and checks that the frame loop actually stops and restarts, because a switch that set a flag nobody
read would look exactly like this one.

**The pixel editor's tiles are indices, and `SKY_GARDEN`'s clouds are the proof.** They are a
hand-drawn 16x16 tile stored in the built-in mood as sixteen rows of hex digits, referred to by an
`ImageLayer` by name, and rendered through whatever palette is in force - so recolouring the mood
recolours the clouds. That is exactly what "Save to layer" produces, which is the point: what ships
is what the editor makes.

- **An `ImageLayer` resolves a tile before a file**, which is what gives a tile that property and a
  PNG none of it.
- **The editing surface is 256px**, which is 16x for a 16x16 tile - the spec's own default zoom, and
  a whole number at the other two sizes. It was 320 first, and that was measured to be wrong rather
  than merely large: the tiled 3x3 preview fell below the fold, and a tiled preview nobody can see is
  the one panel here that cannot afford to be missed.
- **`ImageQuantizer`'s dither is applied between the two nearest candidates, not as a nudge to the
  colour**, and the first version got that wrong: nudging lightness before matching does nothing at
  all on an arbitrary sixteen-colour palette, because the nearest-neighbour decision is dominated by
  chroma. It was a switch that changed no pixels, which is the worst kind - it looks like a feature.
  The formulation that replaced it has no tuning constant in it at all.

**Editing a preset duplicates it first, automatically, and says so.** "Never edit a built-in in
place" is enforced in `MoodRepository.save`, which refuses outright - but making the user press
Duplicate before they may move a colour is a rule they would resent and work around by editing the
file by hand. So the customizer duplicates on the first edit and puts a line in the status bar. The
original is code, so it cannot be corrupted at all.

**The presets are Java rather than resources, which §8b did not ask for.** Copying built-ins out on
first run buys symmetry and costs two things worth more: a user could corrupt a preset, and the
presets would be parsed at runtime and hoped for rather than checked by `MoodsTest` at build time.

**A mood folder name comes off a text field, so `MoodRepository.slug` is the only thing between a
user and `Path.resolve`.** Anything that is not a letter, a digit or a dash becomes a dash, which
rules out separators, traversal and every reserved character at once; `ImageLayer` separately refuses
a file name containing `..` or a separator, because a mood folder is unzipped from something a
teammate sent. Both are tested with the payloads rather than with well-formed names.

**Dropping a `.gpl` or `.hex` anywhere on the window makes a mood from it**, deliberately not
restricted to the mood screen: somebody who has just downloaded a palette is looking at their
downloads folder, not at this application's side rail. It goes through the customizer's own import
path rather than through the repository, so there is one sequence rather than two that can drift.

**`LayeringTest` now enforces the mood package's own half of ground rule 3.** `mood/` may import
`javafx.scene.paint` and `javafx.scene.image` and nothing else - no `Node`, control, layout, canvas,
animation or stage. The image allowance is a deliberate widening of §8b's "only Color / Paint": a
`WritableImage` is a buffer rather than a node, and both uses of one - rendering a tile's indices
through a palette, and quantising an import onto sixteen colours - are pixel arithmetic that would
otherwise drag the tile format into `ui/`. What stays out is what the rule is actually about.

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
| M11 | ⭐ **Mood system** — 16-color GBA palettes, gradient/image/procedural overlay layers, live customizer, 16×16 / 32×32 pixel editor, `.gpl` + `.hex` import, `MoodValidator`, presets | ✅ done — **ten moods**, nine of which cost nothing per frame |
| M10 | *Optional:* `go-librespot` child process, Spotify search, streamed songs in the library | ✅ built; search runs on the user's own Spotify application and needs only a released daemon. Streamed tracks generate courses, built from the playback tap on first play — verified against the file analyser on real music, byte for byte. **Playback and pause/resume confirmed working on a live Premium account (2026-08-15)**, once the pipe deadlock was fixed |

**M10 was built before M11 at the user's request**, against this file's own advice. The advice
still stands for anyone reading it cold: M11 is visible in the first two seconds of the demo and
depends on nothing external, and if only one of the two gets finished it should be that one. M10 is
**strictly additive** and stayed that way — nothing in M0–M9 imports `spotify/`, the three graded
structures are untouched, and the application runs identically with the daemon absent.

**M11 is listed before M10 on purpose — build it first.** It is visible in the first two seconds
of the demo, depends on nothing external, and cannot fail in a way that breaks the app. M10
depends on a third-party binary, an OAuth flow and a network. If only one of the two gets built,
build M11. Its prerequisite is *already paid for* if ground rule 7 was respected: with every
color resolved by role, M11 is new code rather than a refactor — so clear the hex-literal debt
recorded in ground rule 7 before starting it.

**Both are built now, and that prediction was exact.** M11 added twenty classes and changed one
line of CSS; it did not touch a single `gc.setFill`, because every one of them already named a
role. The debt cleared in M9 is what made that true.

### As built (2026-08-17) — the sweep after M11

Six changes, none of them a milestone and all of them things the application was visibly missing. 1060
tests, up from 1017. Each has its own section above; in one line each:

| Change | Where |
|---|---|
| **True fullscreen for the runner** (`F11`, `Esc` leaves) | §"Keyboard shortcuts" |
| **The cartridge vanishes into the machine** — it used to leave a sliver under the loading bar | §"the boot screen" |
| **A PS1-shaped fifteen-second start-up sequence**, as long as the fanfare, with the title fading in | §"the boot screen" |
| **`psx.mp3` as the boot fanfare**, on a line of its own | §"the boot screen" |
| **The boot screen is black and white**, through `Palette.hardware()` | §"the boot screen" |
| **A shutdown screen**, so quitting no longer looks like a hang | §"the shutdown screen" |
| **Add from Spotify out of the library**, with album, genre and a rating | §M10, "As built (2026-08-17)" |

**Then two more the same day, both asked for directly.** 1068 tests, up from 1059 as measured (this
section said 1060, which was a count off by one).

| Change | Where |
|---|---|
| **The application launches into true fullscreen**, with a `< >` title-bar button and `Esc` to leave — and `F7 MINI` takes it out first | §"There are two fullscreens and they are not the same thing" |
| **The start-up title cycles a rainbow and settles to white** as the fanfare dies, through `Palette.bootRainbow()` | §"the boot screen" |

**The second is the one worth reading the reasoning on**, because it looks like the monochrome rule
being abandoned and is not: the boot screen stays on `Palette.hardware()` for everything, and the title
gets a *second* palette rather than a handful of literals — the same arrangement `hardware()` itself
established. A machine running a colour test across its own name is a different statement from a
machine wearing somebody's mood.

**Two of them are findings rather than features, and both are ground rule 6 again.** `Stage.setFullScreen`
enters a nested event loop and deadlocks a synchronous smoke test; and Spotify has quietly stopped sending
`genres` on the artist object while still documenting it, which is why the add dialog's genre comes from
the library's own knowledge of the artist instead.

**Then four more, all asked for directly, all about the two bracket screens.** 1085 tests, up from 1068.

| Change | Where |
|---|---|
| **The cartridge is killed the instant it seats** — it was still being drawn, torn, through the glitch | §"the boot screen" |
| **Scanlines, a vignette and a sync roll on the boot and shutdown screens only** (`ui/CrtEffect`) | §"the glass on the two bracket screens" |
| **`Cartridge_In.mp3` at the moment it seats**, on a second line beside the fanfare | §"the boot screen" |
| **The cartridge is ejected on shutdown**, with `Cartridge_Out.mp3` under it | §"the shutdown screen" |

**The finding in this batch is that the obvious way to write the glitch draws nothing at all.** With the
artwork gone there is nothing left to tear, so the natural move is to blit `CrtEffect`'s own mask back a
band at a time at an offset — and the mask darkens towards `SHADOW`, which on that screen *is* the ground.
Black torn over black is a no-op: the screenshot came out byte-identical to the one before the change,
with code that reads perfectly. It lights the raster instead. That is the third time this project has
been caught by an effect that looks like a feature and changes no pixels — see also the mood importer's
dither and the meters' colour ramp — and the tell is the same every time: **compare the picture, not the
code.**

**Then two more, both asked for directly, both about the same two screens.** 1096 tests, up from 1085.

| Change | Where |
|---|---|
| **The clunk plays the moment the cartridge goes in**, on the release rather than two tenths of a second later on the tear | §"the boot screen" |
| **The glass became a curved tube** — rounded corners, a bowed raster, a lit rim and a sheen | §"the glass on the two bracket screens" |

**The finding in this batch is that one warp cannot be both the raster and the silhouette.** Reading the
tube's outline off the same barrel curve that bows the scanlines gives a region touching the window at
exactly four points and falling away from all four — **a lens, not a television**, with the sides bowed in
over their whole height and the corners closed to points. The code is correct, every number in it is what
it says it is, and the shape is simply wrong; the only thing that reported it was looking at the
screenshot. Two knobs now: `CURVATURE` for the raster, `CORNER_SHARE` for the outline.

**And the smoke check written for the clunk immediately found the drag it was checking had been dead.**
The full-threshold `fireDrag` sat after the glitch and show previews, which put the screen into a phase
that correctly refuses a gesture — so the press, the drag and the release were all dropped and the line
beside it read as a passing check. **A check placed after something that changes state is not a check.**

**Then three more, all asked for directly, all about the two bracket screens.** 1101 tests, up from 1096.

| Change | Where |
|---|---|
| **The screen glitches as the cartridge is pulled out** — the insert's own tear, run at the other end | §"the shutdown screen" |
| **The eject starts the cartridge halfway out**, where the boot screen left it, with the name handing over to its label | §"the shutdown screen" |
| **The application fades in at launch** instead of appearing already drawn | §"the boot screen" |

**The finding in this batch is not about any of them.** The launch fade was measured against an
intermittent freeze in the fullscreen launch and looked, across four separate implementations, like the
cause of it. It was not: a control run at the end of the session wedged six times out of six on
untouched code, because the machine had been degrading under the experiment all along. The freeze is
real, pre-existing and unexplained; the bisection that "found" it was measuring drift. See §11 — and
§7, where this project learned the identical lesson about an animated wallpaper.

**Then one more, asked for directly: the other end of that fade.** 1102 tests as measured, up from
1100 — the two new ones are `SoundEffectTest`'s.

| Change | Where |
|---|---|
| **The application fades up out of the boot screen's black, and the fanfare fades with it** — one gesture, one length, whether the sequence ran out or was skipped | §"the handover" |

**The finding in this batch is that the audio fade had a length it could not survive being given.**
`SoundEffect` wrote its faded tail and then `flush`ed the line, on the reasoning — written down, and
correct at the time — that what was left buffered had already been faded to nothing. That holds only
while the fade is shorter than the line's own buffer. `stop(double)` made a 650 ms fade possible and
the same line of code silently became a cut part way down the ramp, which is the click `FADE_SECONDS`
exists to prevent, arriving by the mechanism that constant's own comment describes. It drains now.
**A constant that is safe at its default is not the same as a constant that is safe**, and the tell
was that nothing about the old code changed or looked wrong — only what could now be passed to it.

**Then one more, reported rather than asked for: the launch sometimes came up windowed.** 1102 tests,
unchanged — this one is a platform race and there is nothing in it a headless test can reach.

| Change | Where |
|---|---|
| **The launch asks for the display 400 ms in rather than one pulse in, asks once more if refused, and believes the window rather than itself** | §"There are two fullscreens and they are not the same thing" |

**The finding is that `Stage.setFullScreen` is a request, not a setter.** macOS discards it outright when
the application is already mid-transition — and the launch makes it during two of them, the maximise zoom
and, on a quick relaunch, the previous instance's fullscreen Space being torn down. The symptom was
reported as *"if I wait it boots up correctly"*, which is the entire diagnosis in one sentence. What made
it worse than a miss is that `setWindowFullscreen` stripped the frame and flipped the button's caption
*before* asking and never read the answer back, so a refusal left a maximised, borderless window claiming
to be fullscreen. **A boolean that is only ever written is not state, it is a hope** — and this one had
been write-only since the mode was built.

**Stop after each milestone and report exactly how to test it before continuing.**

---

## 10. Pending experiments — carry forward across sessions

### EXP-1 — Does the go-librespot FIFO pace to realtime?
**Status: ANSWERED (2026-08-13), from the source rather than the stopwatch. It does not pace.**

`output/driver-pipe.go` settles it without needing an account, a network or a track. The output
loop has **no sleep, no timer and no rate limiter**: it reads decoded float32 from the session,
applies volume, transforms to `s16le` and calls `file.Write`, forever. The *only* thing that can
block it is that write. So a FIFO reader sets the pace, and the pipe's own kernel buffer — tens of
kilobytes, a fraction of a second — is the entire slack.

Two consequences, and the first is why nothing had to be built for it:

- **The model-independent path is the one that was built, and it is correct either way.**
  `SpotifyAudioSource` writes to a `SourceDataLine`, which blocks when the card's buffer is full.
  That backpressure propagates down the pipe and stops the daemon decoding. The sound card is the
  clock, exactly as it is for a local file, and no ring buffer or countdown is involved.
- **The daemon's own idea of the position is therefore wrong**, and must never be read. It assumes
  its output consumes audio at the speed it is heard; a pipe does not. `/status` runs ahead by
  however far the pipe is buffered. The clock is `SourceDataLine.getLongFramePosition()`, as
  documented in §6, and `SpotifyAudioSource.position()` says so.

**The ring-buffer-ahead model in the table below was not built**, and the reason is worth keeping:
it is *possible* — draining the pipe faster than realtime into a buffer would work, since nothing
upstream is pacing — but how far ahead it could actually get is bounded by Spotify's CDN and the
Vorbis decode, which is a network measurement rather than a design fact. Analysing from the tap as
the track plays and caching at the end gives a full course on the second play, and that is the
shipped behaviour — **as of 2026-08-15, and not before.** This paragraph previously said so while
the code did nothing of the kind: the whole beatmap pipeline was keyed on `java.nio.file.Path`, a
streamed song's is `null`, and every Spotify track therefore reached the runner as `NO COURSE`
forever. See "Streamed tracks generate courses" below. **"Needs no new machinery" was the wrong
part** — it needed a second analyser, a locator-keyed cache and two new engine callbacks.

| Elapsed | Meaning | Model to build |
|---|---|---|
| ~10 s | Realtime-paced | Analyze on first play; course unlocks on replay |
| **~1–2 s** | **Decodes ahead — this is the answer** | Ring-buffer inside the track, play ~10 s behind the read head → full game on first play behind a `READY… SET…` countdown |
| Blocks forever | Misconfigured | Confirm backend is `pipe` and a track is playing |

The original experiment still works and is worth running once against a real account, if only to
put a number on how many times realtime the decode actually manages:

```bash
mkfifo /tmp/sdmk-pcm
# configure go-librespot: audio_backend: pipe, audio_output_pipe: /tmp/sdmk-pcm,
# audio_output_pipe_format: s16le — then start the daemon and play any track
time dd if=/tmp/sdmk-pcm of=/dev/null bs=176400 count=10
```

### As built (2026-08-13, M10)

**Spotify is a second `AudioSource` and a second kind of `Song`, and that is the whole of it.** The
three graded structures, the playback modes, the analyser, the runner and the visualiser were not
touched: they navigate `Song` objects and are written against `AudioSource`, and neither has ever
had an opinion about where bytes come from. `LayeringTest` now enforces that rather than trusting
it — it fails the build if anything in `ds/`, `model/`, `playback/`, `analysis/`, `game/`, `mood/`,
`assets/` or `persistence/` so much as imports `spotify/`.

- **`AudioSource.load` takes a `String` locator now, not a `Path`.** A streamed song has no file, so
  `Song.locator()` answers with a path for one kind and a `spotify:track:...` URI for the other, and
  `PlaybackEngine` passes it straight through. `RoutingAudioSource` is the only class in the
  application that reads the difference. The alternative — teaching the engine to branch, or giving
  `audio/` a dependency on `model/` — would have put the knowledge in several places instead of one.
  `load(Path)` survives as a default that delegates, because plenty of callers genuinely do have a
  path.
- **`Song.getFilePath()` can return `null` now, and the compiler will not tell you.** That was the
  whole risk of this milestone: it compiled clean on the first try and had three live
  `NullPointerException`s in it — the library's details panel, the beatmap index request, and the
  edit dialog. The beatmap paths turned out to be null-safe already (`isReady`, `recheck` and
  `request` all take `null` and mean "nothing"), which is luck rather than design. Go through
  `locator()`, or check `isSpotify()`.
- **Search needs no OAuth application of its own — but it needs a daemon newer than any release.**
  The daemon exposes `POST /token` and proxies `GET|POST|PUT|DELETE /web-api/{path}` verbatim to
  `api.spotify.com` with the session's bearer token attached, so `v1/search`, `v1/me/tracks` and
  `v1/me/playlists` work with no client id, no redirect URI and no secret in this repository. The
  interactive login already requests `user-library-read`, `user-read-private` and twenty-odd more
  scopes (`session/session.go`), so nothing had to be added for the search to be allowed.

  **Both endpoints are `master`-only and appear in no tagged release.** Verified against v0.8.0's
  own `api-spec.yml`, which contains *zero* occurrences of `token` or `web-api`; v0.8.0 is what
  Homebrew installs and what every GitHub release carries. So on a stock install today, **playback
  works and search does not** — every `/player/*` endpoint has been there for releases.

  **This was a ground-rule-6 failure and it is worth naming.** The API was read off `master` rather
  than off the version that actually resolves, which is exactly the mistake that rule exists to
  prevent, and it was caught by running the real binary rather than by any amount of re-reading.
  `SpotifyApi.hasWebApiProxy()` now probes for the endpoint once per connection, and the view says
  so plainly instead of showing a search box that returns nothing however it is used — a 404 and
  "Spotify matched nothing" are the same empty list to every caller above, and the silent version
  reads as a bug in this application rather than a missing endpoint in the daemon.
- **There is no `--version` flag.** `go-librespot --help` lists exactly two options, `--conf` and
  `--config_dir`; anything else exits with `error="unknown flag: --version"`. The first version of
  `SpotifyBinary.version()` ran `--version` and returned *that error message* as the version, which
  is worse than not knowing because it would have been shown to the user as the answer. The version
  is read from the daemon's own first log line, `running go-librespot 0.8.0`.
- **The daemon does not answer any HTTP request until it has a session.** Measured: with no login,
  `GET /`, `GET /status` and `POST /token` all accept the connection and then never reply, while the
  `/events` websocket upgrades immediately. So `awaitReady()` is polling something that hangs rather
  than something that answers "not yet", and its timeout has to span a human completing an OAuth
  flow in a browser.

  **A hung endpoint does not prove there is no session, and reading it that way cost a whole
  investigation.** A wedged *pipe* produces byte-for-byte the same symptom — see "the pipe must
  never be left unread" below. The two are told apart in seconds: a daemon with a session holds an
  established TCP connection to Spotify's access point on **port 4070** (`lsof -nP -p <pid> -i`),
  and a wedged pipe shows the daemon's write offset and the app's read offset frozen a single block
  apart (`lsof -nP -p <pid> | grep pcm.fifo`). Check both before concluding anything about a login.
- **go-librespot publishes Linux binaries only, and always has.** Every release from v0.5.2 to
  v0.8.0 carries exactly four assets — `linux_x86_64`, `linux_arm64`, `linux_armv6`,
  `linux_armv6_rpi` — and there has never been a darwin one, despite the source carrying a macOS
  AudioToolbox backend. So "fetch the binary at startup" is real on Linux and impossible on the
  machine this was written on. macOS gets the Homebrew formula (bottled, `arm64_tahoe`), offered as
  a button with the command printed beside it rather than run unasked: installing four packages on
  somebody's machine is not a startup task. Downloads go through
  `releases/latest/download/<asset>`, which GitHub redirects to the newest release — no API call to
  be rate limited, and no version number written down here to go stale.
- **The pipe is opened at *both* ends and held open, and this is the detail the whole audio path
  rests on.** A FIFO's reader reports end of file the moment the last writer leaves, so a reader
  holding only the read end would see EOF at every track boundary and could not tell that from the
  daemon dying. Holding our own write end — and never writing a byte to it — keeps the pipe alive
  across every gap. It also fixes the other half: go-librespot opens `O_WRONLY|O_NONBLOCK` and
  **errors outright if no reader is present**, so a reader that came and went would produce a
  failure inside the daemon at the exact moment the user pressed play. Measured before it was
  written: with our writer held, a read after the daemon closes its end blocks rather than
  returning `-1`.
- **The end of a track cannot be detected from the pipe**, and this is not a detail that can be
  worked around. When a track finishes the daemon's pipe output goes *quiet* — it does not close
  the pipe — and a quiet pipe is indistinguishable from a quiet passage. So end-of-track arrives
  out of band, from the `/events` websocket, as `stopped` or `not_playing`. Without that socket the
  running order stops dead after one song, which is precisely the failure `PlaybackEngine` was
  already built to avoid for local files.
- **The `stopped` event is a flag for the pump, not an instruction to stop.** The daemon is ahead of
  the speakers by whatever the pipe holds, so acting on that event where it arrives would cut the
  end off every streamed track — up to a pipe buffer plus a card buffer, getting on for half a
  second, and plainly audible. `trackEnded()` sets `finishing` and returns; the pump plays the pipe
  out, drains the line and only then announces. It also keeps every read of the pipe on one thread,
  so the event socket can never race the pump. The flag is cleared by `retirePump`, or a pending
  end would fire on the next track the instant its pipe was briefly empty — which is immediately.
- **`WebSocket.Listener.onText` must call `request(1)` itself.** Overriding it takes over the demand
  management, and without that call the socket delivers exactly one message and then goes silent
  forever — which looks identical to a daemon that stopped sending. Text frames are also fragmented,
  so they are accumulated until `last`; a JSON parse of a fragment fails and the event is lost.
- **A pipe holds stale audio and a file does not.** After a seek or a track change the pipe still
  contains up to a bufferful of the previous moment. Left there it puts a fraction of a second of
  the wrong music at the front of every track, which sounds like a glitch rather than like a bug.
  `drainPipe()` throws it away, and the ordering matters: after the daemon has been told to move,
  before the pump is let go.
- **Four configuration values decide whether this application or Spotify owns the running order,
  and every one of them fails silently.** `disable_autoplay: true`, `zeroconf_enabled: false`,
  `crossfade_duration: 0` and `audio_backend: pipe`. Get one wrong and playback carries on sounding
  completely normal while the circular list, the queue and the tree stop being consulted — no
  exception, no log line, nothing in a screenshot. `SpotifyConfigTest` asserts them one at a time
  and the smoke test prints them, because a test is the only thing that would ever notice.
- **`external_volume: true` is the fifth, and it is subtler than the four.** Left false, the pipe
  driver multiplies every sample by the *square* of its own volume setting before writing — so the
  meters would follow a slider rather than the music, and the generated courses with them. Volume
  belongs to the `SourceDataLine`, in one place, exactly as it does for a local file.
- **Normalisation is deliberately left on**, for the reason the M10 notes already gave: Spotify's
  −14 LUFS target is what keeps the meter range, and therefore the difficulty of a generated
  course, consistent between tracks.
- **Taps have to be replayed onto a source built later.** The Spotify source is constructed the
  first time a streamed song is loaded, which is long after the meters and the beat analyser
  registered themselves at startup. `RoutingAudioSource` keeps its own tap list and seeds the new
  source from it — without which every streamed song plays with dead meters and no analysis, and
  nothing anywhere says so. This was written wrong first and caught by
  `RoutingAudioSourceTest.tapsReachASourceBuiltLater`.
- **Nothing starts by itself.** Constructing the session looks at the filesystem and does nothing
  else — no process, no socket, no network. The daemon, the event socket and the login all wait for
  a button. The one thing that does happen unasked is the background *download* into
  `~/.superdwarfkart/spotify/bin`, which the user asked for and which can be turned off in
  `settings.json`; it is skipped entirely during a smoke test, so the build never depends on the
  network.

  **Superseded again on 2026-08-16: the boot screen starts the daemon while its bar runs.** The
  cartridge going in is the start ritual, and a console reading a cartridge is the right cover for
  the one thing at startup that genuinely takes a moment. It also answers what the Spotify page kept
  raising — connecting was a step whose necessity nothing on screen explained.
  - **The boot never waits for it.** The bar runs its own length and hands over regardless; a daemon
    that is slow, absent or sitting on a login must not be able to hold the application shut (ground
    rule 5). The caption reports how far it got by the time the machine finished reading, and the
    Spotify page carries on from there.
  - **The caption is the only line on that screen that changes**, so it is drawn in `ACCENT` rather
    than `TEXT_DIM` — a reader has about a second and a half to notice it.
  - **`previewAt` runs none of the sequence's side effects**, deliberately: a screenshot must not
    start a daemon. So the smoke test sets the caption by hand before photographing
    `boot-loading`, or the one shot of that phase would show the line blank and prove nothing.
  - Suppressed entirely under `sdmk.smokeTest`, which prints `SKIPPING GO-LIBRESPOT` on the screen
    and a suppression line in the log rather than quietly doing nothing.

  **Superseded in one place as of 2026-08-15: pressing play on a streamed song now connects.** The
  rule stands for everything else — the daemon still waits for a button *unless the user has asked
  for the one thing that requires it*, which is a clearer statement of intent than pressing CONNECT
  and is a step whose necessity nothing on screen explains. Before this, playing a Spotify track
  with the daemon down **did nothing at all and said nothing anywhere**: `PlaybackEngine` has always
  had `setOnFailure` and `App` had never registered one.
  - `App.songWouldNotOpen` splits on the only question that matters. **Installed → connect and carry
    on**, holding the song in `awaitingSpotify` and starting it when the session reaches
    `CONNECTED`. **Not installed → say so**, once per session, with the install command for this
    platform: no amount of waiting fixes that, and the search that put the track in the library gave
    no hint that playing it needs anything else.
  - **The waiting song is re-checked against `player.current()` before it starts.** Connecting can
    take as long as a person takes to finish a login in a browser, and starting a track the user has
    since navigated away from is worse than doing nothing.
  - **Suppressed entirely under `sdmk.smokeTest`**, which must never launch a subprocess, take a port
    or wait on a login — and the smoke line says so out loud rather than being silently skipped.
  - **`SpotifySession` took a list of change listeners for this.** The Spotify page claims a slot in
    its own constructor and playback registers later; with the old single-handler `setOnChanged` the
    second silently displaced the first, and the symptom would have been a page that stopped
    redrawing — which reads as a frozen interface rather than as a lost listener. `addOnChanged` is
    the one to use after startup, one listener throwing cannot take down the rest, and
    `listenersAccumulate` pins it.
- **The child is killed on every exit path** — `stop()` on the ordinary one and a shutdown hook for
  the rest, `destroyForcibly()` after a grace period. An orphaned daemon holds a Spotify session and
  keeps port 3678 bound, so the next launch finds the port taken and Spotify silently does not work
  with nothing on screen to say why.
- **logrus quotes its message, and `\S+` eats the closing quote.** The daemon logs
  `msg="to complete authentication visit the following link: https://...user-top-read"`, so a
  pattern that runs to the next whitespace takes that final `"` as part of the URL. The link is then
  correct for 766 characters and wrong at the 767th, `URI.create` throws
  `Illegal character in query at index 766`, and **the browser never opens** - while everything else
  looks perfect: the link is found, the state moves to "waiting for login", and the button just does
  nothing. The pattern excludes quotes.

  **The test that should have caught it was the thing that hid it.** The stub wrote the line with
  `echo "..."`, and the shell strips those quotes - so the line under test was not the line the
  daemon prints, and the bug passed. It now uses `echo '...'` so the quotes survive, and asserts
  that the extracted link both lacks a trailing quote and survives `URI.create`. Verified by
  reverting the pattern and watching the test fail.

  **And the live check called it verified.** It printed `auth link: DETECTED (767 chars)`, which was
  read as "the whole link came through" when 767 was the length *with the quote on the end*. A
  measurement that cannot fail is not a check - the number needed something to be right *against*,
  which is why it now parses the URL rather than counting it.
- **`go install .../cmd/daemon@master` produces a binary called `daemon`, not `go-librespot`.** Go
  names a main package's executable after its directory. Printed as a suggestion on the page it was
  advice that could not work: the build would succeed and put a perfectly good daemon somewhere
  `resolve()` never looks, because that searches for the name `go-librespot`. The build button sets
  `GOBIN` to `~/.superdwarfkart/spotify/bin` so the output lands in the folder resolution already
  searches - no `go env` to parse - and then renames it. `SpotifyBinaryTest` pins both names and
  asserts they differ, so the rename cannot quietly become dead code.
- **The build button checks every prerequisite up front, because the build's own failure is
  useless.** A source build needs Go 1.25+, `pkg-config`, and `ogg`/`vorbis`/`flac`/`mpg123` (plus
  `libasound2` on Linux). With pkg-config absent, `go install` compiles for a while and then prints
  `github.com/devgianlu/go-librespot/mp3: exec: "pkg-config": executable file not found in $PATH`
  once per cgo package - three near-identical lines naming what *Go* could not run rather than what
  the user has to install.

  **And fixing that one leads straight into the next.** On macOS `ogg`, `vorbis` and `flac` arrive
  as Homebrew dependencies of go-librespot itself, but **`mpg123` does not** - so a second attempt
  fails too, on a different line, after another wait. `checkBuildPrerequisites()` therefore asks
  about all of them at once and produces a single command
  (`brew install pkg-config mpg123` on the machine this was written on). Libraries are checked with
  `pkg-config --exists`, which is the same question cgo asks; when pkg-config itself is missing it
  falls back to looking for the `.pc` file across the standard directories, so the *first* answer
  still names everything rather than only the tool that would have found the rest.

  **A pkg-config module name is not a package name, and for mpg123 they differ.** The check first
  asked for a module called `mpg123` and reported it missing on a machine where it was installed and
  working - because the module is `libmpg123` and `mpg123` is only the Homebrew *formula*. Every
  other library here happens to spell both the same, which is exactly why the mistake looked right.
  The authority is go-librespot's own cgo directive (`// #cgo pkg-config: libmpg123` in
  `mp3/decoder.go`), since that is literally the question the build asks; `SpotifyBinaryTest` pins
  it, along with the module-to-formula mapping.

  On Linux the equivalent needs root. The command is shown to be copied and is never run: this
  application does not ask for a password.
- **The lockfile is an advisory `flock`, not a stale PID file** — `cli_config.go` calls
  `flock.New(configDir/lockfile).TryLock()`. The kernel drops the lock when the process dies, even
  under `SIGKILL`, so the leftover empty file is harmless and heals itself. **Do not "clean it up"
  on startup**: deleting it would defeat the only thing stopping two daemons sharing one config
  directory. A `go-librespot is already running (lockfile: ...)` message means a daemon genuinely is
  running, which during development is usually one this application orphaned.
- **The Spotify view added no CSS**, so it added no hex literal (ground rule 7). It reuses the
  classes the history and settings pages already define and therefore follows the palette for free.

**What has actually been verified, and what has not.** Against the real go-librespot 0.8.0 from
Homebrew: the binary is found on `PATH`, the generated `config.yml` is accepted without complaint,
the API server binds to 127.0.0.1:3678, the authorisation link is printed in the exact wording
`SpotifyDaemon.AUTH_LINK` matches and is extracted at its true length of 766 characters and parses
through `URI.create`, the `/events` websocket connects, and the child process is killed with no
orphan left behind.
**Superseded on 2026-08-15: audio through the pipe has now been heard on a live Premium account and
works.** This paragraph previously read "everything past the login is unverified — no Spotify
Premium account was available", and for the whole of M10 that was true. What unblocked it was not a
missing feature but the pipe deadlock recorded below: the reader parked whenever playback stopped,
which wedged go-librespot's entire HTTP API, so `/player/resume` timed out and nothing was ever
audible. With the reader draining in every state, **streamed playback and pause/resume are confirmed
working against a real account**.

The rest of the path is still unverified by ear rather than by reasoning: the seek path, the
drain-on-track-end and the running order advancing from a `stopped` event are unit-tested and
argued for but have not been separately exercised on a live account. Do not promote them to
"verified" without hearing them.

### As built (2026-08-15) — search moved off the daemon, and the source build stopped being needed

**Search runs as a Spotify application of the user's own now, and playback still goes through
go-librespot.** The two halves of this feature reach Spotify by completely separate routes, which is
what the previous arrangement obscured: `spotify/SpotifyCatalog` calls `api.spotify.com` directly
over the Client Credentials flow, and `/player/*` goes on driving the daemon over librespot's own
session protocol. Only the search half was ever rate-limited, and playback was never affected by
any of it.

- **librespot's quota is not ours and never was, and this is the measurement that settles it.**
  `client_id.go:5` hardcodes Spotify's own desktop client id,
  `65b708073fc0480ea92a077233ca87bd` — as a **byte array, not a hex string**, which is why grepping
  the binary for the hex finds nothing and why this took a source clone to establish. Every
  librespot and go-librespot instance in the world authenticates as that one application, and
  Spotify rate-limits per client id.

  Measured against the live service, a day after the account had been touched at all: the first
  call answered `retry-after: 31`, and a single call after **forty seconds of complete idleness**
  answered `retry-after: 33`. A rolling window nobody is spending drains to zero; this one went
  **up**. That is a bucket contended by strangers, and no amount of waiting empties it —
  which is exactly the advice that had been given and was wrong.
- **The daemon's proxy strips the header that would have explained any of this.** A 429 through
  `/web-api` carries `Vary: Origin`, `Date` and `Content-Length: 0` and nothing else; the identical
  call made directly carries `retry-after: 31` and a JSON body naming the problem. So reading
  `Retry-After` was worthless while search went through the proxy and is worth having now that it
  does not — `SpotifyCatalog.retryAfterSeconds()` is what lets the interface say *how long*.
- **This removed the master-only dependency entirely, which is the part worth the most.** `/token`
  and `/web-api/{path}` are both `master`-only — the ground-rule-6 failure recorded above — and
  catalogue search uses **neither**. Verified against v0.8.0's own `api-spec.yml`: it declares
  **17 endpoints, every `/player/*` route this application calls among them, and zero occurrences
  of `token` or `web-api`**. So a stock `brew install go-librespot` is now enough, and the Go
  toolchain, `pkg-config`, `mpg123`, the `libmpg123`-versus-`mpg123` module-name trap,
  `buildFromSource()` and `checkBuildPrerequisites()` all came off the critical path. They are kept,
  because they are still the only route to the *user's own* library — see below — but nothing
  ordinary needs them.
- **The Client Credentials flow deliberately cannot see the user, and that is a real boundary rather
  than a limitation to work around.** An application token identifies the application; `v1/me/tracks`
  and `v1/me/playlists` answer 401 through it however the request is shaped. Saved tracks and
  playlists stay on the daemon's proxy, and `SpotifyView` **only draws those two buttons when the
  proxy exists** — a button that cannot work is worse than no button.
- **The request shape was verified against the real endpoint before trusting it**, with deliberately
  fake credentials so it cost no quota: `POST accounts.spotify.com/api/token` with the pair as HTTP
  Basic and `grant_type=client_credentials` as a form body answers
  `400 {"error":"invalid_client","error_description":"Invalid client"}`. Spotify parsed the grant and
  rejected only the credentials, which is the answer that proves the shape. Putting the id and secret
  in the *body* is the arrangement people reach for first and it is refused with a different error.
- **The token is cached for its full hour, and dropped in the two cases where keeping it is wrong.**
  Minting one per search would spend the very allowance this class exists to protect. It is discarded
  on a 401 — otherwise a token revoked early leaves the feature dead until the application restarts —
  and on a credential change, or a token minted for the old application would keep working until it
  expired, so a corrected secret would appear to change nothing and a wrong one would appear to work.
- **Credentials are verified when saved, not merely stored.** Stored blindly, a typo in the secret
  shows up as an empty search result — which sends the user looking for a better search term for a
  problem that is in a text field on the same page.
- **A refused attempt rolls back to whatever was working, and getting this wrong looked exactly like
  a broken feature.** The first version replaced the pair and then *cleared* it on refusal. But
  search comes up **already configured** from `settings.json`, so the button is only ever pressed
  against a working configuration — and one press with a typo in the field turned a working search
  **off**, while the message on screen talked about the credentials being wrong rather than about
  the setting it had just destroyed. Reported as "it still says the client or the secret are wrong"
  after the credentials had been proven good three separate ways, which is precisely how it reads
  from the other side. `SpotifyCatalog.applyCredentials` keeps the previous pair and says so;
  `aRefusedAttemptDoesNotDestroyWorkingCredentials` is the test.
- **The corollary is that nobody needs to press it.** `restoreCredentials` loads the stored pair at
  launch and search is on before the page is opened. The button is for *changing* the application,
  not for arming it.
- **The secret is stored in clear in `settings.json`, deliberately.** The file is already in the
  user's own home directory under their own account, the credential grants nothing but catalogue
  search against a free application they registered themselves, and there is no key store on this
  project's dependency list. Obscuring it would buy nothing and would imply a protection that was not
  there. What matters is that it goes nowhere else: not logged, not printed by the smoke test, not
  carried in the library or mood exports.
- **`v1/search` accepts a `limit` of ten, and its own documentation says fifty.** The reference
  states "Minimum: 1. Maximum: 50"; measured against the live service, **11 and above answer
  `400 {"error":{"status":400,"message":"Invalid limit"}}`** — 11, 12, 15, 18, 19, 20, 25, 30, 40 and
  50 were all refused, and 10 returned ten items. `SpotifyCatalog.MAX_SEARCH_LIMIT` is 10 and the
  clamp lives **in the client rather than at the call site**, so no caller can reintroduce it.

  **This is ground rule 6 with the documentation as the thing that could not be trusted, and it cost
  most of a session.** The interface asked for `PAGE_SIZE` = 50 and failed; every probe written to
  diagnose it asked for a handful and passed. So the class was correct everywhere it was tested and
  broken everywhere it was used, and the evidence kept saying the credentials were fine — which they
  were. Two changes exist so this shape of fault reports itself next time: **the smoke test now runs
  one real search with the same limit the interface uses** (a harness that asks for something
  different is not testing the application), and a refused *search* no longer borrows the *token*
  failure's message — "The credentials were accepted but Spotify refused the search itself (HTTP
  400)" is what finally located it, out of a user's screenshot.
- **A streamed song's cover is a URL, and for a while nothing drew it.** `coverUrl` was parsed,
  stored and persisted, and both views that show artwork read `getCoverPath()` alone — so every
  Spotify track sat on the "no artwork" placeholder, which is indistinguishable from a song that
  genuinely has none. `ui/CoverArt` is now the one place that knows about both kinds, and
  `LibraryView` and `MiniPlayerView` go through it.
  - **Remote artwork is background-loaded, never fetched on the interface thread.** `new Image(url)`
    will happily block until the network answers, and a slow server then looks like an application
    that has hung. The consequence is that the image has no dimensions when it is handed back, so
    the centre-crop has to be applied on completion — cropping against a zero width yields an empty
    viewport, which is a frame that stays blank forever with nothing logged.
  - **Decoded covers are cached, bounded at 64.** The details panel repopulates on every selection
    change, so without it holding an arrow key down re-fetches the same artwork once per row.
  - **The cover is chosen by width, not by position.** Spotify offers roughly 640, 300 and 64, and
    the first version took the *last* entry — right for the companion card at 124px and **four times
    too small for the library's ~250px panel**, where it arrived visibly blurred. `PREFERRED_COVER_WIDTH`
    is 300 and the choice is the smallest image that covers it. The array does arrive widest-first,
    but nothing promises that, and an ordering assumption fails by silently picking the wrong size.
- **`[smoke] covers` cannot wait for a background load, and the reason is a trap worth remembering.**
  JavaFX decodes off-thread but publishes progress *on the interface thread*, which the smoke test is
  holding — so polling `getProgress()` in a sleep loop guarantees it never advances. Measured: five
  seconds of waiting reported neither decoded nor failed. The check therefore decodes one
  synchronously to prove the artwork is real, and **selects a song with remote artwork so the
  screenshots taken afterwards show it** — by then the thread has been free for two seconds. The
  running application never has this problem.
- **There is one track parser.** A track object is shaped identically whichever route fetched it, so
  `SpotifyTrack.fromJson` / `listFrom` is now the single implementation and `SpotifyApi` delegates to
  it. Two copies of that method would have been free to drift in ways nothing would report.
- **Configuring credentials changes what the page shows without the session's state moving at all**,
  so `SpotifySession` grew a `changed()` notification separate from `set(State, String)`. A view
  listening only for state transitions would not have redrawn, and the search box would not have
  appeared until something unrelated happened to move the session.
- **Searching no longer needs a connection**, since it does not involve the daemon. Adding a result
  to the library while disconnected is a perfectly ordinary thing to do — it plays when the daemon is
  connected — and the panel says so rather than leaving the user to wonder.
- 660 tests, up from 637. `SpotifyCatalogTest` drives the whole thing against a stub Spotify over a
  real socket, for the reason `SpotifyApiTest` already does: what breaks here is the shape of the
  wire traffic, and a mock would agree with whatever the code did.

### As built (2026-08-17) — adding a Spotify track from the library, with its album, genre and a rating

**`ui/SpotifySearchDialog`, opened by a SPOTIFY button beside ADD in the library's own header.** Adding a
song is a library action, so it belongs where the library is. The Spotify page in the side rail is a
*connection* page — install, log in, register an application — and its search panel adds a track exactly
as Spotify describes it, which leaves the fields Spotify cannot describe blank forever. This is the same
search with the missing half attached: pick a track, check what came back, choose a genre, set a rating,
add it once.

- **`LibraryView` still knows nothing about Spotify.** It takes a `setOnSpotifySearch(Runnable)` and
  `App` wires it, matching `setOnSongActivated` — so the two-argument constructor the tests use keeps
  working and no view holds a connection to a streaming service.
- **Album, year and artwork were always parsed** (`SpotifyTrack.fromJson`); what they were not was
  *shown*. A field that is populated and invisible is indistinguishable from one that is empty, which is
  most of why "bring the album correctly" was worth asking for.

**Spotify has stopped answering the genre question, and this is a ground-rule-6 finding measured against
the live service on 2026-08-17.** `v1/artists/{id}` on an application token returns **HTTP 200 with no
`genres` key at all** — absent, not an empty array — checked on The Beatles, Ariana Grande and Don Diablo;
the album object inside a search result carries none either, and neither does the artist object in the
search response. The Web API reference still documents the field. So on a Client Credentials token there
is currently **no route from Spotify to a genre**.

- **`SpotifyCatalog.artistGenres` was written, tested and kept anyway.** It is one request on a track the
  user has deliberately opened, it is correct the moment Spotify sends the field again, and until then it
  changes nothing and says nothing. What it must never do is **overwrite a better answer with an empty
  one**, which is why an empty result is discarded rather than applied. Its path is *derived* from the
  search endpoint rather than written out, so a test pointing the class at a stub gets both redirected
  for one override — `SpotifyCatalogTest` asserts the request lands beside `v1/search`, because getting
  that wrong would send a live request from a unit test.
- **`Library.genreForArtist` is the answer that actually works**, and it is a better one than Spotify's
  ever was: if the user has filed three Crystal Castles songs as Electronic, the fourth almost certainly
  is too. Most common answer wins rather than first, so one mis-filed song does not decide it;
  `UNKNOWN` songs do not vote, because "nobody has said" is not an opinion; and it matches on the
  **whole credit** case-insensitively, so `"Crystal Castles"` and `"Crystal Castles, HEALTH"` are
  deliberately different acts — a feature credit is a different act and guessing across them would be
  worse than not guessing.
- **`Genre.fromTags` lives in `model/`, not in `spotify/`.** Deciding which of our fourteen constants a
  phrase describes is a question about *our* vocabulary; nothing in it mentions Spotify, and `model/`
  may not import it in any case. It matches **substrings, most specific first**, because the tags are
  compounds and the compound names the parent. Get the order backwards and nothing throws — half the
  library simply arrives filed under Pop.
  - **`"dance"` had to go below `"pop"` as well as below `"rock"`**, and a test caught it rather than a
    reading of the list: above them it filed *dance pop* and *dance rock* as electronic, which is three
    wrong answers for one right one. `"garage"` is deliberately absent for the same reason and could not
    be fixed by moving it — it is UK garage and it is garage rock, on opposite sides of the list, and a
    tag filed wrongly half the time is worse than one that falls through to `UNKNOWN`.
  - **`UNKNOWN` rather than `OTHER` when nothing matches.** The two are not interchangeable: `OTHER`
    means the user looked at the list and none of it fitted.
- **The rating is the user's and always was**; setting it while adding saves a trip through the edit
  dialog for the field most likely to be wanted immediately.
- **A `ListView` would have arrived in Modena's own look**, since a bare list view is one of the few
  controls this project has not restyled (§3b). The rows are `Button`s wearing `.history-entry` with a
  `.result-row:selected` rule for the pick, whose ground is `-ui-selected` — the same one the library
  table marks a selected row with. Two CSS rules, no hex literal.
- **`PixelDialog` gained `resizeToContent()` and `showForCapture()`.** The first is the companion
  window's `sizeToScene` lesson in a different window: a dialog whose body grows after it is up gets no
  second pass on its own, and a toolkit will happily clip contents that outgrew it. The second exists
  because `showAndWait` cannot be photographed — it does not return until the dialog closes — and layout
  overflow in this font is invisible to every unit test.
- **`R...` was the first screenshot's verdict**, and it is why the check is a picture. A `GridPane`
  column is one width across every row, so left to itself it settled on what the *shortest* caption
  needed: ALBUM and GENRE fitted at five characters and RATING came out ellipsized, which reads as a
  control with no name on it. `fieldLabel` sets `USE_PREF_SIZE` as the minimum so a caption can never
  shorten itself, and a `ColumnConstraints` makes the column honour it.
- **`[smoke] spotify genre` is worth more for what it says when it comes back empty**, which is now
  always: it prints "Spotify sends no genres field any more (measured)" rather than a blank that could
  equally be a wrong path, a missing scope or an artist id read out of the wrong field. Printed rather
  than asserted, because an empty answer is now the *correct* one and failing a build on it would be a
  red light nobody can act on. `[smoke] library genre` prints what the dialog actually pre-fills with.
- **The existing `[smoke] spotify search` asked for a limit of 50 while the interface asked for 10.**
  That is exactly the discrepancy this file already warns about — "a diagnostic that asks for a different
  limit than the interface does is not testing the interface" — and it was still there. It now uses
  `SpotifyCatalog.MAX_SEARCH_LIMIT`, as the dialog does.

### As built (2026-08-15) — streamed tracks generate courses

**Reported as "the loaded go-librespot tracks don't generate a track (circuit for the game)", and it
was exactly that: every Spotify song reached the runner as an empty course, permanently.** The
milestone notes above claimed the opposite — see the correction in EXP-1 — which is worth stating
plainly, because a note describing a feature that was never written is worse than no note at all.

**The cause was one type.** The whole beatmap pipeline was keyed on `java.nio.file.Path`:
`App.fileOf(song)` handed `BeatmapService.request` a `getFilePath()`, which for a streamed song is
`null`, so the service went idle, `RunnerView.syncCourse` found no beatmap, and
`Course.generate(id, Beatmap.EMPTY, class)` produced nothing. **Nothing threw and nothing was
logged** — this is the `getFilePath()` trap in §11 for a fourth time, and the most expensive one,
because the symptom was a whole feature quietly not working rather than a stack trace.

- **The pipeline is keyed by locator now, not by path**, so `Song.locator()` is the one thing passed
  around and `BeatmapService.Status.file` became `.source`. `BeatmapCache.keyFor(String)` is the
  single place that decides what identifies a track: **a locator naming a readable file is hashed by
  its contents exactly as before, and anything else is hashed as text.** The test is "does this
  resolve to a file", never a `spotify:` prefix — `analysis/` has no business having an opinion
  about who can open what, and a URI is already a stable identity with no bytes to read.
- **There is nothing to analyse ahead of time, so the track is analysed as it is heard.**
  `analysis/StreamBeatmapBuilder` is a `PcmListener` on the routing source: it sums each block to
  mono on the playback thread and hands it to one background thread that runs the transforms. So the
  first play of a streamed track builds its course and every play after that has it — the road, the
  meters and the beat flash all work on that first play, and the banner says
  `LEARNING TRACK - COURSE NEXT PLAY` rather than `GENERATING COURSE`, which would be a lie about
  what the wait is for. That is `Stage.LISTENING`, the one new state a caller can see.
- **The two routes share `BeatmapAnalyzer.fromNovelty`, and that is not tidiness.** A streamed track
  and a local copy of the same recording must generate the *identical* course or a score earned on
  one means nothing on the other. Two near-identical copies of the tempo fit is exactly how that
  drift would start. **Measured on `Crimewave`: 120.0 BPM, 826 onsets, 461 on the beat by both
  routes, arrays equal element for element** — `[smoke] stream vs file` prints it every launch, and
  it listens to the whole 4:18 track at 191x realtime in 1.4 seconds.
- **The tap copies and returns; it never transforms.** A 1024-point FFT is tens of microseconds and
  would very probably fit inside a 23 ms block — "very probably fits" is how audio acquires a stutter
  nobody can reproduce (ground rule 4). `theTapReturnsImmediately` is the check.
- **A run is thrown away rather than trusted in three cases**, all ordinary user actions: a seek
  (`PlaybackEngine.setOnSeek`), a track skipped before `COMPLETE_FRACTION` of its length, and the
  handover queue overflowing. **A partial curve is not a short beatmap, it is a wrong one** — it
  would claim the song ends where the listener stopped listening, and it would be cached and
  believed for good.
- **`PlaybackEngine.setOnTrackEnded` fires before the running order moves**, and the ordering is
  load-bearing: a moment later the beatmap would be filed against whatever came next. It is also why
  it is distinct from a song change — skipping and finishing look identical from there, and one of
  them has produced a usable beatmap.
- **The one bug in this that cost real time: `abandon()` bumped the run counter.** `finishStream()`
  queues the derivation and then abandons, but handing blocks over is asynchronous — so at that
  instant almost the whole track is still queued, and invalidating queued work left the derivation
  with a third of a second of audio. Every streamed course came back empty, with nothing thrown and
  nothing logged, which is the same shape of failure as the original report. Only `arm()` bumps it
  now; ordering on the single worker does the rest, and it is exact where a counter was a guess
  about timing. `abandoningAfterFinishingKeepsWhatWasHeard` pins it.
- **`BeatmapIndex` is locator-keyed too**, so a streamed track earns the library's course-ready badge
  the same way a file does. A badge that could only ever say "no course" for half the library reads
  as the feature being broken rather than as the analysis not having happened yet.
- **Verified on a live Premium account as of 2026-08-15**, once the pipe deadlock below was fixed —
  which is what had been keeping every streamed track silent. The stream path was already proven on
  real music through the file decode, byte for byte; what was missing was audio ever leaving the
  pipe at all.
- 696 tests, up from 660.

### As built (2026-08-15) — the pipe reader never parks

**Reported as "go-librespot would not resume playback", and it was a deadlock between this
application and the daemon rather than anything Spotify refused.** Diagnosed against the live wedged
daemon, which is the only place it exists: no unit test reached it and no screenshot shows it.

**This is the fix that made M10 audible.** For the whole milestone the Spotify path had been written,
reasoned about, unit-tested and never heard, and the standing note said no Premium account was
available. It turned out the account was not the blocker: the reader parked the moment playback
stopped, which wedged the daemon's entire API, so `/player/resume` could never be answered and no
streamed track ever reached the speakers. **Streamed playback and pause/resume were confirmed
working on a live Premium account the same day.**

- **The measurements that located it.** `GET /player/resume` answered **405 instantly** — the route
  is registered and the HTTP server is alive — while `GET /` and `/status` did not answer in 4, 15,
  20 or 25 seconds. The daemon meanwhile held an **established connection to Spotify's access point
  on port 4070**, so it was fully logged in. Draining the FIFO by hand returned `/status` to **200
  immediately** and pulled **25 MB in 6 s, about 24x realtime** — which is what a writer blocked on a
  full pipe does the moment a reader appears. That last one is the proof; everything before it is
  circumstantial.
- **The cycle.** `pause()` cleared `playing` first, which parked the reader, and only then commanded
  the daemon. With nothing reading, the 4 KB FIFO filled in about 23 ms and go-librespot's output
  goroutine blocked inside `write`. Because v0.8.0 serves every HTTP request from that same single
  goroutine over an unbuffered channel with no timeout, the whole API went with it. Then `play()`
  called `api.resume()` **before** `setPlaying(true)`/`startPump()` — so the reader could only
  restart after the daemon answered, and the daemon could only answer after the reader restarted.
  Permanent: every later press threw the same exception.
- **All four transport paths had the shape** (`load`, `pause`, `seek`, `stop`), so track transitions
  were equally exposed — those surface as `"would not load"` instead. And `drainPipe()` could never
  help, because it was called *after* the API call that had already hung.
- **The fix is one persistent reader that consumes the pipe in every state.** A per-track pump that
  never parks is not enough and is worse: `retirePump()` cannot join a thread blocked in `read()`,
  so the old and new pumps would take blocks from one FIFO at random — a hole in the audio, which is
  exactly what a streamed beatmap cannot survive. One thread for the life of the source removes the
  race outright.
- **Three states, all of which still read.** *Stale* (the daemon has just been repositioned) drops
  blocks without publishing them, which is what `drainPipe()` used to do; `settle()` now only
  *watches* `available()` rather than reading, because a second reader on one FIFO is the same hole.
  *Paused* publishes to the taps but does not write to the card. *Playing* does both, and
  `SourceDataLine.write` still paces the daemon exactly as before.
- **Paused audio reaches the taps deliberately, and that is what protects the streamed beatmaps.**
  `StreamBeatmapBuilder`'s novelty curve is a position in the track measured in samples, and
  `abandonStream` is wired to seeks only — pausing has never spoilt a run. Dropping the blocks the
  daemon emits before it notices the pause would have put a hole in the curve instead, which is the
  one failure that class documents as worse than no curve at all because nothing downstream can
  detect it. So pausing a streamed track still yields a course.
- **What is dropped is banked onto the clock.** The daemon resumes from after whatever it emitted,
  so frames discarded while paused are time the track genuinely advanced by. `advanceBase` adds
  them, or `position()` — which the runner's whole lookahead reads off — falls behind the music by
  that much at every pause.
- **`SpotifyAudioSourceTest` is the first test this class has ever had**, and it drives a real FIFO
  and a real socket for the reason `SpotifyApiTest` already does. It writes 256 KB while the source
  is stopped and fails by hanging if the reader parks. **Verified to fail against the old
  behaviour**: `Consumed 4 of 262144 bytes` — the 4 being `publishSilence()`, so not one byte of
  audio moved.
- 698 tests, up from 696.

### M10 notes — kept as the record of *why*, now that it is built

*Superseded where it disagrees with "As built (M10)" above: the binary resolution order gained a
download step and lost the bundled-resource one (there is no binary to bundle for macOS, and
shipping a Linux one in the jar would bloat it for every user who never touches Spotify), and
`peekNext()` needed no change because the modes already had it. Everything else below still holds.*
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
- **`SourceDataLine.write` returns when the audio is *buffered*, not when it has been heard, so a
  `flush()` after it throws away whatever has not played yet.** That is harmless when what is left is
  the inaudible tail of a fade already near zero and is a click the moment the fade is longer than the
  line's buffer — which is what happened the day `SoundEffect.stop(double)` made a 650 ms fade
  possible, to code that had been correct and untouched for a quarter-second one. `drain()` after a
  fade; the wait is bounded by the fade and is on a daemon thread nobody is holding. **A constant that
  is safe at its default is not the same as a constant that is safe.**
- The PCM callback is on the audio thread: analyze fast, return, never touch the scene graph.
- Game timing from `audioSource.position()`, never accumulated frame time.
- **`java.time.Duration.toSeconds()` truncates to a whole `long` second and widens to `double`
  without a warning.** Its JavaFX namesake returns a `double`, so the call site looks correct and the
  compiler agrees. Read the clock through `positionSeconds()`; the only place `toSeconds()` belongs
  is formatting `m:ss`. This cost a milestone's worth of "the game feels laggy" — see §7, M7.
- **Measuring a `Canvas` by timing `redraw()` measures nothing but the command recording.** The
  frame-to-frame interval is the honest number; `-Dsdmk.diag` prints it.
- **There is no GPU: Prism falls back to its software pipeline on this machine**, so fill rate is
  the budget and a full-canvas fill is one of the more expensive things the runner can do. Check
  with `-Dprism.verbose=true` before believing any performance reasoning that assumes hardware
  compositing. A new screen-wide effect should be composited into `RunnerView.drawWashes` rather
  than painted over the top of the existing ones — see §7.
- **Ask what else is running on the machine before treating "it feels laggy" as a rendering bug.**
  The whole of §7's software-pipeline investigation was measured against a `flurry` animated
  wallpaper repainting the desktop the entire time, on an **M4 Mac** — so the absolute frame times
  recorded there are contention, not a hardware ceiling, and have not been re-measured on a quiet
  machine. Three JavaFX versions were swept before anyone looked at the process list.
- **`Song.getFilePath()` returns `null` for a streamed song.** Nothing in the type system says so.
  Go through `Song.locator()`, or ask `isSpotify()` first — the places this was got wrong were a
  details panel, an index request, an edit dialog and **the whole beatmap pipeline**, all of which
  compiled perfectly. The first three threw a `NullPointerException` and were found in minutes; the
  fourth threw nothing, silently gave every Spotify track an empty course, and survived a milestone
  behind a note in this file claiming it worked. **A `null` that is quietly accepted as "nothing
  asked for" is far more dangerous here than one that is dereferenced.**
- **logrus wraps its message in double quotes**, so any pattern scraping a URL out of a
  go-librespot log line must exclude `"` rather than stop at whitespace. One trailing character is
  enough for `URI.create` to reject the whole link, and the only symptom is a button that does
  nothing.
- **A FIFO reader sees end of file the instant its last writer leaves**, so anything reading one
  across track boundaries must hold a write end open too. And go-librespot refuses to open its end
  at all if no reader is there, so the reader has to be first and has to stay.
- **The pipe must never be left unread — not merely left open — and a full pipe kills the daemon's
  entire HTTP API, not just the audio.** The pipe driver has no pacing and blocks inside `write`
  once the FIFO's 4 KB kernel buffer fills; v0.8.0 then serves *every* request from the one
  goroutine that is blocked (`AppPlayer.Run`, handler called inline, unbuffered channel, no
  timeout). Pausing by parking the reader therefore wedged `/status`, `/player/pause` and
  `/player/resume` alike, permanently — and since `play()` asked `/player/resume` *before*
  restarting the reader, the two waited on each other for good. The symptom was
  `"go-librespot would not resume playback"`, which is a 5 s `COMMAND_TIMEOUT` being reported as a
  refusal. `SpotifyAudioSource` now reads in every state; `SpotifyAudioSourceTest` pins it.
- **The daemon's `/status` position is not the clock.** It assumes the output consumes audio at the
  speed it is heard, which a pipe does not; it runs ahead by however far the pipe is buffered. Read
  `SourceDataLine.getLongFramePosition()`, as everywhere else.
- **Anything reached through the daemon's `/web-api` proxy is rate-limited by strangers.** librespot
  hardcodes Spotify's own desktop client id and every instance in the world shares it, so the quota
  is contended globally and does not drain when you stop using it — measured, a `retry-after` that
  rose from 31 to 33 over forty seconds of idleness. Catalogue search goes through `SpotifyCatalog`
  and the user's own registered application for this reason. Only `v1/me/*` still needs the proxy,
  because an application token is not a user.
- **The proxy strips `Retry-After` and the response body**, so a 429 through it is indistinguishable
  from any other empty result. Read the header on a direct call, where it is present.
- **`v1/search` refuses a `limit` above 10, and Spotify's reference says the maximum is 50.** It is a
  flat `400 Invalid limit`, not a trim, so a page size copied from the documentation fails every
  search. `SpotifyCatalog.MAX_SEARCH_LIMIT` clamps it inside the client. **A diagnostic that asks
  for a different limit than the interface does is not testing the interface** — that is what hid
  this for a whole session.
- **A credential arrives by copy and paste, so it must be stripped before it is used.** A trailing
  newline off a web page is invisible in the field, survives into the `id:secret` Base64 blob, and
  comes back as `invalid_client` — the identical answer Spotify gives for a genuinely wrong secret,
  with nothing anywhere to tell the two apart. This was shipped once and reported immediately.
  `SpotifyCatalog` and `SettingsRepository` both `strip()`, and the refusal message now names the
  observed **lengths** (never the values) because "Invalid client" is also what a client id pasted
  into both fields produces.
- **The four Spotify configuration values in §10 fail silently**, every one of them by letting
  Spotify choose the running order while the music goes on sounding fine.
- **The combo multiplies `coins` and must never touch `coinsCollected`.** The rank is a fraction of
  what the generator put on the course; multiply its numerator and a good streak reads as more coins
  than the course ever held, past 100%, and `ScoreEntry`'s own constructor throws on it. The split
  already existed for the star's break bonus — the combo is the second thing that depends on it.
- **A screen-wide wash goes over the entities, so it is a protected-role problem.** The combo heat,
  the beat wash and the event flashes all sit above the coins and the bumps, and a tint that closes
  the gap between them throws nothing and photographs perfectly. Any new one needs a ΔE check across
  every mood, and it will fail on the **light** mood before the dark one. **Washing in a role that
  something on the road is already drawn in is the sharper version of the same trap**: the combo
  washes in `PRIMARY`, which the star and the coin tally are, so that pair cannot separate by moving
  apart — only the ground can move, and it moves *towards* them.
- **A value that glides is a value a screenshot photographs at zero.** The combo heat eases from
  where the picture was, and a preview has no previous picture, so the race shot came out with a
  full meter over an unlit screen and nothing anywhere said so. Anything eased off the game clock
  needs its own `settle()` before a still is taken — the same fix `StructureView`, `MiniPlayerView`
  and `BootScreen` each needed for the same reason.
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
- **Swapping `scene.setRoot` takes the title bar with it**, now that the header is the title bar.
  Anything that replaces what is on screen has to swap the shell's *centre*; replacing the root
  leaves a window that cannot be moved or closed until the same key is pressed again. This is what
  `F5` did.
- **A `Parent` only lays out when something marked it dirty.** A method that changes plain fields and
  then calls `applyCss()` / `layout()` to redraw silently does nothing — the previous frame stays on
  the canvas, which reads as the state never having changed rather than as the redraw never having
  happened. Call `requestLayout()` first. `BootScreen.previewAt` was written wrong this way and the
  only symptom was a screenshot of the wrong phase.
- **FNV-1a avalanches poorly in its high bits**, so a seeded effect that takes its randomness from
  the top of an FNV hash over two small integers is not random at all — measured, *every* band of the
  boot glitch cleared its displacement threshold. Use the SplitMix64 finalizer for a mixer over small
  seeds; keep FNV for identity hashing, where `Course` uses it correctly.
- **A `Canvas` in front of a control eats its clicks**, over the canvas's whole rectangle, however
  little it has drawn. The control still hovers and still does nothing, and the keyboard path keeps
  working so every key-driven test stays green. `setMouseTransparent(true)` on any canvas that
  overlaps something clickable.
- **A `Canvas` in a `StackPane` stops the window ever getting smaller.** Different rule, same class:
  a `StackPane`'s minimum is the largest of its children's minimums, and a non-resizable node reports
  its own size as its minimum — so a canvas sized to its parent pins that parent at whatever it last
  was. The symptom is a window that grows and then **crops** its contents instead of relaying them
  out, which reads as a failure to redraw; nothing throws and a screenshot at either size looks
  right. `setManaged(false)` on the canvas, or hold it in a plain `Pane` (whose minimum is just its
  insets) as every other view here does. This is what `MoodOverlayRenderer` did to the restore
  button, and `[smoke] window shrink` is what now catches it.
- **`Stage.setFullScreen` may not be called from `Application.start()` either**, and the reason is the
  same as the smoke test's: the nested event loop it enters cannot finish while the launcher still has
  the FX thread. Measured at 574 s wedged in `MacApplication._enterNestedEventLoopImpl`. **The window
  draws first and then accepts no input at all**, so it reads as a hit-testing bug in whatever the user
  tried to click rather than as a frozen thread — here, "the cartridge won't drag". Defer it with
  `Platform.runLater`. The rule is not "not during a smoke test", it is **not while anything is holding
  the interface thread**.
- **`Stage.setFullScreen` is a request the platform may silently refuse, so asking is not getting.**
  On macOS it reaches AppKit's `toggleFullScreen:`, which discards the request if the application is
  already mid-transition — the maximise zoom at `show()`, or the previous instance's Space still being
  torn down after a quick relaunch. Nothing throws, nothing is logged and the property does not change.
  Any code that sets its own flag *before* the call and never reads `isFullScreen()` back is then
  describing a window that does not exist: here that was a maximised window with the frame stripped off
  and a button whose caption said the opposite of the truth, which reads as an unreliable launch rather
  than as a desynced boolean. Reconcile after the call **and** listen to `fullScreenProperty` — and
  guard the reconcile against the *other* fullscreen mode, or entering a race silently converts itself
  into a fullscreen window and leaving one becomes impossible.
- **The fullscreen launch still freezes sometimes, it did so before any of this was written, and it is
  not understood.** `Stage.setFullScreen` blocks in a nested event loop until macOS finishes its own
  transition, and on this machine that transition intermittently never finishes — leaving the interface
  thread `RUNNABLE` in `MacApplication._enterNestedEventLoopImpl` and the application drawn and
  completely deaf, exactly as the entry above describes. Deferring the call out of `start()` made it
  rare rather than certain. **Try a logout or a restart first**: the rate climbs steeply as fullscreen
  windows are killed outright, each of which leaves its own Space behind.
- **And the way that was nearly misdiagnosed is worth more than the finding.** The launch fade was
  suspected, and four independent implementations of it — dimming the stage before `show()`, dimming it
  after, fading the scene's root, and a full-canvas fill over the boot screen — each measured **far**
  worse than an untouched launch: about 10 wedges in 16 runs against 2 in 12. Every one of those
  comparisons was wrong. A control run at the *end* of the session wedged **6 times out of 6** on code
  that had never been touched. The environment had been degrading throughout, so the effect attributed
  to the fade was drift.

  This is §7's `flurry` lesson arriving a second time in a different room, and the tell is the same:
  **a rate measured against an environment that is changing under the experiment is not a rate.**
  Interleave the control rather than running it once at the start, and ask what state the *machine* is
  in before concluding anything about the code.
- **Nothing called from inside `runSmokeTest` may enter a nested event loop.** `Stage.setFullScreen`
  does, on macOS: it does not return until the platform's fullscreen transition has finished, and that
  transition can never finish while a synchronous check is holding the interface thread. The run wedged
  in `MacApplication._enterNestedEventLoopImpl` and printed nothing after the line before it — no
  exception, no timeout, just silence. A nested loop also *pumps the event queue*, so it can re-enter the
  check that is running. `showAndWait` is the other one; `PixelDialog.showForCapture` exists for exactly
  that reason.
- **A `Pane` does not clip its children, so a child bigger than the pane draws outside it** — over
  whatever is behind, and out of reach of any canvas sized to the pane. The boot screen's cartridge is
  taller than the travel it makes, so its foot hung below the pane's bottom edge and stayed on screen
  under the loading bar while everything else went black. Worse, it is *off the shot* on a tall window,
  so the screenshot looked fine. Clip the pane, and hide what should not be there rather than relying on
  something painting over it.
- **Spotify's artist object no longer carries `genres` at all**, and its own reference still documents
  the field. Measured 2026-08-17 on an application token: 200, and the key is simply absent for every
  artist tried. An empty genre from that endpoint is now the correct answer, so anything reading it must
  discard an empty result rather than apply it — applying it overwrites a better guess with nothing,
  which is a field getting *worse* the longer the user waits.
- **A `GridPane` column is one width across every row, so it settles on what the shortest label needs
  and ellipsizes the rest.** `RATING` became `R...` beside an `ALBUM` that fitted. Give captions
  `setMinWidth(Region.USE_PREF_SIZE)`; in this font a truncated six-letter word still looks deliberate
  and nothing anywhere reports it.
- **A hex literal anywhere outside `mood/`** — it looks harmless per site and turns M11 into a
  find-and-replace across a finished UI, which is where the feature gets abandoned. **A second palette
  is not a second place for literals**: `Palette.hardware()` is how the boot and shutdown screens get to
  be black and white without putting a colour in `ui/`, and `Palette.bootRainbow()` is how the start-up
  title gets to have a hue — they still name a role and still ask a palette, they just ask a different
  one. **The tell that a new palette is legitimate is that no drawing code changed**: `titleColor` calls
  `Palette.mix(role, role, t)` exactly as the runner's star does.
- **Cycling palette roles is only a rainbow if the palette has hues in it.** The six roles the star walks
  are monochrome in `Palette.hardware()`, so a boot title "cycling" them is a static white title —
  nothing throws, the code reads correctly, and every screenshot looks like a title that is simply white.
  That is why `bootRainbow()` exists and why `theTitleCycles` measures saturation rather than trusting
  the loop.
- **A colour that eases towards another while still cycling does not approach it monotonically.** The
  distance from white wobbles because the hues are not equally far from it, so a "it only ever gets
  closer" assertion is false against correct code. Assert the *envelope* — the room the hue has left —
  which is what the interpolation actually guarantees.
- **Darkening something that is already the darkest colour on screen changes nothing, and it compiles.**
  `CrtEffect`'s mask shades towards `SHADOW`; both bracket screens *are* `SHADOW`. So tearing that mask
  sideways to break the boot glitch up drew a perfect no-op — the screenshot came out identical to the
  one before the change, and nothing anywhere said so. The rule generalises past this one case: **an
  effect whose colour is the ground it is drawn on is invisible by construction**, and the only way to
  find out is to compare the picture rather than to re-read the code. Light the thing instead, where it
  has somewhere to go.
- **A whole-screen effect can only be seen where something is drawn.** That is correct for a scanline
  grille and it means a screenshot of one on a mostly-black screen proves very little; read the pixels
  down a column of text instead (a title row should read clear/soft/dark on a three-row cycle).
- **The same rule is why the tube's rounded corners need a *lit* rim rather than a black bezel.** Blacking
  the corners out is the obvious way to shape a screen and on these two it changes nothing anybody can
  see. `CrtEffect.RIM_LIFT` is what the eye actually reads, and every geometry test about the curvature
  passes with it set to zero.
- **A curvature that shapes the raster and a curvature that shapes the screen are two different numbers.**
  One barrel warp doing both gives a lens: a region touching the window at its four edge-midpoints and
  falling away from all of them, sides bowed over their whole height, corners closed to points. Nothing
  reports it and it is obvious in one screenshot. `CURVATURE` bows the scanlines; `CORNER_SHARE` rounds a
  rectangle.
- **A smoke check placed after something that changes state may be checking nothing.** The boot screen's
  full-threshold drag sat after the glitch and show previews, which move it into a phase that correctly
  refuses a gesture — so the press, drag and release were dropped on the floor for a whole milestone
  while the line beside it read as a passing check. Both `[smoke] boot full drag` and
  `[smoke] cartridge clunk` exist because that only came to light when a *second* check was hung off the
  same gesture.
- **`static final` primitives are inlined into the classes that read them, so tuning one and running
  `./mvnw test` without `clean` tests the old value.** Changing `CrtEffect.RIM_LIFT` from 0.20 to 0.18
  produced `expected: <0.2> but was: <0.18>` from an untouched test — which reads exactly like a real
  regression and is a stale `.class` file. `./mvnw clean test` after moving a constant.
- **Smoothed pixel art.** JavaFX interpolates by default; the sprites turn to mush and it reads
  as a bug in the art, not in the code. `setImageSmoothing(false)`, integer scale factors.
- **Moods: the four protected roles are the whole risk.** A mood that makes coins and bumps the
  same color, or the BST highlight the same as the outline, throws nothing and looks fine in a
  screenshot — it fails live, in front of the room. `MoodValidator` runs on load *and* on every
  edit, and renders a substitute rather than the invalid value.
- **A `Canvas` measured by timing its own draw calls is measured by a factor of two hundred.** The
  mood overlays reported 0.05 ms a frame recorded and 7.1 ms rasterised. Measure a frame with
  `Scene.snapshot`, which runs the window through the same pipeline the pulse does — timing
  `redraw()` or `repaint()` answers a different question, and on this machine the two answers are
  three orders of magnitude apart. This is the third time the project has been caught by it.
- **A tiled layer repeated per frame is several hundred `drawImage` calls.** Repeat it *once* into a
  picture one whole tile larger than the canvas and a scroll offset becomes a source rectangle — the
  same pixels in one call. Worth it only when the tile is a small fraction of the canvas; a
  canvas-sized picture pre-tiled costs four canvases of memory to save three draws.
- **A layer drawn behind an opaque pane is a layer nobody can see.** `.root-pane` had to give up its
  background for the `BEHIND_CONTENT` band to exist at all, and the renderer paints the identical
  ramp instead. Anything new that fills the window's centre has to leave the ground visible or the
  wallpaper silently stops working.
- **`ImageLayer` file names and mood folder names are untrusted input**, because a mood folder is
  unzipped from something somebody sent. `MoodRepository.slug` is the only thing between a text field
  and `Path.resolve`, and `ImageLayer` refuses a name containing `..` or a separator.
- **A dither applied as a nudge to the colour does nothing on an arbitrary palette.** Nearest-colour
  matching over sixteen scattered entries is dominated by chroma, so shifting lightness before
  matching leaves every pixel where it was — a switch that changes no pixels, which looks like a
  feature. Dither *between the two nearest candidates* instead: take the second when the pixel's
  distance ratio exceeds its Bayer threshold.
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
