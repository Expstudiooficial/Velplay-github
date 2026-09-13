package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.audio.Sfx

/**
 * Chapter 4 — Subfloor 4, and out.
 *
 * Opens on the well taking both of them, again, and then simply leaves him
 * standing on the deck with nothing behind him for the first time in four
 * chapters. What is left is a long way down, a door with three bolts, and Ren.
 *
 * Nothing hunts the player in this chapter. That is deliberate: after three
 * chapters of being chased, the horror of Subfloor 4 is that it is empty, and
 * the tension is entirely in its machinery.
 */
class Chapter4Script : ChapterScript() {

    override val chapter = 4
    override val completeStage = Stage4.COMPLETE
    /** Nothing chases in Chapter 4, but the base class requires a budget. */
    override val chaseSeconds = 60f

    /** Which of the three bolts have been released. */
    private val bolts = HashSet<String>()

    override fun build(): Level = Chapter4.build()

    override fun applyStage(g: GameSession, stage: Int) {
        Chapter4.applyStage(g, stage)
        bolts.clear()
        if (stage >= Stage4.OPEN) bolts.addAll(listOf("lock_a", "lock_b", "lock_c"))
    }

    override fun spawnFor(stage: Int): Triple<String, Float, Float> = Chapter4.spawnFor(stage)
    override fun checkpointFor(stage: Int): Int = Stage4.checkpointFor(stage)
    override fun objectiveFor(stage: Int): String = Stage4.objectiveFor(stage)

    override fun onStart(g: GameSession, checkpoint: Int) {
        g.reach.unlocked = true
        g.dodgeUnlocked = true
        // Five, everywhere. Chapter 4's damage comes from beams and falls, which
        // are learnt by being hit by them.
        g.maxHealth = 5
        g.health = 5
        when (checkpoint) {
            Stage4.CORE_REPLAY -> {
                g.beginCut(Cut.CH4_CORE)
                g.player.visible = false
                g.playSound(Sfx.Id.RUMBLE)
            }
            Stage4.TOGETHER -> g.say("Stay behind me.", blocking = false)
            Stage4.ASCENT -> g.beginCut(Cut.CH4_ASCENT)
        }
    }

    // ---- room beats -------------------------------------------------------

    override fun onRoomEntered(g: GameSession, roomId: String) {
        when {
            roomId == "g0" && g.stage < Stage4.DESCENT -> {
                g.setStage(Stage4.DESCENT)
                g.say("Nothing behind me. First time all the way down.")
                g.say("Ren. Last floor. If you're anywhere, you're here.", blocking = false)
            }
            roomId == "lift5" && g.stage < Stage4.LIFT5 -> {
                g.setStage(Stage4.LIFT5)
                g.say("Subfloor 4. There isn't a five.", blocking = false)
            }
            roomId == "h0" && g.stage < Stage4.DEEP4 -> g.setStage(Stage4.DEEP4)
            roomId == "bigdoor4" && g.stage < Stage4.BIG_DOOR -> {
                g.setStage(Stage4.BIG_DOOR)
                g.say("Containment. Not a door — a lid.")
                g.say("Three bolts, and none of them are on this side.", blocking = false)
            }
            roomId == "lock4a" && g.stage < Stage4.LOCKS -> g.setStage(Stage4.LOCKS)
            roomId == "ren4" && g.stage < Stage4.REN -> {
                g.setStage(Stage4.REN)
                g.say("...Ren?", blocking = false)
            }
            roomId == "vent4" && g.stage < Stage4.TOGETHER -> g.setStage(Stage4.TOGETHER)
            roomId == "liftup4" && g.stage < Stage4.LIFT_UP -> {
                g.setStage(Stage4.LIFT_UP)
                g.say("That one goes all the way up. Get in.", blocking = false)
            }
        }
    }

    // ---- per-frame --------------------------------------------------------

    override fun update(g: GameSession, dt: Float) {
        // Mechanism doors. A group that is satisfied opens its door and keeps it
        // open, because a puzzle you have solved should stay solved.
        for ((group, door) in MECH_DOORS) checkMechDoor(g, group, door)

        // The three bolts, each read straight off its room.
        if (g.stage in Stage4.LOCKS until Stage4.OPEN) {
            for (name in BOLT_GROUPS) {
                if (name in bolts) continue
                if (g.room.id == roomForBolt(name) && g.groupSatisfied(name)) releaseBolt(g, name)
            }
        }
    }

    /**
     * A mechanism door stays open once it opens. A plate you had to stand on to
     * release the way out cannot also be a plate you have to keep standing on.
     */
    private fun checkMechDoor(g: GameSession, group: String, doorName: String) {
        val door = g.room.props.firstOrNull { it is Door && it.name == doorName } as? Door ?: return
        if (!door.open && g.groupSatisfied(group)) {
            door.forceOpen()
            g.playSound(Sfx.Id.POWER, 0.8f)
            g.onDoorOpened(door)
        }
    }

    private fun roomForBolt(group: String): String = when (group) {
        "lock_a" -> "lock4a"
        "lock_b" -> "lock4b"
        else -> "lock4c"
    }

    private fun releaseBolt(g: GameSession, group: String) {
        bolts.add(group)
        g.playSound(Sfx.Id.UNLOCK, 1f)
        g.camera.shake(0.35f, 0.7f)
        g.onHaptic?.invoke(60)
        val left = BOLT_GROUPS.count { it !in bolts }
        if (left > 0) {
            g.say("One. ${left} to go.", blocking = false)
        } else {
            g.setStage(Stage4.OPEN)
            (g.level.rooms["bigdoor4"]?.props
                ?.firstOrNull { it is Door && it.name == "door_big4" } as? Door)?.forceOpen()
            g.say("All three. It's coming open.")
            g.say("Whatever is behind that, it has been behind it a long time.", blocking = false)
        }
    }

    // ---- mechanisms -------------------------------------------------------

    override fun onSlapSwitch(g: GameSession, sw: SlapSwitch) {
        when (sw.id) {
            "sw_fan", "sw_fan2", "sw_lockc" -> {
                for (p in g.room.props) if (p is Updraft) p.running = true
                g.say("Column's up.", blocking = false, hold = 0.1f)
            }
            "sw_gate1", "sw_lockb" -> {
                for (p in g.room.props) if (p is TimedGate) p.trigger(g)
                g.say("Move.", blocking = false, hold = 0.1f)
            }
        }
    }

    override fun onCompanionFound(g: GameSession, who: Ren) {
        g.setStage(Stage4.TOGETHER)
        who.following = true
        g.playSound(Sfx.Id.CHIME, 1f)
        g.say("Ren. Ren, it's me.")
        g.say("...you took your time.")
        g.say("Can you walk? — I can walk.")
        g.say("Then walk. There's a duct at the end of this row.", blocking = false)
    }

    override fun onElevatorUse(g: GameSession, elevator: Elevator) {
        when {
            g.stage == Stage4.LIFT5 -> {
                g.beginCut(Cut.CH4_LIFT)
                g.playSound(Sfx.Id.RUMBLE, 0.9f)
            }
            g.stage == Stage4.LIFT_UP -> {
                g.setStage(Stage4.ASCENT)
                g.beginCut(Cut.CH4_ASCENT)
                g.playSound(Sfx.Id.RUMBLE, 0.8f)
            }
            else -> g.say("Not yet.", blocking = false)
        }
    }

    // ---- cutscenes --------------------------------------------------------

    override fun updateCut(g: GameSession, dt: Float): Boolean {
        when (g.cut) {
            Cut.CH4_CORE -> {
                g.endingProgressCh3 += dt / CORE_SECONDS
                if (g.endingProgressCh3 >= 1f) {
                    g.endingProgressCh3 = 1f
                    g.player.visible = true
                    g.endCut()
                    g.setStage(Stage4.DESCENT)
                    g.say("It took both of them.")
                    g.say("And it is still going. Whatever they were feeding, it is awake now.", blocking = false)
                }
                return true
            }
            Cut.CH4_LIFT -> {
                if (g.cutTime >= LIFT_SECONDS) {
                    g.endCut()
                    g.setStage(Stage4.DEEP4)
                    g.moveTo("h0", 3f, 0f)
                    g.holdExits(0.4f)
                }
                return true
            }
            Cut.CH4_ASCENT -> {
                g.endingProgressCh4 += dt / ASCENT_SECONDS
                if (g.endingProgressCh4 >= 1f) {
                    g.endingProgressCh4 = 1f
                    g.setStage(Stage4.COMPLETE)
                }
                return true
            }
            else -> return false
        }
    }

    private companion object {
        val BOLT_GROUPS = listOf("lock_a", "lock_b", "lock_c")
        /** Every room whose way out is held by a mechanism, and the door it holds. */
        val MECH_DOORS = listOf(
            "g3" to "door_g3", "g8" to "door_g8", "g17" to "door_g17",
            "h15" to "door_h15", "h22" to "door_h22", "h36" to "door_h36",
            "h40" to "door_h40"
        )
        const val CORE_SECONDS = 10f
        const val LIFT_SECONDS = 4.2f
        const val ASCENT_SECONDS = 26f
    }
}
