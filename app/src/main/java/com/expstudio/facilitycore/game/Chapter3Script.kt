package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.MathX

/**
 * Chapter 3 — Subfloors 2 and 3.
 *
 * Opens on Chapter 2's pour, seen again through the glass of the deck above it,
 * and runs to the core.
 *
 * The chapter's shape is: the archive (where the hand is found), the duct, the
 * long eastern spur, the detour round a door that does not exist as far as the
 * facility is concerned, Subfloor 3, the coolant loop, the master index, the
 * riser, the ignition gallery, and the pit.
 */
class Chapter3Script : ChapterScript() {

    override val chapter = 3
    override val completeStage = Stage3.COMPLETE
    override val chaseSeconds: Float
        get() = if (deepPursuit) Chapter3.DEEP_CHASE_SECONDS else Chapter3.CHASE_SECONDS

    private var deepPursuit = false

    // The archive set-piece.
    private var attackTimer = 0f
    private var attackPhase = 0f
    private var swinging = false
    private var struckThisSwing = false
    private var archiveRng = Compose.Rng(0x11CE)

    // The duct clock.
    private var ventTime = 0f

    // The boss.
    private var bossTime = 0f
    private var boss2Timer = 0f
    private var boss2Phase = 0f
    private var boss2Swinging = false
    private var boss2Struck = false

    // The evacuation clock, and the seconds each valve buys back.
    private var sprintTime = 0f
    private var sprintBudget = SPRINT_START

    override fun build(): Level = Chapter3.build()

    override fun applyStage(g: GameSession, stage: Int) {
        Chapter3.applyStage(g, stage)
        deepPursuit = stage >= Stage3.DEEP_CHASE
        if (stage >= Stage3.BULKHEAD && stage < Stage3.DEEP_CHASE) {
            g.monster.setRoute(Chapter3.bulkRoute())
        }
        if (deepPursuit) g.monster.setRoute(Chapter3.deepRoute(g.level.rooms))
        attackTimer = 0f
        attackPhase = 0f
        swinging = false
        ventTime = 0f
        bossTime = 0f
        boss2Timer = 0f
        boss2Phase = 0f
        boss2Swinging = false
        sprintTime = 0f
        sprintBudget = SPRINT_START
        archiveRng = Compose.Rng(0x11CE + stage.toLong())

        // The coolant terminal is inert until its three valves are open.
        (g.level.rooms["corepz3"]?.props?.firstOrNull { it is DataTerminal } as? DataTerminal)?.let { t ->
            t.requires = { s -> s.level.room("corepz3").props.filterIsInstance<SnapValve>().all { it.done } }
            t.blockedMessage = "No pressure in the loop. The three valves first."
        }
    }

    override fun spawnFor(stage: Int): Triple<String, Float, Float> = Chapter3.spawnFor(stage)
    override fun checkpointFor(stage: Int): Int = Stage3.checkpointFor(stage)
    override fun objectiveFor(stage: Int): String = Stage3.objectiveFor(stage)

    override fun onStart(g: GameSession, checkpoint: Int) {
        g.dodgeUnlocked = true
        g.reach.unlocked = checkpoint >= Stage3.HAND
        when (checkpoint) {
            Stage3.LAVA -> {
                g.beginCut(Cut.CH3_LAVA)
                g.player.visible = false
                g.playSound(Sfx.Id.RUMBLE)
            }
            Stage3.HAND -> {
                beginArchiveFight(g, silent = true)
                (archiveCore(g))?.let { it.open = true }
                g.say("It's still in here. Take it and go.", blocking = false)
            }
            Stage3.VENT_RUN -> {
                ventTime = 0f
                g.say("Keep low. Don't stop.", blocking = false)
            }
            Stage3.STUCK_LIFT -> g.say("Stuck. Of course it's stuck.", blocking = false)
            Stage3.BOSS -> beginBoss(g, silent = true)
            Stage3.FINAL_RUN -> {
                sprintTime = 0f
                sprintBudget = SPRINT_START
                g.say("The whole level is going. Every valve I can reach.", blocking = false)
            }
            Stage3.PIT -> g.say("There it is. The head of the core.", blocking = false)
        }
    }

    // ---- room beats -------------------------------------------------------

    override fun onRoomEntered(g: GameSession, roomId: String) {
        when {
            roomId == "a0" && g.stage < Stage3.DESCEND -> {
                g.setStage(Stage3.DESCEND)
                g.say("Service level. Two floors under the pour.")
                g.say("Ren. If you're down here, say something.", blocking = false)
            }
            roomId == "archive3" && g.stage < Stage3.ARCHIVE -> {
                g.setStage(Stage3.ARCHIVE)
                g.say("The archive. Everything this place ever knew is in here.", blocking = false)
            }
            roomId == "juke3" && g.stage == Stage3.JUKE -> {
                // It comes through the door behind you. There is one way up.
                g.monster.place("juke3", 2.0f, 0f, 1)
                g.monster.mode = Monster.Mode.ATTACKING
                attackTimer = FIRST_SWING
                swinging = false
                g.say("Up. The duct — go up!", blocking = false)
            }
            roomId == "vent3" && g.stage == Stage3.JUKE -> {
                g.setStage(Stage3.VENT_RUN)
                g.monster.mode = Monster.Mode.HIDDEN
                ventTime = 0f
                g.say("It can't follow me in here. It can follow me to the end of it.", blocking = false)
            }
            roomId == "seal3" && g.stage == Stage3.VENT_RUN -> {
                g.setStage(Stage3.SEAL)
                g.say("Shutters. Bring them down.", blocking = false)
            }
            roomId == "b0" && g.stage < Stage3.RUN_B -> g.setStage(Stage3.RUN_B)
            roomId == "bigdoor3" && g.stage < Stage3.BIG_DOOR -> {
                g.setStage(Stage3.BIG_DOOR)
                g.say("That is not a door. That's a wall someone put a handle on.")
                g.say("Nothing in the pack even reads it.", blocking = false)
            }
            roomId == "c0" && g.stage < Stage3.DETOUR -> {
                g.setStage(Stage3.DETOUR)
                g.say("Round, then. The hard way round.", blocking = false)
            }
            roomId == "bulkA" && g.stage < Stage3.BULKHEAD -> {
                deepPursuit = false
                g.monster.kind = Monster.Kind.SHADE
                g.monster.setRoute(Chapter3.bulkRoute())
                g.startChase(Stage3.BULKHEAD)
                g.say("No. NO —", blocking = false)
            }
            roomId == "d0" && g.stage == Stage3.BULKHEAD ->
                g.survivedChase(Stage3.AFTER_BULK, "shutter3")
            roomId == "lift3" && g.stage < Stage3.LIFT3 -> {
                g.setStage(Stage3.LIFT3)
                g.say("Subfloor 3. That's as deep as this place goes.", blocking = false)
            }
            roomId == "e26" && g.stage == Stage3.DEEP -> {
                deepPursuit = true
                g.monster.kind = Monster.Kind.PLAYERR
                g.monster.setRoute(Chapter3.deepRoute(g.level.rooms))
                g.startChase(Stage3.DEEP_CHASE)
            }
            roomId == "corepz3" && g.stage == Stage3.DEEP_CHASE -> {
                g.stopChase()
                g.setStage(Stage3.CORE_PUZZLE)
                g.player.speedScale = 1f
                g.playSound(Sfx.Id.SHUTTER, 0.9f)
                g.say("Lost it in the gallery. That won't last.", blocking = false)
            }
            roomId == "corepz3" && g.stage < Stage3.CORE_PUZZLE -> g.setStage(Stage3.CORE_PUZZLE)
            roomId == "superdb3" && g.stage < Stage3.SUPER_DB -> {
                g.setStage(Stage3.SUPER_DB)
                superDb(g)?.available = true
                g.say("The master index. Every door in the facility, in one rack.", blocking = false)
            }
            roomId == "q0" && g.stage < Stage3.QUIET -> g.setStage(Stage3.QUIET)
            roomId == "lift4" && g.stage < Stage3.STUCK_LIFT -> beginStuckLift(g)
            roomId == "shaft3" && g.stage < Stage3.SHAFT -> {
                g.setStage(Stage3.SHAFT)
                g.say("Straight up. Nothing else for it.", blocking = false)
            }
            roomId == "core3" && g.stage < Stage3.LASER_ROOM -> {
                g.setStage(Stage3.LASER_ROOM)
                g.say("Ignition gallery. Four emitters, all of them cold.")
                g.say("Arm them and this place makes its own sun.", blocking = false)
            }
            roomId == "f0" && g.stage < Stage3.FINAL_RUN -> {
                g.setStage(Stage3.FINAL_RUN)
                sprintTime = 0f
                sprintBudget = SPRINT_START
                g.say("RUN.", blocking = false)
            }
            roomId == "pit3" && g.stage < Stage3.PIT -> {
                g.setStage(Stage3.PIT)
                g.stopChase()
                g.say("The head of the core. And a hole where the floor was.", blocking = false)
            }
        }
    }

    // ---- per-frame --------------------------------------------------------

    override fun update(g: GameSession, dt: Float) {
        when (g.stage) {
            Stage3.ARCHIVE ->
                if (g.cut == Cut.NONE && g.room.id == "archive3" && g.player.x >= Chapter3.ARCHIVE_TRIGGER_X) {
                    beginArchiveFight(g, silent = false)
                }
            Stage3.ARCHIVE_PUZZLE, Stage3.HAND -> updateHunt(g, dt, Stage3.ARCHIVE_PUZZLE)
            Stage3.JUKE -> if (g.room.id == "juke3") updateHunt(g, dt, Stage3.JUKE)
            Stage3.VENT_RUN -> updateVentRun(g, dt)
            Stage3.BOSS -> updateBoss(g, dt)
            Stage3.FINAL_RUN -> updateSprint(g, dt)
            Stage3.PIT -> updatePit(g, dt)
        }
    }

    // ---- the archive ------------------------------------------------------

    private fun archiveCore(g: GameSession): ArchiveCore? =
        g.level.rooms["archive3"]?.props?.firstOrNull { it is ArchiveCore } as? ArchiveCore

    private fun superDb(g: GameSession): SuperDatabase? =
        g.level.rooms["superdb3"]?.props?.firstOrNull { it is SuperDatabase } as? SuperDatabase

    private fun beginArchiveFight(g: GameSession, silent: Boolean) {
        g.setStage(Stage3.ARCHIVE_PUZZLE)
        g.maxHealth = 3
        g.health = 3
        g.monster.kind = Monster.Kind.PLAYERR
        g.monster.mode = Monster.Mode.ATTACKING
        g.monster.place("archive3", 27.0f, 0f, -1)
        attackTimer = FIRST_SWING
        swinging = false
        if (!silent) {
            g.say("It was waiting in here. It was waiting the whole time.")
            g.say("Five index nodes. It calls them, I touch them, in order.")
            g.say("Roll when it swings. Do not stop moving.", blocking = false)
        }
        startWave(g, 0)
    }

    /** Builds the wave's sequence and plays it back on the core. */
    private fun startWave(g: GameSession, wave: Int) {
        val core = archiveCore(g) ?: return
        core.wave = wave
        core.progress = 0
        val length = 3 + wave
        // A permutation, never a sequence with repeats. A node lights once it
        // has been touched, and a lit node cannot be pressed again — so a
        // sequence that called the same index twice was unfinishable.
        val pool = IntArray(NODE_COUNT) { it }
        var n = NODE_COUNT
        while (n > 1) {
            val j = archiveRng.int(n)
            n--
            val tmp = pool[n]; pool[n] = pool[j]; pool[j] = tmp
        }
        val seq = IntArray(length) { pool[it] }
        core.sequence = seq
        core.beginShow()
        for (p in g.level.room("archive3").props) if (p is ArchiveNode) p.lit = false
        g.playSound(Sfx.Id.CHIME, 0.8f)
    }

    override fun onArchiveNode(g: GameSession, node: ArchiveNode) {
        val core = archiveCore(g) ?: return
        if (core.open || core.sequence.isEmpty()) return
        if (core.sequence[core.progress] == node.index) {
            node.lit = true
            core.progress++
            g.playSound(Sfx.Id.CONFIRM, 0.7f)
            if (core.progress >= core.sequence.size) {
                if (core.wave + 1 >= ArchiveCore.WAVES) {
                    core.open = true
                    g.setStage(Stage3.HAND)
                    g.playSound(Sfx.Id.POWER, 1f)
                    g.say("It's open. Whatever is in there, I'm taking it.", blocking = false)
                } else {
                    g.say("Next wave. Longer.", blocking = false, hold = 0.2f)
                    startWave(g, core.wave + 1)
                }
            }
        } else {
            // Wrong node: the wave resets, but nothing kills you for it. The
            // punishment is the seconds, and the seconds are already expensive.
            node.wrongFlash = 0.5f
            core.progress = 0
            for (p in g.level.room("archive3").props) if (p is ArchiveNode) p.lit = false
            core.beginShow()
            g.playSound(Sfx.Id.DENY, 0.9f)
            g.camera.shake(0.15f, 0.3f)
        }
    }

    override fun onArchiveCoreTaken(g: GameSession) {
        if (g.stage >= Stage3.JUKE) return
        g.reach.unlocked = true
        g.setStage(Stage3.JUKE)
        g.playSound(Sfx.Id.UNLOCK, 1f)
        g.say("...it's an arm. A spool of one.")
        g.say("Facility issue. For reaching into things nobody should reach into.")
        g.say("REACH grabs anything marked. Bars pull me. Everything else pulls to me.", blocking = false)
        (g.level.rooms["archive3"]?.props?.firstOrNull { it is Door && it.name == "door_arch" } as? Door)
            ?.forceOpen()
    }

    /**
     * The Playerr's attack loop. Shared by the archive and the sorting floor,
     * because it is the same animal doing the same thing.
     */
    private fun updateHunt(g: GameSession, dt: Float, retryStage: Int) {
        if (g.cut != Cut.NONE) return
        val m = g.monster
        if (m.mode != Monster.Mode.ATTACKING) return
        val bounds = g.room.bounds

        if (!swinging) {
            val toward = if (g.player.x > m.x) 1f else -1f
            m.x = MathX.clamp(m.x + toward * STALK_SPEED * dt, bounds.l + 1.2f, bounds.r - 1.2f)
            m.facing = if (toward > 0f) 1 else -1
            // It walks the stacks too. Standing on the top step is a better
            // position, not a safe one.
            m.y = groundUnder(g, m.x)
            attackTimer -= dt
            if (attackTimer <= 0f) {
                swinging = true
                attackPhase = 0f
                struckThisSwing = false
                g.playSound(Sfx.Id.SNARL, 0.75f)
            }
        } else {
            attackPhase += dt / SWING_SECONDS
            m.swing = attackPhase
            if (!struckThisSwing && attackPhase in STRIKE_AT..(STRIKE_AT + 0.18f)) {
                struckThisSwing = true
                val inFront = (g.player.x - m.x) * m.facing
                // Height matters here: the nodes are up on shelves, and being
                // above the sweep is a real answer as well as rolling under it.
                val low = g.player.y > m.y - 2.4f
                val hit = inFront in -0.7f..STRIKE_REACH && low && !g.player.isInvulnerable
                if (hit) g.takeHit(m.facing, retryStage)
                g.camera.shake(0.25f, 0.45f)
                g.particles.sparkBurst(
                    m.x + m.facing * 2f, -0.2f, 14, 1.3f, 6f, Monster.PLAYERR_SCAR
                )
            }
            if (attackPhase >= 1f) {
                swinging = false
                m.swing = 0f
                attackTimer = SWING_GAP
            }
        }
    }

    /** The highest surface under [x] in the current room, as a foot height. */
    private fun groundUnder(g: GameSession, x: Float): Float {
        var best = 0f
        for (s in g.room.solids) {
            if (s.kind == Solid.Kind.STRUCTURE && s.box.t >= 0f) continue
            if (x < s.box.l - 0.2f || x > s.box.r + 0.2f) continue
            if (s.box.t < best) best = s.box.t
        }
        return best
    }

    // ---- the duct ---------------------------------------------------------

    private fun updateVentRun(g: GameSession, dt: Float) {
        if (g.cut != Cut.NONE || g.room.id != "vent3") return
        ventTime += dt
        // Nothing is drawn behind you. Nothing has to be.
        if (ventTime > Chapter3.VENT_SECONDS * 0.45f && g.room.id == "vent3") {
            g.addTension(dt * 0.6f)
        }
        if (ventTime >= Chapter3.VENT_SECONDS) g.killPlayer()
    }

    override fun onAnchorPulled(g: GameSession, anchor: ReachAnchor) {
        when (anchor.id) {
            "shutter_vent" -> if (g.stage == Stage3.SEAL) {
                g.setStage(Stage3.RUN_B)
                g.playSound(Sfx.Id.SHUTTER, 1f)
                g.camera.shake(0.4f, 0.7f)
                g.onHaptic?.invoke(60)
                (g.level.rooms["seal3"]?.props?.firstOrNull { it is Door && it.name == "door_seal" } as? Door)
                    ?.forceOpen()
                g.say("Sealed. It can have the duct.", blocking = false)
            }
            "rip_lift" -> if (g.stage == Stage3.STUCK_LIFT) {
                g.setStage(Stage3.SHAFT)
                g.camera.shake(0.6f, 0.9f)
                g.onHaptic?.invoke(90)
                g.say("There. Out.", blocking = false)
            }
            "bar_s5" -> (g.level.rooms["shaft3"]?.props
                ?.firstOrNull { it is Door && it.name == "door_shaft" } as? Door)?.forceOpen()
            "crate_pit" -> if (g.stage == Stage3.PIT) checkPitBlocked(g)
        }
    }

    // ---- the lifts --------------------------------------------------------

    override fun onElevatorUse(g: GameSession, elevator: Elevator) {
        if (g.stage < Stage3.LIFT3) {
            g.say("Not yet.", blocking = false)
            return
        }
        if (g.stage > Stage3.LIFT3) return
        g.beginCut(Cut.CH3_LIFT)
        g.playSound(Sfx.Id.RUMBLE, 0.9f)
    }

    private fun beginStuckLift(g: GameSession) {
        g.setStage(Stage3.STUCK_LIFT)
        g.beginCut(Cut.CH3_STUCK)
        g.playSound(Sfx.Id.RUMBLE, 0.8f)
    }

    override fun onSuperDatabaseTaken(g: GameSession) {
        g.setStage(Stage3.QUIET)
        g.playSound(Sfx.Id.POWER, 1f)
        (g.level.rooms["superdb3"]?.props?.firstOrNull { it is Door && it.name == "door_master" } as? Door)
            ?.forceOpen()
        g.say("Every door. Including the one that wasn't there.")
        g.say("Ren's not on it. Nobody is.", blocking = false)
    }

    // ---- the ignition gallery --------------------------------------------

    override fun onEmitterArmed(g: GameSession, emitter: LaserEmitter) {
        val all = g.level.room("core3").props.filterIsInstance<LaserEmitter>()
        val left = all.count { !it.armed }
        if (left > 0) {
            g.say("$left to go.", blocking = false, hold = 0.1f)
            return
        }
        g.setStage(Stage3.CORE_LIT)
        g.beginCut(Cut.CH3_IGNITE)
        for (e in all) e.firing = true
        g.playSound(Sfx.Id.POWER, 1f)
        g.camera.shake(0.7f, 1.6f)
    }

    private fun beginBoss(g: GameSession, silent: Boolean) {
        g.setStage(Stage3.BOSS)
        g.maxHealth = 4
        g.health = 4
        bossTime = 0f
        g.monster.kind = Monster.Kind.PLAYERR
        g.monster.mode = Monster.Mode.ATTACKING
        g.monster.place("core3", 38f, 0f, -1)
        g.monster2.kind = Monster.Kind.SHADE
        g.monster2.mode = Monster.Mode.ATTACKING
        g.monster2.place("core3", 5f, 0f, 1)
        attackTimer = FIRST_SWING
        boss2Timer = FIRST_SWING * 1.6f
        swinging = false
        boss2Swinging = false
        if (!silent) {
            g.say("Both of you. Fine.")
            g.say("Stay off the well. Keep rolling.", blocking = false)
        }
    }

    /**
     * Two attackers, offset so their swings interleave rather than land
     * together — a simultaneous double strike is not a fight, it is a coin toss.
     */
    private fun updateBoss(g: GameSession, dt: Float) {
        if (g.cut != Cut.NONE) return
        updateHunt(g, dt, Stage3.BOSS)

        val m = g.monster2
        val bounds = g.room.bounds
        if (!boss2Swinging) {
            val toward = if (g.player.x > m.x) 1f else -1f
            // Deliberately the slower of the two, so the pair always leaves one
            // side softer than the other and the squeeze has a way out of it.
            m.x = MathX.clamp(m.x + toward * STALK_SPEED * 0.68f * dt, bounds.l + 1.2f, bounds.r - 1.2f)
            m.facing = if (toward > 0f) 1 else -1
            m.y = groundUnder(g, m.x)
            boss2Timer -= dt
            if (boss2Timer <= 0f) {
                boss2Swinging = true
                boss2Phase = 0f
                boss2Struck = false
                g.playSound(Sfx.Id.SNARL, 0.6f)
            }
        } else {
            boss2Phase += dt / SWING_SECONDS
            m.swing = boss2Phase
            if (!boss2Struck && boss2Phase in STRIKE_AT..(STRIKE_AT + 0.18f)) {
                boss2Struck = true
                val inFront = (g.player.x - m.x) * m.facing
                val low = g.player.y > m.y - 2.4f
                if (inFront in -0.7f..STRIKE_REACH && low && !g.player.isInvulnerable) {
                    g.takeHit(m.facing, Stage3.BOSS)
                }
                g.camera.shake(0.2f, 0.4f)
            }
            if (boss2Phase >= 1f) {
                boss2Swinging = false
                m.swing = 0f
                boss2Timer = SWING_GAP * 1.45f
            }
        }

        bossTime += dt
        g.addTension(dt * 0.5f)
        if (bossTime >= Chapter3.BOSS_SECONDS) {
            g.setStage(Stage3.FINAL_RUN)
            g.maxHealth = 0
            g.monster.mode = Monster.Mode.HIDDEN
            g.monster2.mode = Monster.Mode.HIDDEN
            g.monster.swing = 0f
            g.monster2.swing = 0f
            (g.level.rooms["core3"]?.props?.firstOrNull { it is Door && it.name == "door_core" } as? Door)
                ?.forceOpen()
            g.camera.shake(0.6f, 1.4f)
            g.playSound(Sfx.Id.RUMBLE, 1f)
            g.say("The well's taking the whole floor. GO.", blocking = false)
        }
    }

    // ---- the evacuation ---------------------------------------------------

    override fun onValvePulled(g: GameSession, valve: SnapValve, group: String) {
        when (group) {
            "sprint" -> {
                sprintBudget += SPRINT_PER_VALVE
                g.playSound(Sfx.Id.CHIME, 0.7f)
                g.say("+${SPRINT_PER_VALVE.toInt()}s", blocking = false, hold = 0.1f)
            }
            "coolant" -> {
                val left = g.level.room("corepz3").props.filterIsInstance<SnapValve>().count { !it.done }
                if (left > 0) g.say("$left more.", blocking = false, hold = 0.1f)
                else g.say("Pressure. The routing panel will read now.", blocking = false)
            }
        }
    }

    /**
     * The run out. The corridor behind is going, and every valve on the way buys
     * seconds back — so the fast route and the safe route are not the same route.
     */
    private fun updateSprint(g: GameSession, dt: Float) {
        if (g.cut != Cut.NONE) return
        sprintTime += dt
        g.addTension(dt * 0.7f)
        if (sprintTime >= sprintBudget) g.killPlayer()
    }

    /** Seconds left in the evacuation, for the HUD. */
    val sprintRemaining: Float get() = (sprintBudget - sprintTime).coerceAtLeast(0f)

    // ---- the pit ----------------------------------------------------------

    private var pitCrossed = false
    private var pitBlocked = false
    private var pitArrival = 0f

    private fun updatePit(g: GameSession, dt: Float) {
        if (g.cut != Cut.NONE) return
        // The hole is a core, not a gap. Without this the session simply lifts
        // the player back to floor level and they drift across it falling.
        if (g.player.y > 1.2f) { g.killPlayer(); return }
        if (!pitCrossed && g.player.x > 22f) {
            pitCrossed = true
            g.say("Across.", blocking = false)
            g.say("They'll come the same way. Unless there's nothing to land on.", blocking = false)
        }
        if (!pitCrossed || pitBlocked) {
            if (pitBlocked) {
                pitArrival += dt
                if (pitArrival >= PIT_ARRIVAL_SECONDS) beginFinale(g)
            }
            return
        }
        // Once the crate is over the lip, the jump they are about to make stops
        // being a jump.
        checkPitBlocked(g)
    }

    private fun checkPitBlocked(g: GameSession) {
        if (pitBlocked || !pitCrossed) return
        val crate = g.level.room("pit3").props.firstOrNull { it is HeavyCrate } as? HeavyCrate ?: return
        if (crate.box.l <= 23.4f) {
            pitBlocked = true
            pitArrival = 0f
            g.playSound(Sfx.Id.IMPACT, 1f)
            g.camera.shake(0.35f, 0.6f)
            g.say("That's the landing gone.", blocking = false)
        }
    }

    private fun beginFinale(g: GameSession) {
        g.setStage(Stage3.ENDING)
        g.beginCut(Cut.CH3_ENDING)
        g.monster.mode = Monster.Mode.HIDDEN
        g.monster2.mode = Monster.Mode.HIDDEN
        g.playSound(Sfx.Id.RISER, 1f)
    }

    // ---- cutscenes --------------------------------------------------------

    override fun updateCut(g: GameSession, dt: Float): Boolean {
        when (g.cut) {
            Cut.CH3_LAVA -> {
                g.endingProgressCh3 += dt / LAVA_SECONDS
                if (g.endingProgressCh3 >= 1f) {
                    g.endingProgressCh3 = 1f
                    g.player.visible = true
                    g.endCut()
                    g.setStage(Stage3.DESCEND)
                    g.say("It went in. It went in and it did not stop screaming.")
                    g.say("And the pour just kept going.", blocking = false)
                }
                return true
            }
            Cut.CH3_LIFT -> {
                if (g.cutTime >= LIFT_SECONDS) {
                    g.endCut()
                    g.setStage(Stage3.DEEP)
                    g.moveTo("e0", 3f, 0f)
                    g.holdExits(0.4f)
                    g.say("Subfloor 3.", blocking = false)
                }
                return true
            }
            Cut.CH3_STUCK -> {
                if (g.cutTime >= STUCK_SECONDS) {
                    g.endCut()
                    g.camera.shake(0.5f, 0.8f)
                    g.playSound(Sfx.Id.IMPACT, 0.9f)
                    g.say("Stopped. Between floors.")
                    g.say("The doors, then. REACH — both hands.", blocking = false)
                }
                return true
            }
            Cut.CH3_IGNITE -> {
                val well = g.level.rooms["core3"]?.props?.firstOrNull { it is CoreWell } as? CoreWell
                val p = (g.cutTime / IGNITE_SECONDS).coerceIn(0f, 1f)
                well?.charge = p
                if (g.cutTime >= IGNITE_SECONDS) {
                    g.endCut()
                    beginBoss(g, silent = false)
                }
                return true
            }
            Cut.CH3_ENDING -> {
                g.endingProgressCh3 += dt / ENDING_SECONDS
                if (g.endingProgressCh3 >= 1f) {
                    g.endingProgressCh3 = 1f
                    // setStage fires the completion callback itself.
                    g.setStage(Stage3.COMPLETE)
                }
                return true
            }
            else -> return false
        }
    }

    companion object {
        const val NODE_COUNT = 5
        const val FIRST_SWING = 1.9f
        const val SWING_SECONDS = 0.78f
        const val SWING_GAP = 1.35f
        const val STRIKE_AT = 0.42f
        const val STRIKE_REACH = 2.7f
        const val STALK_SPEED = 2.55f

        const val SPRINT_START = 42f
        const val SPRINT_PER_VALVE = 9f

        /** The widest the evacuation bar ever has to represent. */
        const val SPRINT_SHOWN_MAX = SPRINT_START + SPRINT_PER_VALVE * 5f

        const val PIT_ARRIVAL_SECONDS = 2.4f

        const val LAVA_SECONDS = 9.5f
        const val LIFT_SECONDS = 4.2f
        const val STUCK_SECONDS = 3.4f
        const val IGNITE_SECONDS = 4.8f
        const val ENDING_SECONDS = 11f
    }
}
