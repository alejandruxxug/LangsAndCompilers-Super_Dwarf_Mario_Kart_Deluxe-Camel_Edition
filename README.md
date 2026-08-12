# Super_Dwarf_Mario_Kart_Deluxe-Camel_Edition

A JavaFX desktop music player built around three **hand-written data structures**, with a
beat-synchronised Mario-Kart-styled rhythm game riding on top of the audio it plays.

Data Structures course project, Universidad EIA.

> The name is intentional, underscores and hyphen included. It is styled after a ROM filename
> and pairs with the 8-bit font. Short form: `SDMK_Deluxe`.

---

## Prerequisites

| Requirement | Notes |
|---|---|
| **JDK 25** | Built and tested on OpenJDK 25.0.2. |
| **Maven** | **Not required.** Use the bundled wrapper (`./mvnw`), which fetches Maven 3.8.5 on first use. |
| Internet, once | Only to download dependencies the first time. The application itself runs fully offline — the font is bundled, nothing is fetched at runtime. |

Everything else, including JavaFX 26, is resolved by Maven.

## Running

```bash
./mvnw javafx:run          # launch
./mvnw test                # run the JUnit suite
./mvnw clean compile       # compile only
```

Verify a launch without leaving a window on screen — it starts, prints what it checked, and
closes itself:

```bash
./mvnw javafx:run -Dsdmk.smokeTest=true
```

```
[smoke] window shown      : true
[smoke] window title      : Super_Dwarf_Mario_Kart_Deluxe-Camel_Edition
[smoke] 8-bit font loaded : true
[smoke] library view      : true
[smoke] songs loaded      : 5
[smoke] RESULT            : PASS
```

Two further switches help when demonstrating or debugging:

| Switch | Effect |
|---|---|
| `-Dsdmk.home=/tmp/sdmk-demo` | Use a scratch profile instead of `~/.superdwarfkart`, so a demo library can be seeded without touching your real one |
| `-Dsdmk.screenshot=shot.png` | During a smoke test, save a snapshot of the window |

## Project status

Built milestone by milestone; each is verified before the next begins.

| # | Milestone | Status |
|---|---|---|
| M0 | Maven skeleton, `javafx:run` opens a styled window, font loads | ✅ |
| M1 | Domain model + the three hand-written structures + JUnit tests | ✅ |
| M2 | Library CRUD, search, 0–100 rating, cover display, JSON persistence | ✅ |
| — | Asset registry: keyword classification, frame inference, `assets.json`, drop-in folder | ✅ |
| M3 | Three playback modes behind one interface, mode selector, complexity panel | ✅ |
| M4 | **Structure visualizer** — circuit, starting grid, animated BST traversal, live complexity scatter, Presentation Mode | ✅ |
| M5 | Real playback, PCM tap, independent left/right level meters | ✅ |
| M6 | Offline beat analysis and beatmap cache | ⬜ |
| M7 | The 3-lane rhythm runner | ⬜ |
| M8 | Mini companion window | ⬜ |
| M9 | Favourites, history, statistics, keyboard shortcuts | ⬜ |
| M11 | **Mood system** — 16-colour GBA palettes, overlay layers, pixel editor, palette import. Dark mode ships here, as two moods rather than a boolean | ⬜ |
| M10 | *Optional:* Spotify playback through go-librespot | ⬜ |

---

## The three hand-written structures

None of these is backed by a `java.util` collection. All are generic, and every public method
documents its time complexity in its Javadoc.

### `CircularDoublyLinkedList<T>` — shuffle playback
Doubly linked and circular, with no null terminators: past the tail is the head, before the head
is the tail, so traversal never ends. `next()` and `previous()` are O(1) — being doubly linked is
what makes stepping backwards constant rather than a full lap.

The shuffle is baked into the ring **once at load time** rather than rolled per step, so
`previous()` returns the song actually played before, and the next song is always predictable.

Its iterator walks **exactly one lap** and then stops, so a `for-each` over an endless ring
terminates.

### `SimpleQueue<T>` — arrival order playback
Strict FIFO with head and tail pointers, O(1) at both ends.

The queue is a **view built from the library, never the storage of it**. Draining it consumes
the view; the library keeps every song and the mode can be rebuilt at any time. A queue cannot
be walked backwards, so this mode reports that it has no `previous()` and the user interface
disables that button instead of letting it throw.

### `BinarySearchTree<T>` — alphabetical playback
Ordered by title, then artist, then identifier, case-insensitively. The tiebreakers matter: with
title alone, two different songs sharing a title compare equal and one is silently dropped.

Every node carries a **parent pointer**, and playback navigation is **real tree navigation** —
the in-order successor descends into the right subtree and runs left to its minimum, or climbs
through parents until it arrives from a left child; the predecessor mirrors it. The tree is never
flattened into an array and indexed.

Deletion handles all three cases (leaf, one child, two children via the in-order successor) by
**relinking nodes rather than copying values between them**.

---

## Where to put assets

There are two places artwork can live, and the registry scans both recursively — the folder
layout inside them does not matter:

| Where | For what |
|---|---|
| `src/main/resources/assets/` | art that ships with the application; needs a rebuild |
| `~/.superdwarfkart/assets/` | **drop-in folder — no rebuild.** Wins over a bundled file of the same name |

So new art can be tried out by copying it into `~/.superdwarfkart/assets/` and restarting.

Files are classified by case-insensitive keyword in the filename. **The table is in priority
order**: a name matching more than one row takes the first, because a filename says what a sprite
*is* and then qualifies it — `kart_explosion.png` is an explosion, `racer-select.png` is a menu.

| Asset | Matched keywords |
|---|---|
| Spinning disk | `disk`, `disc` |
| Select screen | `select` |
| Star | `star`, `estrella` |
| Coin | `coin`, `moneda` |
| Explosion | `explos` |
| Obstacle | `bump`, `obstacle` |
| Background | `bg`, `background`, `fondo` |
| Racer | `char`, `player`, `kart`, `racer`, `personaje`, **and every racer's name** |

A racer is also matched by name, because `Mario.png` contains none of those keywords. Two-letter
keywords match only as whole words, so `bgm-theme.png` is not mistaken for a background.

Spritesheets are sliced by inference: where the frame count is known the frame width is the image
width divided by that count; otherwise **frames are assumed square**, which correctly reads the
disk as 13 frames, the star as 9, the coin as 1 and each racer as 4 — no frame table needed.
Scanning reads filenames only; an image is decoded the first time it is actually drawn, so a
folder of large sheets costs nothing at startup.

**Nothing here is mandatory.** Any missing asset resolves to a labelled magenta placeholder and
logs one warning — the application always starts, with or without art. Run with
`-Dsdmk.smokeTest=true` to print what was found, what is missing, and how each sheet was sliced.

### Overriding the detection

An optional `assets.json` overrides every guess:

```json
{ "key": "untitled-4", "kind": "STAR", "file": "untitled-4.png", "frames": 9 }
```

`kind` is one of the rows above (or `UNKNOWN`); `frames` of `0` means "work it out from the
image". If no manifest exists, one is written to **`~/.superdwarfkart/assets/assets.json`** on
first run, already filled in with whatever was detected — so a wrong guess is corrected by
editing a line rather than by writing the file from scratch. It goes in the user folder because
that is the only writable location that survives `./mvnw clean`. An existing manifest is never
overwritten.

*(A few Spanish keywords appear in the table above. They are there because some sprites were
exported with Spanish filenames before the English-only rule existed. This is the only place in
the project where Spanish appears, and only ever as a pattern to match filenames against —
never as an identifier or as anything the user sees.)*

---

## The three playback modes

The mode selector at the top of the window does not change a setting — it swaps the data
structure the library is played from. `Player` holds a `PlaybackMode` and never asks which one
it is holding: no `instanceof`, no switch. Everything that follows from the choice is read back
off the interface.

| Mode | Structure | Order | Going back |
|---|---|---|---|
| Shuffle | `CircularDoublyLinkedList` | shuffled once at load, then fixed | O(1), and the ring wraps |
| Arrival order | `SimpleQueue` | as added to the library | **not possible** |
| Alphabetical | `BinarySearchTree` | by title, then artist | O(log n) predecessor |

The **complexity panel** on the left lists what the active mode's operations cost and the
current `n`, straight from `PlaybackMode.complexities()`. Switching mode changes the list — the
same search is O(n) over the ring and O(log n) in the tree, and the panel says so.

In arrival order the **previous button is disabled**, with a tooltip explaining why, rather than
being left to fail. A queue has no backwards; the interface says that instead of hiding it.

Two things this design protects:

- **Playing never empties your library.** A mode is a view built *from* the library, not the
  storage of it. Drain the queue to the end and the library still has every song — switching
  mode rebuilds from it.
- **Editing a song does not reorder playback.** Moving the rating slider would otherwise re-draw
  the shuffle on every pixel of travel, changing the running order while you listen.

Select a song in the table and press **Enter** to jump to it. Double-click still opens the
editor.

---

## How the beatmap cache works

Beat detection runs **off the playback path**, on a background thread, on import or first play —
never on the audio thread.

1. The file is decoded to PCM as fast as it reads.
2. A spectral-flux novelty curve is computed over 1024-sample windows with a 512-sample hop.
3. Peaks above an adaptive local threshold, at least ~100 ms apart, become onsets.
4. The tempo is estimated from a histogram of intervals between onsets; the onsets closest to
   that grid are marked as strong beats.

The result — tempo, duration, onsets, strong beats, intensity — is written to:

```
~/.superdwarfkart/beatmaps/<sha256-of-the-audio-file>.json
```

The cache key is the **content hash of the file plus the analyzer version**. Hashing the content
means renaming or moving a file does not force reanalysis, and including the analyzer version
means improving the algorithm invalidates every stale map automatically. A cached file is never
re-analysed.

Other per-user state lives alongside it in `~/.superdwarfkart/`: `library.json` and `scores.json`.

---

## Where each requirement is implemented

| Requirement | Where |
|---|---|
| Circular doubly linked list, by hand | `ds/CircularDoublyLinkedList.java` |
| FIFO queue, by hand | `ds/SimpleQueue.java` |
| Binary search tree, by hand | `ds/BinarySearchTree.java` |
| Generic types on all three | `<T>` on every structure |
| Documented time complexity | Javadoc on every public method of `ds/` |
| Unit tests for the structures | `src/test/java/com/eia/superdwarfkart/ds/` |
| Encapsulation and validation | `model/Song.java` — private fields, validating setters |
| Inheritance and polymorphism | `playback/` — `PlaybackMode` → `AbstractPlaybackMode` → three modes |
| Interfaces | `PlaybackMode`, `AudioSource`, `PcmListener`, `Repository<T>`, `StepCounter` |
| Playback mode 1 — shuffle | `playback/ShuffleMode` over the circular list |
| Playback mode 2 — arrival order | `playback/ArrivalOrderMode` over the queue |
| Playback mode 3 — alphabetical | `playback/AlphabeticalMode` over the tree |
| Library CRUD, search, filters | `model/Library`, `ui/LibraryView` |
| 0–100 rating | `model/Song#setRating` — throws outside the range; slider in `ui/LibraryView` and `ui/SongDialog` |
| Persistence | `persistence/LibraryRepository` — JSON under `~/.superdwarfkart/` |
| Cover display with fallback | `ui/LibraryView#showCover` — labelled placeholder when absent |
| Separation of logic and presentation | `ds/`, `model/`, `playback/`, `audio/`, `analysis/` import no JavaFX |
| Bonus: favourites, play counts | `model/Song` |
| Bonus: complexity instrumentation | `ds/StepCounter` → `ui/visualizer/OperationCounter` *(M4)* |

---

## Architecture notes

**Logic and presentation are strictly separated.** Nothing in `ds/`, `model/`, `playback/`,
`audio/` or `analysis/` imports `javafx.*` — including `javafx.util.Duration`, which is why the
model uses `java.time.Duration`. The user interface observes shared state; it never owns it.

**The step counter is declared in `ds/`, not in the visualizer that consumes it.** The structures
must not depend on the presentation layer, so `ds/StepCounter` is the seam and
`ui/visualizer/OperationCounter` implements it, passed in through a constructor. It defaults to a
no-op so instrumentation never sits in a hot path.

**The audio thread is never blocked.** The PCM callback copies, computes and returns; levels are
published into atomics and the user interface polls them from an `AnimationTimer` at ~60 fps
rather than being pushed a message per audio block.

**There is no `module-info.java`.** The project runs on the classpath deliberately: `mp3spi`,
`jlayer` and `tritonus-share` are legacy non-modular jars whose `javax.sound.sampled` service
registration is awkward under the module system.

### Audio format

Everything downstream assumes one format: **PCM signed, 44100 Hz, 16-bit, stereo,
little-endian**, 4 bytes per frame, interleaved as `[L0 R0 L1 R1 …]`.

Every file reaches that format whatever it started as, which is what lets the meters, the beat
analyser and the game each assume one shape of buffer and one sample rate. Getting there takes
**one conversion where the file is already at 44.1 kHz stereo, and two where it is not** — a
decoder only offers its output at the file's own rate and channel count, so a 22 kHz mono MP3 has
to be decoded first and resampled second. Both stages were measured against the resolved jars
rather than assumed.

Level meters are computed **per channel, never as one combined number**: samples are
deinterleaved (even index left, odd index right) and RMS and peak are tracked separately for each.
The bars are drawn on a logarithmic scale from −60 dBFS, because on a linear one ordinary music
never leaves the bottom of the bar.

---

## Optional: Spotify playback

Not built, and nothing in the application depends on it. If it is attempted, it plugs in behind
the `AudioSource` interface and nothing outside `audio/` learns that a subprocess exists.

The implementation would be **`devgianlu/go-librespot`**, run as a child process writing raw
`s16le` PCM to a named pipe — which is exactly the format the analyzer and the meters already
expect, so nothing downstream would change.

Two settings are correctness requirements rather than preferences: `zeroconf_enabled: false` and
`disable_autoplay: true`. If either is wrong, Spotify chooses the next track instead of the
active playback mode, and the data structures become decorations while playback carries on
looking normal.

**`librespot-java` (`xyz.gianlu.librespot`) is deprecated and has been tested — it does not
work.** It needs JitPack or a protoc source build, and requires a Premium account. Do not spend
time on it. `librespot-org/librespot-golang` is a different, archived project.

Spotify playback would also be POSIX-only, since it needs `mkfifo`. Local file playback stays
portable.

---

## Credits

**Press Start 2P** by CodeMan38, bundled under the SIL Open Font License 1.1. The full licence
text ships alongside the font at `src/main/resources/fonts/OFL.txt`.
