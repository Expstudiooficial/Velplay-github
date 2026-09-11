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
| **JUMP** | Bottom right | Clears a 1.4 m step and a 2.8 m gap. |
| **SNEAK** | Bottom right | Crouch. The only way through low gaps and ducts. |
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
7. **Archive Floor** — you are not alone. **Run.** Fifteen seconds through the
   service runs: vault the server rows, sneak the collapsed bulkhead, and get
   the bulkhead feed connected in the pump room before it reaches you. Miss, and
   it puts its face in yours and you wake up back at the locked lift.
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
files. The whole look comes from flat fills, a ten-colour palette and a single
lit edge on every surface.

### Tests

The suite is mostly simulation rather than unit assertions. A bot drives the
real physics and the real story machine with nothing but the inputs a player
has, and `PlaythroughTest` plays Chapter 1 from the intake hall to the closing
scene — so a level change that strands the player fails the build. Alongside it,
`LevelIntegrityTest` checks the level graph and spawn points, `ProgressionTest`
checks every saveable stage loads into a finishable world, and `TraversalTest`
proves each climb is jumpable and that the chase is both winnable and lethal.

## Licence

MIT — see [LICENSE](LICENSE).
