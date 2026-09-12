package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette

/**
 * Chapter 2 — Subfloor 1.
 *
 * Picks up inside the falling car with the thing from Chapter 1 already in it,
 * and runs through the lift fight, the cell bay, the Playerr's hunt, the hoist
 * escape, the feeder gallery and the smelter.
 */
class Chapter2Script : ChapterScript() {

    override val chapter = 2
    override val completeStage = Stage2.COMPLETE
    override val chaseSeconds: Float
        get() = if (shadePursuit) Chapter2.SHADE_CHASE_SECONDS else Chapter2.CHASE_SECONDS

    /** True once the second pursuit — the shade's — is the live one. */
    private var shadePursuit = false

    // Lift fight state.
    private var attackTimer = 0f
    private var attackPhase = 0f
    private var swinging = false
    private var struckThisSwing = false
    private var liftDescend = 0f

    // Hoist escape state.
    private var hoistTime = 0f

    // Smelter finale state.
    private var ventTime = 0f
    private var ventArmed = false

    override fun build(): Level = Chapter2.build()
    override fun applyStage(g: GameSession, stage: Int) {
        Chapter2.applyStage(g, stage)
        shadePursuit = stage >= Stage2.SHADE_RETURN
        if (shadePursuit) g.monster.setRoute(Chapter2.shadeRoute())
        attackTimer = 0f
        attackPhase = 0f
        swinging = false
        liftDescend = 0f
        hoistTime = 0f
        ventTime = 0f
        ventArmed = stage > Stage2.VENT_RUN
    }

    override fun spawnFor(stage: Int): Triple<String, Float, Float> = Chapter2.spawnFor(stage)
    override fun checkpointFor(stage: Int): Int = Stage2.checkpointFor(stage)
    override fun objectiveFor(stage: Int): String = Stage2.objectiveFor(stage)

    override fun onStart(g: GameSession, checkpoint: Int) {
        g.dodgeUnlocked = true
        when (checkpoint) {
            Stage2.OPENING -> {
                g.beginCut(Cut.CH2_OPENING)
                g.player.visible = false
                g.playSound(Sfx.Id.RUMBLE)
            }
            Stage2.LIFT_FIGHT -> beginFight(g, silent = true)
            Stage2.MEET_PLAYERR ->
                g.say("It took the door off its hinges. It knows I'm here.", blocking = false)
            Stage2.DEAD_END ->
                g.say("No doors. Just that duct.", blocking = false)
            Stage2.SMELTER_DOOR -> {
                armSmelter(g)
                g.say("The hatch. Pack. Now.", blocking = false)
            }
        }
    }

    // ---- room beats -------------------------------------------------------

    override fun onRoomEntered(g: GameSession, roomId: String) {
        when (roomId) {
            "landing1" -> if (g.stage < Stage2.ARRIVAL) {
                g.setStage(Stage2.ARRIVAL)
                g.say("Subfloor 1. Colder than I thought it would be.")
                g.say("Ren? ...Ren!", blocking = false)
            }
            "battery" -> if (g.stage < Stage2.FIND_BATTERY) {
                g.setStage(Stage2.FIND_BATTERY)
                g.say("Cell bay. The gate needs one of these to move.", blocking = false)
            }
            "hall" -> if (g.stage == Stage2.GATE_OPEN) {
                g.setStage(Stage2.MEET_PLAYERR)
                g.say("Holding cells. All of them open.", blocking = false)
            }
            "hoist" -> if (g.stage == Stage2.PLAYERR_CHASE) {
                g.say("Hoist controls — the pack, use the pack!", blocking = false)
            }
            "feed" -> if (g.stage in Stage2.DROPPED until Stage2.FEED_ROOM) {
                g.setStage(Stage2.FEED_ROOM)
                g.say("Charger gallery. That feeder has to go up.", blocking = false)
            }
            "shade" -> if (g.stage == Stage2.SPINE_OPEN) {
                g.setStage(Stage2.SHADE_RETURN)
            }
            "deadend" -> if (g.stage == Stage2.SHADE_CHASE) {
                g.setStage(Stage2.DEAD_END)
                g.stopChase()
                g.say("No — no, there's no door!", blocking = false)
            }
            "vent2" -> if (g.stage == Stage2.DEAD_END) {
                g.setStage(Stage2.VENT_RUN)
                ventTime = 0f
                g.say("Keep low. Keep going.", blocking = false)
            }
            "smelter" -> if (g.stage < Stage2.ENDING) {
                beginFinale(g)
            }
        }
    }

    // ---- per-frame --------------------------------------------------------

    override fun update(g: GameSession, dt: Float) {
        when (g.stage) {
            Stage2.LIFT_FIGHT -> updateFight(g, dt)
            Stage2.MEET_PLAYERR ->
                if (g.cut == Cut.NONE && g.room.id == "hall" && g.player.x >= Chapter2.HALL_TRIGGER_X) {
                    beginDoorBreak(g)
                }
            Stage2.SHADE_RETURN ->
                if (g.cut == Cut.NONE && g.room.id == "shade" && g.player.x >= Chapter2.SHADE_TRIGGER_X) {
                    beginShadeReturn(g)
                }
            Stage2.VENT_RUN -> updateVentRun(g, dt)
            Stage2.SMELTER_ARMED -> updateVentRun(g, dt)
        }
    }

    // ---- the lift fight ---------------------------------------------------

    private fun beginFight(g: GameSession, silent: Boolean) {
        g.setStage(Stage2.LIFT_FIGHT)
        g.maxHealth = 3
        g.health = 3
        g.dodgeUnlocked = true
        g.monster.kind = Monster.Kind.SHADE
        g.monster.mode = Monster.Mode.ATTACKING
        g.monster.place("car", 12.6f, 0f, -1)
        attackTimer = FIRST_SWING
        swinging = false
        if (!silent) {
            g.say("It came down with me. Of course it did.")
            g.say("The car's wrecked — three jobs and it moves again.")
            g.say("Roll. Do not let it touch you.", blocking = false)
        }
    }

    private fun updateFight(g: GameSession, dt: Float) {
        if (g.cut != Cut.NONE) return
        val monster = g.monster
        monster.mode = Monster.Mode.ATTACKING

        // It stalks toward the player between swings.
        if (!swinging) {
            val toward = if (g.player.x > monster.x) 1f else -1f
            monster.x = MathX.clamp(monster.x + toward * STALK_SPEED * dt, 1.2f, 13.8f)
            monster.facing = if (toward > 0f) 1 else -1
            attackTimer -= dt
            if (attackTimer <= 0f) {
                swinging = true
                attackPhase = 0f
                struckThisSwing = false
                g.playSound(Sfx.Id.RUMBLE, 0.6f)
            }
        } else {
            attackPhase += dt / SWING_SECONDS
            monster.swing = attackPhase
            // The strike lands in the middle of the sweep.
            if (!struckThisSwing && attackPhase in STRIKE_AT..(STRIKE_AT + 0.18f)) {
                struckThisSwing = true
                val reach = 2.6f
                val inFront = (g.player.x - monster.x) * monster.facing
                val hit = inFront in -0.6f..reach && !g.player.isInvulnerable
                if (hit) {
                    g.takeHit(monster.facing, Stage2.LIFT_FIGHT)
                } else if (g.player.isInvulnerable) {
                    g.say("Close.", blocking = false, hold = 0.2f)
                }
                g.camera.shake(0.25f, 0.45f)
                g.particles.sparkBurst(
                    monster.x + monster.facing * 2f, -0.2f, 14, 1.3f, 6f, Monster.PLAYERR_SCAR
                )
            }
            if (attackPhase >= 1f) {
                swinging = false
                monster.swing = 0f
                attackTimer = SWING_GAP
            }
        }

        val tasks = g.level.room("car").props.filterIsInstance<LiftTask>()
        if (tasks.isNotEmpty() && tasks.all { it.done } && g.stage == Stage2.LIFT_FIGHT) {
            g.setStage(Stage2.LIFT_FIXED)
            g.maxHealth = 0
            monster.mode = Monster.Mode.HIDDEN
            monster.swing = 0f
            g.beginCut(Cut.CH2_FIGHT_INTRO)
            liftDescend = 0f
            g.playSound(Sfx.Id.POWER)
            g.camera.shake(0.4f, 1.0f)
            g.say("Brakes are off — hold on!")
        }
    }

    // ---- the Playerr ------------------------------------------------------

    private fun beginDoorBreak(g: GameSession) {
        g.beginCut(Cut.DOOR_BREAK)
        g.monster.kind = Monster.Kind.PLAYERR
        g.monster.mode = Monster.Mode.LURKING
        g.monster.place("hall", Chapter2.HALL_TRIGGER_X - 6f, 0f, 1)
        g.playSound(Sfx.Id.RUMBLE)
        g.camera.shake(0.3f, 0.8f)
    }

    private fun beginShadeReturn(g: GameSession) {
        g.beginCut(Cut.ENCOUNTER)
        shadePursuit = true
        g.monster.kind = Monster.Kind.SHADE
        g.monster.setRoute(Chapter2.shadeRoute())
        g.monster.mode = Monster.Mode.LURKING
        g.monster.place("shade", Chapter2.SHADE_TRIGGER_X - 6f, 0f, 1)
        g.playSound(Sfx.Id.RUMBLE)
        g.camera.shake(0.2f, 0.7f)
        g.say("You again.", blocking = false)
    }

    // ---- the duct and the smelter ----------------------------------------

    private fun updateVentRun(g: GameSession, dt: Float) {
        if (g.cut != Cut.NONE) return
        ventTime += dt
        // The shade is behind you in the duct, and once the pour is live the
        // gantry is cooking. Either way the clock is something catching up.
        val limit = if (g.stage == Stage2.VENT_RUN) Chapter2.VENT_SECONDS
        else Chapter2.VENT_SECONDS + Chapter2.HATCH_SECONDS
        if (ventTime >= limit) g.killPlayer()
    }

    private fun armSmelter(g: GameSession) {
        (g.level.room("vent2").props.firstOrNull { it is Smelter } as? Smelter)?.active = true
        ventArmed = true
    }

    private fun beginFinale(g: GameSession) {
        g.setStage(Stage2.ENDING)
        g.beginCut(Cut.SMELTER_END)
        g.monster.mode = Monster.Mode.HIDDEN
        g.playSound(Sfx.Id.RUMBLE)
        g.camera.shake(0.3f, 1.2f)
    }

    // ---- cutscenes --------------------------------------------------------

    override fun updateCut(g: GameSession, dt: Float): Boolean {
        when (g.cut) {
            Cut.CH2_OPENING -> {
                g.endingProgressCh2 += dt / OPENING_SECONDS
                if (g.endingProgressCh2 >= 1f) {
                    g.endingProgressCh2 = 1f
                    g.player.visible = true
                    g.endCut()
                    beginFight(g, silent = false)
                }
            }
            Cut.CH2_FIGHT_INTRO -> {
                // The car drops the last few floors and the doors give way.
                liftDescend += dt
                g.camera.shake(0.12f, 0.2f)
                if (liftDescend > 2.6f) {
                    g.endCut()
                    g.setStage(Stage2.ARRIVAL)
                    (g.level.room("car").props.firstOrNull { it is Door && it.name == "door_car" } as? Door)
                        ?.let { it.locked = false; it.forceOpen() }
                    g.playSound(Sfx.Id.UNLOCK)
                    g.say("...it's gone. It left me here.", blocking = false)
                }
            }
            Cut.DOOR_BREAK -> {
                val breakable = g.level.room("hall").props
                    .firstOrNull { it is BreakableDoor } as? BreakableDoor
                if (g.cutTime > 0.7f && breakable != null && !breakable.broken) {
                    breakable.broken = true
                    g.playSound(Sfx.Id.THUD)
                    g.playSound(Sfx.Id.SCREAM)
                    g.camera.shake(0.45f, 0.9f)
                    g.onHaptic?.invoke(140)
                    g.particles.debris(breakable.box.cx, breakable.box.cy, 26, 7f, Palette.TRIM)
                    g.monster.mode = Monster.Mode.ALERTED
                    g.say("...that is not the same thing.", blocking = false)
                }
                if (g.cutTime > 2.1f) {
                    g.endCut()
                    shadePursuit = false
                    g.monster.setRoute(
                        listOf(
                            ChaseLeg("hall", Chapter2.HALL_TRIGGER_X - 6f, 27.5f, 0f),
                            ChaseLeg("maze_a", 0.5f, 15.5f, 0f),
                            ChaseLeg("maze_b", 0.5f, 15.5f, 0f),
                            ChaseLeg("hoist", 0.5f, 14.5f, 0f)
                        )
                    )
                    g.monster.kind = Monster.Kind.PLAYERR
                    g.startChase(Stage2.PLAYERR_CHASE)
                    g.say("RUN.", blocking = false, hold = 0.6f)
                }
            }
            Cut.ENCOUNTER -> {
                if (g.cutTime > 0.8f && g.monster.mode == Monster.Mode.LURKING) {
                    g.monster.mode = Monster.Mode.ALERTED
                    g.playSound(Sfx.Id.SCREAM)
                    g.camera.shake(0.3f, 0.8f)
                    g.onHaptic?.invoke(90)
                }
                if (g.cutTime > 1.7f) {
                    g.endCut()
                    g.startChase(Stage2.SHADE_CHASE)
                    g.say("RUN.", blocking = false, hold = 0.6f)
                }
            }
            Cut.GRABBED -> {
                hoistTime += dt
                val grabbers = g.level.room("hoist").props.firstOrNull { it is Grabbers } as? Grabbers
                grabbers?.armed = true
                if (hoistTime < 1.0f) {
                    // The claws come down and take you off the floor.
                    g.player.visible = true
                } else if (hoistTime < HOIST_HOLD) {
                    grabbers?.holding = true
                    // Hoisted out of reach; it passes underneath.
                    g.player.teleport(g.player.x, -3.2f - (hoistTime - 1f) * 0.6f)
                    g.player.vy = 0f
                    if (g.monster.mode != Monster.Mode.HIDDEN) {
                        g.monster.x += dt * 6.5f
                        if (g.monster.x > 21f) {
                            g.monster.mode = Monster.Mode.HIDDEN
                            g.playSound(Sfx.Id.RUMBLE, 0.5f)
                        }
                    }
                } else {
                    grabbers?.holding = false
                    grabbers?.armed = false
                    g.setCut(Cut.HOISTED)
                }
            }
            Cut.HOISTED -> {
                // Lowered back onto the deck.
                g.player.vy = 3.2f
                if (g.player.onGround || g.cutTime > 2.5f) {
                    g.endCut()
                    g.setStage(Stage2.DROPPED)
                    (g.level.room("hoist").props.firstOrNull { it is Door && it.name == "door_hoist" } as? Door)
                        ?.let { it.locked = false; it.forceOpen() }
                    g.playSound(Sfx.Id.THUD, 0.5f)
                    g.say("It went straight past. It never looked up.")
                    g.say("Move, before it circles back.", blocking = false)
                }
            }
            Cut.SMELTER_END -> {
                g.endingProgressCh2 += dt / FINALE_SECONDS
                if (g.endingProgressCh2 >= 1f) {
                    g.endingProgressCh2 = 1f
                    g.setStage(Stage2.COMPLETE)
                }
            }
            else -> return false
        }
        return true
    }

    // ---- prop events ------------------------------------------------------

    override fun onTaskCompleted(g: GameSession, task: LiftTask) {
        val left = g.level.room("car").props.filterIsInstance<LiftTask>().count { !it.done }
        if (left > 0) g.say("$left to go.", blocking = false, hold = 0.3f)
    }

    override fun onCubeTaken(g: GameSession, cube: PowerCube) {
        if (g.stage < Stage2.BATTERY_CARRIED) g.setStage(Stage2.BATTERY_CARRIED)
        g.say("Still warm. Something down here is drawing power.", blocking = false)
    }

    override fun onCubeInserted(g: GameSession, socket: CubeSocket) {
        when (socket.id) {
            "sock_gate" -> {
                g.setStage(Stage2.GATE_OPEN)
                (g.level.room("gate").props.firstOrNull { it is Door && it.name == "door_gate" } as? Door)
                    ?.let { it.locked = false; it.forceOpen() }
                g.say("Gate's live.", blocking = false)
            }
            "sock_spine" -> {
                g.setStage(Stage2.SPINE_OPEN)
                (g.level.room("spine2").props.firstOrNull { it is Door && it.name == "door_spine" } as? Door)
                    ?.let { it.locked = false; it.forceOpen() }
                g.say("Spine lock open.", blocking = false)
            }
        }
    }

    override fun onSwitchUsed(g: GameSession, sw: KeySwitch) {
        if (sw.id != "sw_hoist") return
        g.stopChase()
        g.beginCut(Cut.GRABBED)
        hoistTime = 0f
        g.setStage(Stage2.HOIST_ESCAPE)
        g.camera.shake(0.3f, 0.7f)
        g.playSound(Sfx.Id.POWER)
    }

    override fun onCableGrabbed(g: GameSession, cable: CableCoil) {
        if (cable.id == "cable_v") g.say("Fast. Fast!", blocking = false, hold = 0.2f)
        else g.say("Heavy as ever.", blocking = false, hold = 0.2f)
    }

    override fun onStationConnected(g: GameSession, station: ConnectionStation) {
        when (station.id) {
            "st_feed" -> {
                g.setStage(Stage2.CUBE_TAKEN)
                g.say("Charger's running. It'll cut a cell loose in a second.", blocking = false)
            }
            "st_vent" -> {
                g.setStage(Stage2.SMELTER_ARMED)
                armSmelter(g)
                g.camera.shake(0.4f, 1.1f)
                g.playSound(Sfx.Id.POWER)
                g.particles.embers(station.box.cx, station.box.b, 14, Smelter.LAVA_HOT)
                g.say("The pour's live. Everything on this line just woke up.")
                g.say("The hatch — go!", blocking = false)
            }
        }
    }

    override fun onShardTaken(g: GameSession) {
        // In Chapter 2 the charger gives up a battery cell, not a data shard.
        g.shards = 0
        val cube = g.level.room("feed").props
            .filterIsInstance<PowerCube>().firstOrNull { it.id == "cube_b" }
        if (cube != null) {
            cube.consumed = false
            cube.box.set(g.player.x - 0.27f, g.player.y - 0.82f, g.player.x + 0.27f, g.player.y - 0.27f)
        }
        g.say("There — a charged cell. That opens the spine.", blocking = false)
    }

    override fun onDoorOpened(g: GameSession, door: Door) {
        if (door.name == "door_smelt") {
            if (g.stage < Stage2.SMELTER_DOOR) g.setStage(Stage2.SMELTER_DOOR)
            g.say("Through — GO!", blocking = false)
        }
    }

    companion object {
        const val FIRST_SWING = 2.2f
        const val SWING_SECONDS = 1.15f
        const val SWING_GAP = 1.9f
        const val STRIKE_AT = 0.52f
        const val STALK_SPEED = 1.5f
        const val HOIST_HOLD = 5.0f
        const val OPENING_SECONDS = 11f
        const val FINALE_SECONDS = 9f
    }
}
