package com.expstudio.facilitycore.game

import com.expstudio.facilitycore.audio.Sfx

/** Chapter 1 — Subfloor 0. The beats that were the whole game to begin with. */
class Chapter1Script : ChapterScript() {

    override val chapter = 1
    override val chaseSeconds = Chapter1.CHASE_SECONDS
    override val completeStage = Stage.COMPLETE

    override fun build(): Level = Chapter1.build()
    override fun applyStage(g: GameSession, stage: Int) = Chapter1.applyStage(g, stage)
    override fun spawnFor(stage: Int): Triple<String, Float, Float> = Chapter1.spawnFor(stage)
    override fun checkpointFor(stage: Int): Int = Stage.checkpointFor(stage)
    override fun objectiveFor(stage: Int): String = Stage.objectiveFor(stage)

    override fun onStart(g: GameSession, checkpoint: Int) {
        when (checkpoint) {
            Stage.INTRO -> {
                g.beginCut(Cut.INTRO)
                g.say("The intake door sealed behind me. Of course it did.")
                g.say("Ren said he got trapped down here three days ago.")
                g.say("He's the only thing I've got left. So I'm here.")
                g.say("Nobody's answered a radio from this place in years.", hold = 0.4f)
            }
            Stage.ELEVATOR_DENIED ->
                g.say("Back at the lift. It still doesn't know this floor exists.", blocking = false)
        }
    }

    override fun onRoomEntered(g: GameSession, roomId: String) {
        when (roomId) {
            "lobby" -> if (g.stage < Stage.LOBBY_PUZZLE) {
                g.setStage(Stage.LOBBY_PUZZLE)
                g.say("Intake hall. Dead as everything else.", blocking = false)
            }
            "sealed" -> if (g.stage < Stage.FIND_KEYPACK) {
                g.setStage(Stage.FIND_KEYPACK)
                g.say("There's the way down. Sealed, naturally.", blocking = false)
            }
            "storage" -> if (g.stage == Stage.FIND_KEYPACK) {
                g.say("Storage. Somebody left in a hurry.", blocking = false)
            }
            "hub" -> if (g.stage < Stage.FETCH_BIG_WIRE) g.setStage(Stage.FETCH_BIG_WIRE)
            "power" -> if (g.stage == Stage.FETCH_BIG_WIRE && g.carriedCable == null) {
                g.say("The feeder's back the other way. I need to bring it here.", blocking = false)
            }
            "lift" -> if (g.stage == Stage.REACH_ELEVATOR) {
                g.setStage(Stage.ELEVATOR_DENIED)
                g.say("An elevator. Subfloor 1. That's got to be where he is.", blocking = false)
            }
            "archive" -> if (g.stage == Stage.ELEVATOR_DENIED) g.setStage(Stage.ENCOUNTER)
            "vent" -> if (g.stage == Stage.VENT_CRAWL) {
                g.say("Tight. Keep low and keep moving.", blocking = false)
            }
        }
    }

    override fun update(g: GameSession, dt: Float) {
        when (g.stage) {
            Stage.ENCOUNTER ->
                if (g.cut == Cut.NONE && g.room.id == "archive" && g.player.x >= Chapter1.ENCOUNTER_X) {
                    beginEncounter(g)
                }
            Stage.VENT_CRAWL ->
                if (g.cut == Cut.NONE && g.room.id == "vent" && g.player.x >= Chapter1.VENT_SWIPE_X) {
                    beginVentSwipe(g)
                }
        }
    }

    private fun beginEncounter(g: GameSession) {
        g.beginCut(Cut.ENCOUNTER)
        g.monster.mode = Monster.Mode.LURKING
        // Reveal it exactly where the chase route begins, so the cut and the
        // chase are continuous rather than snapping the figure across the room.
        g.monster.place("archive", Chapter1.ENCOUNTER_X - 6f, 0f, 1)
        g.camera.shake(0.18f, 0.6f)
        g.playSound(Sfx.Id.RUMBLE)
        g.say("...that's not a machine.", blocking = false)
    }

    private fun beginVentSwipe(g: GameSession) {
        g.beginCut(Cut.VENT_SWIPE)
        g.monster.mode = Monster.Mode.VENT_SWIPE
        g.monster.place("vent", g.player.x + 4.2f, 0f, -1)
        g.playSound(Sfx.Id.SCREAM, 0.8f)
        g.camera.shake(0.25f, 0.9f)
        g.onHaptic?.invoke(60)
    }

    override fun updateCut(g: GameSession, dt: Float): Boolean {
        when (g.cut) {
            Cut.INTRO -> if (!g.dialogueVisible) g.endCut()
            Cut.ENCOUNTER -> {
                if (g.cutTime > 0.9f && g.monster.mode == Monster.Mode.LURKING) {
                    g.monster.mode = Monster.Mode.ALERTED
                    g.playSound(Sfx.Id.SCREAM)
                    g.camera.shake(0.3f, 0.8f)
                    g.onHaptic?.invoke(80)
                }
                if (g.cutTime > 1.9f) {
                    g.endCut()
                    g.startChase(Stage.CHASE)
                    g.say("RUN.", blocking = false, hold = 0.6f)
                }
            }
            Cut.VENT_SWIPE -> {
                if (g.cutTime > 0.75f && !g.ventFloorBroken) {
                    g.ventFloorBroken = true
                    g.playSound(Sfx.Id.THUD)
                    g.camera.shake(0.35f, 0.7f)
                    g.particles.debris(g.player.x, g.player.y, 16, 5f, com.expstudio.facilitycore.core.Palette.TRIM)
                }
                if (g.cutTime > 1.15f) {
                    g.setCut(Cut.FALL)
                    g.monster.mode = Monster.Mode.HIDDEN
                    // Drop into the lift landing through the duct in its ceiling.
                    g.moveTo("lift", 16.4f, -8.2f)
                    g.player.vy = 4.5f
                    g.holdExits(0.6f)
                }
            }
            Cut.FALL -> {
                if (g.player.onGround || g.cutTime > 2.2f) {
                    g.camera.shake(0.22f, 0.5f)
                    g.playSound(Sfx.Id.THUD)
                    g.onHaptic?.invoke(40)
                    g.particles.debris(g.player.x, g.player.y, 12, 3.5f, com.expstudio.facilitycore.core.Palette.TRIM)
                    g.setCut(Cut.PEER)
                    g.monster.mode = Monster.Mode.PEERING
                    g.monster.place("lift", 16.4f, -8.2f, -1)
                    g.setStage(Stage.AFTER_FALL)
                    g.say("...ow. Okay. Okay.", blocking = false)
                }
            }
            Cut.PEER -> {
                if (g.cutTime > GameSession.PEER_SECONDS) {
                    g.monster.mode = Monster.Mode.HIDDEN
                    g.endCut()
                    g.setStage(Stage.ELEVATOR_READY)
                    g.say("It just... watched. Then it left.", blocking = false)
                }
            }
            else -> return false
        }
        return true
    }

    // ---- prop events -----------------------------------------------------

    override fun onBreakersSolved(g: GameSession, gateId: String) {
        g.say("Intake's live. The gate's open.", blocking = false)
    }

    override fun onKeyPackTaken(g: GameSession) {
        g.setStage(Stage.REPAIR_KEYPACK)
        g.say("A key pack. This opens everything in here.")
        g.say("Or it would, if its loom weren't torn to pieces.")
        g.queueRepairPuzzle()
    }

    override fun onCableGrabbed(g: GameSession, cable: CableCoil) {
        if (g.stage == Stage.FETCH_BIG_WIRE) {
            g.setStage(Stage.POWER_SUBFLOOR)
            g.say("Heavy. Feeder line. It has to reach the vault.", blocking = false)
        }
    }

    override fun onCableThrown(g: GameSession, station: ConnectionStation) {
        if (g.stage == Stage.CHASE && station.id == "st_chase") g.survivedChase(Stage.CHASE_SURVIVED, "shutter")
    }

    override fun onStationConnected(g: GameSession, station: ConnectionStation) {
        when (station.id) {
            "st_main" -> {
                g.subfloorPowered = true
                g.setStage(Stage.REACH_ELEVATOR)
                g.say("Subfloor zero is live.")
                g.say("Now the pack can talk to the locks down here.", blocking = false)
            }
            "st_c1", "st_c2" -> g.say("Feed's seated. There's a data shard in the return.", blocking = false)
        }
    }

    override fun onShardTaken(g: GameSession) {
        if (g.shards >= 2) {
            g.circuitAssembled = true
            g.setStage(Stage.CIRCUIT_ASSEMBLED)
            g.playSound(Sfx.Id.CHIME)
            g.say("Two halves. They lock together into one circuit.")
            g.say("The archive panel will take this.", blocking = false)
        } else {
            g.say("One shard. There's another feed on the far side.", blocking = false)
        }
    }

    override fun onUploadStarted(g: GameSession) {
        g.say("Writing the lift into the pack. Come on...", blocking = false)
    }

    override fun onUploadFinished(g: GameSession) {
        g.setStage(Stage.PANEL_UPLOADED)
    }

    override fun onPackRetrieved(g: GameSession) {
        g.keyPackHasElevator = true
        g.setStage(Stage.VENT_CRAWL)
        g.say("Got it. Subfloor 1 is in the database now.")
        g.say("The vent goes back to the lift. Faster than the long way.", blocking = false)
    }

    override fun onElevatorUse(g: GameSession, elevator: Elevator) {
        if (!g.hasKeyPack || !g.keyPackRepaired) {
            g.say("No pack, no lift.", blocking = false)
            g.playSound(Sfx.Id.DENY)
            return
        }
        if (!g.keyPackHasElevator) {
            g.playSound(Sfx.Id.DENY)
            g.say("What — it's not in the database?")
            g.say("I guess I have to add it myself.")
            g.say("There has to be an archive on this floor.", blocking = false)
            return
        }
        elevator.doorsOpen = true
        g.playSound(Sfx.Id.UNLOCK)
        g.say("Subfloor 1. Accepted.", blocking = false)
    }

    override fun onElevatorBoard(g: GameSession) {
        if (g.stage >= Stage.ENDING) return
        g.setStage(Stage.ENDING)
        g.beginCut(Cut.ENDING)
        g.player.visible = false
        g.playSound(Sfx.Id.RUMBLE)
    }

    override fun onDoorOpened(g: GameSession, door: Door) {
        when (door.name) {
            "door_main" -> if (g.stage < Stage.FETCH_BIG_WIRE) {
                g.setStage(Stage.FETCH_BIG_WIRE)
                g.say("Open. Subfloor zero.", blocking = false)
            }
            "door_power" -> if (g.stage < Stage.REACH_ELEVATOR) g.setStage(Stage.REACH_ELEVATOR)
            "door_circuit" -> if (g.stage < Stage.CIRCUIT_ROOM) {
                g.setStage(Stage.CIRCUIT_ROOM)
                g.say("Data spine. Two dead feeds and a write panel.", blocking = false)
            }
        }
    }
}
