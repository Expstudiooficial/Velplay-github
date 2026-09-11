# Facility Core

A 2D low-poly horror-puzzle game for Android, by **EXP Studio**.

You are a white figure who walks into a forgotten facility because a friend said
he was trapped inside — and he is the only thing you have left. The place has no
power, no people, and something in the dark that used to be one.

**Chapter 1 — Subfloor 0** is complete and playable.

---

## Playing

| Control | Where | What it does |
|---|---|---|
| Stick | Touch anywhere on the left half and drag | Move. The stick floats to your thumb. |
| **JUMP** | Bottom right | Clears a 1.6 m step and a 3 m gap. Walk into a crate and tap JUMP and you pull yourself up onto it — no run-up needed. |
| **SNEAK** | Bottom right | Crouch. The only way through low gaps and ducts — if something stops you that you could fit under, the game says so and lights the button. |
| **USE** | Right, above JUMP | Lights up whenever something is in reach. |
| **II** | Top right | Pause, restart from the checkpoint, or quit. |

The objective is always on the ribbon at the top of the screen. Your key pack
and any data shards you are carrying sit in the bottom-left corner; while the
pack is broken, tapping it reopens the repair.

### Worlds

**Play** opens the world list: up to **20 slots**, each with its own progress,
play time and puzzle layout. Worlds can be created, renamed, copied and deleted.
Finishing Chapter 1 in any world unlocks **Chapter 2** as a choice when creating
new ones.

Progress saves automatically at every checkpoint.

---

## Chapter 1

Thirteen connected rooms, roughly 20–30 minutes on a first run:

1. **Intake Corridor / Intake Hall** — the door seals behind you. Reroute the
   intake breaker bank (flipping one breaker shakes its neighbours) to open the
   gate.
2. **Sealed Junction** — the way down is locked. *"Locked. I need to unlock this."*
3. **Storage Bay** — a key pack, with its internal loom torn apart. Reconnect
   the four severed wires and it reads again.
4. Back at the junction, climb to the ledge and let the pack pull the door's key
   out of its database.
5. **Feeder Hub / Connection Vault** — haul the heavy feeder cable through and
   throw it up into the connection station. Subfloor 0 goes live.
6. **Lift Landing** — the lift is not in the pack's database. *"I guess I have to
   add it myself."*
7. **Archive Floor** — you are not alone. **Run.** Thirty seconds through the
   service runs: vault the server rows, sneak the collapsed bulkhead, and get
   the bulkhead feed connected in the pump room before it reaches you. It hangs
   about nine metres off your heels the whole way, so the clock is the deadline
   but the thing behind you is what you feel. Miss, and it puts its face in
   yours and you wake up back at the locked lift.
8. **Data Spine** — two feeds, two data shards, one circuit. Write it into the
   archive panel and take your pack back with Subfloor 1 on it.
9. **Return Vent** — crawl back. Something is waiting at the far end.
10. The lift accepts the key. Then the chapter ends.

Chapter 2 picks up on Subfloor 1.

---

## Building

Requires the Android SDK (compileSdk 34) and JDK 17.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # onto a connected device
./gradlew testDebugUnitTest      # the test suite
```

Minimum Android 7.0 (API 24). Landscape only. No runtime permissions beyond
optional vibration.

## Project layout

```
app/src/main/java/com/expstudio/facilitycore/
  core/      geometry, camera, drawing primitives, virtual gamepad
  game/      player and monster physics, level data, props, puzzles,
             the chapter script, and the render/update loop
  save/      world slots and settings (SharedPreferences + JSON)
  audio/     every sound synthesised at runtime — the APK ships no audio assets
  ui/        menu, world list, settings, and the game activity
```

Everything is drawn to a `Canvas`: no game engine, no art assets, no image
files. The whole look is generated — three parallax layers of structure built
deterministically per room, volumetric shafts under every fixture, gradient-shaded
surfaces with lit lips and contact shadows, drifting dust, and a carry-light that
keeps unpowered rooms readable without lifting the dark.

Rendering goes through the hardware canvas where the device offers one, and the
expensive falloffs (glows, the darkness pool, the vignette) are baked into small
cached bitmaps rather than shaded per pixel — that alone took a frame from 45 ms
to 26 ms on the software path.

### Tests

The suite is mostly simulation rather than unit assertions. A bot drives the
real physics and the real story machine with nothing but the inputs a player
has, and `PlaythroughTest` plays Chapter 1 from the intake hall to the closing
scene — so a level change that strands the player fails the build.

- `ObstacleTest` walks the player flush into every obstacle in the game, kills
  the run-up, then taps JUMP — from both sides. This is the guard for "I can't
  jump over this".
- `MantleTest` checks the ledge pull-up cannot be abused: the sealed door's
  ledge still needs its staircase, the chase bulkhead still has to be crawled,
  and no pull-up ever ends inside geometry.
- `CrawlTest` finds every gap in the game too low to walk through, and checks
  each one announces itself and can actually be crawled — plus that a player who
  sprints into the bulkhead and jumps at it before working out it is a crawl
  still survives the chase.
- `LevelIntegrityTest` checks the room graph and every spawn point.
- `ProgressionTest` checks every saveable stage loads into a finishable world.
- `TraversalTest` proves each climb is jumpable, the chase both winnable and
  lethal, and the pursuer close enough to stay a threat.
- `ScreenshotTest` and `RenderCostTest` rasterise real frames off-device, to
  `app/build/screenshots/`, so the art direction and the frame budget can be
  looked at rather than assumed.

The last two use Robolectric, which downloads its Android runtime on first run;
everything else is plain JVM and works offline.

## Licence

MIT — see [LICENSE](LICENSE).
