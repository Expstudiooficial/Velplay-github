package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import kotlin.math.sin

/** One line of the player's inner monologue. */
class Line(val text: String, val blocking: Boolean = true, val hold: Float = 0f)

/** Scripted beats that take control away from the player. */
enum class Cut { NONE, INTRO, ENCOUNTER, VENT_SWIPE, FALL, PEER, ENDING, DEATH }

/**
 * Owns the running chapter: world state, the story state machine, and the
 * world-space rendering. The view above it only supplies input and time.
 */
class GameSession(
    startStage: Int,
    val puzzleSeed: Long,
    private val audio: Sfx
) {
    var level: Level = Chapter1.build()
        private set
    lateinit var room: Room
        private set

    val player = Player()
    val monster = Monster()
    val camera = Camera()

    var stage: Int = startStage
        private set
    var time = 0f
        private set

    // Story flags.
    var hasKeyPack = false
    var keyPackRepaired = false
    var keyPackHasElevator = false
    var subfloorPowered = false
    var circuitAssembled = false
    var shards = 0
    var carriedCable: CableCoil? = null
        private set

    // Presentation state read by the HUD.
    var cut = Cut.NONE
        private set
    var cutTime = 0f
        private set
    var chaseTime = 0f
        private set
    var deathFlash = 0f
        private set
    var fade = 1f
        private set
    private var fadeTarget = 0f
    var endingProgress = 0f
        private set
    var completed = false
        private set

    /** Set by the view; called whenever the stage advances so it can be saved. */
    var onStageChanged: ((Int) -> Unit)? = null
    var onChapterComplete: (() -> Unit)? = null
    var onHaptic: ((Int) -> Unit)? = null

    // Dialogue.
    private val lines = ArrayDeque<Line>()
    private var currentLine: Line? = null
    private var lineTime = 0f
    var dialogueText: String = ""
        private set
    var dialogueVisible = false
        private set
    /** True while the current line holds control; only those consume taps. */
    val dialogueBlocking: Boolean get() = currentLine?.blocking == true

    // Overlays.
    var overlay: Overlay? = null
        private set
    private var overlayDone: ((Boolean) -> Unit)? = null

    // Interaction.
    var focusProp: Prop? = null
        private set
    var focusLabel: String = ""
        private set

    private val solidScratch = ArrayList<Box>(64)
    private var exitCooldown = 0f
    private var ventFloorBroken = false
    private var pendingRespawn = false
    private var repairQueued = false

    init {
        restart(startStage)
    }

    // ---- lifecycle -------------------------------------------------------

    /** Rebuilds the world from scratch at [targetStage]'s checkpoint. */
    fun restart(targetStage: Int) {
        val checkpoint = Stage.checkpointFor(targetStage)
        level = Chapter1.build()
        stage = checkpoint
        hasKeyPack = false
        keyPackRepaired = false
        keyPackHasElevator = false
        subfloorPowered = false
        circuitAssembled = false
        shards = 0
        carriedCable = null
        ventFloorBroken = false
        pendingRespawn = false
        repairQueued = false
        cut = Cut.NONE
        cutTime = 0f
        chaseTime = 0f
        deathFlash = 0f
        endingProgress = 0f
        completed = false
        lines.clear()
        currentLine = null
        dialogueVisible = false
        overlay = null
        overlayDone = null
        player.controlEnabled = true
        player.visible = true
        player.carrying = false
        player.speedScale = 1f
        monster.mode = Monster.Mode.HIDDEN
        monster.scale = 1f

        Chapter1.applyStage(this, checkpoint)

        val (roomId, sx, sy) = Chapter1.spawnFor(checkpoint)
        room = level.room(roomId)
        player.teleport(sx, sy)
        camera.follow(player.x, player.y - 1f, room.bounds, 0f, snap = true)
        fade = 1f
        fadeTarget = 0f

        if (checkpoint == Stage.INTRO) startIntro()
        else if (checkpoint == Stage.ELEVATOR_DENIED) {
            say("Back at the lift. It still doesn't know this floor exists.", blocking = false)
        }
    }

    fun setStage(next: Int) {
        if (next <= stage) return
        stage = next
        onStageChanged?.invoke(stage)
        if (stage >= Stage.COMPLETE && !completed) {
            completed = true
            onChapterComplete?.invoke()
        }
    }

    // ---- helpers used by props -------------------------------------------

    fun playSound(id: Sfx.Id, volume: Float = 1f) = audio.play(id, volume)
    fun sfx(id: Sfx.Id) = audio.play(id)

    fun say(text: String, blocking: Boolean = true, hold: Float = 0f) {
        lines.addLast(Line(text, blocking, hold))
    }

    fun openOverlay(o: Overlay, onDone: (Boolean) -> Unit) {
        o.sfx = { id -> audio.play(id) }
        overlay = o
        overlayDone = onDone
        player.controlEnabled = false
    }

    // ---- update ----------------------------------------------------------

    fun update(dt: Float, moveX: Float, crouch: Boolean, jumpPressed: Boolean, interactPressed: Boolean) {
        time += dt
        fade = MathX.moveToward(fade, fadeTarget, dt * 1.6f)

        overlay?.let { o ->
            o.update(dt)
            if (o.finished) {
                val ok = o.succeeded
                overlay = null
                val cb = overlayDone
                overlayDone = null
                player.controlEnabled = cut == Cut.NONE
                cb?.invoke(ok)
            }
            // The world is deliberately frozen behind a puzzle.
            camera.update(dt)
            return
        }

        updateDialogue(dt)
        if (repairQueued && !dialogueVisible && overlay == null) {
            repairQueued = false
            openRepairPuzzle()
        }
        updateCutscene(dt)

        val frozen = cut != Cut.NONE || (currentLine?.blocking == true)
        player.controlEnabled = !frozen && !pendingRespawn

        collectSolids()
        player.update(dt, moveX, crouch, jumpPressed, solidScratch)

        if (player.stepEvent) {
            player.stepEvent = false
            if (!player.crouching) audio.play(Sfx.Id.THUD, 0.16f)
        }

        for (p in room.props) p.update(this, dt)
        monster.update(dt)

        if (exitCooldown > 0f) exitCooldown -= dt
        updateInteraction(interactPressed)
        if (cut == Cut.NONE) checkExits()
        updateStory()
        updateChase(dt)

        // Keep the player inside the room even if geometry ever lets them slip.
        val b = room.bounds
        player.x = MathX.clamp(player.x, b.l - 1.2f, b.r + 1.2f)
        if (player.y > b.b + 3f) {
            player.teleport(player.x, b.b)
        }

        val focusY = player.y - 1.1f
        camera.follow(player.x, focusY, room.bounds, dt)
        camera.update(dt)
    }

    private fun collectSolids() {
        solidScratch.clear()
        for (s in room.solids) {
            if (ventFloorBroken && room.id == "vent" && s.box.t >= -0.01f && s.box.h > 0.5f) continue
            solidScratch.add(s.box)
        }
        for (p in room.props) p.solid?.let { solidScratch.add(it) }
    }

    private fun updateDialogue(dt: Float) {
        if (currentLine == null && lines.isNotEmpty()) {
            currentLine = lines.removeFirst()
            lineTime = 0f
            dialogueVisible = true
        }
        val line = currentLine ?: run { dialogueVisible = false; return }
        lineTime += dt
        val chars = (lineTime / CHAR_SECONDS).toInt().coerceAtMost(line.text.length)
        dialogueText = line.text.substring(0, chars)
        val fullTime = line.text.length * CHAR_SECONDS
        val total = fullTime + (if (line.hold > 0f) line.hold else AUTO_HOLD)
        if (lineTime >= total) advanceLine()
    }

    /** Tap-to-advance: completes the typewriter first, then moves on. */
    fun tapDialogue(): Boolean {
        val line = currentLine ?: return false
        val fullTime = line.text.length * CHAR_SECONDS
        if (lineTime < fullTime) {
            lineTime = fullTime
            dialogueText = line.text
        } else {
            advanceLine()
        }
        return true
    }

    private fun advanceLine() {
        currentLine = null
        dialogueText = ""
        if (lines.isEmpty()) dialogueVisible = false
    }

    private fun updateInteraction(pressed: Boolean) {
        focusProp = null
        focusLabel = ""
        if (!player.controlEnabled) return
        var best: Prop? = null
        var bestDist = Float.MAX_VALUE
        var bestLabel = ""
        for (p in room.props) {
            val label = p.interactLabel(this)
            if (label.isEmpty()) continue
            if (!p.playerInReach(this)) continue
            val dist = p.box.distanceTo(player.x, player.y - player.height * 0.5f)
            if (dist < bestDist) {
                bestDist = dist
                best = p
                bestLabel = label
            }
        }
        focusProp = best
        focusLabel = bestLabel
        if (pressed && best != null) {
            onHaptic?.invoke(12)
            best.onInteract(this)
        }
    }

    private fun checkExits() {
        if (exitCooldown > 0f) return
        val pb = player.bounds()
        for (e in room.exits) {
            if (e.gate != null && !e.gate.open) continue
            if (!pb.overlaps(e.zone)) continue
            transition(e.toRoom, e.spawnX, e.spawnY)
            return
        }
    }

    private fun transition(roomId: String, sx: Float, sy: Float) {
        val next = level.rooms[roomId] ?: return
        // A carried coil travels with us: hand it over rather than leaving it
        // registered in both rooms' prop lists.
        val previous = room
        room = next
        player.teleport(sx, sy)
        carriedCable?.let { cable ->
            previous.props.remove(cable)
            if (!next.props.contains(cable)) next.props.add(cable)
        }
        exitCooldown = 0.4f
        camera.follow(player.x, player.y - 1.1f, room.bounds, 0f, snap = true)
        onRoomEntered(roomId)
    }

    // ---- story -----------------------------------------------------------

    private fun startIntro() {
        cut = Cut.INTRO
        cutTime = 0f
        player.controlEnabled = false
        say("The intake door sealed behind me. Of course it did.")
        say("Ren said he got trapped down here three days ago.")
        say("He's the only thing I've got left. So I'm here.")
        say("Nobody's answered a radio from this place in years.", hold = 0.4f)
    }

    private fun onRoomEntered(roomId: String) {
        when (roomId) {
            "lobby" -> if (stage < Stage.LOBBY_PUZZLE) {
                setStage(Stage.LOBBY_PUZZLE)
                say("Intake hall. Dead as everything else.", blocking = false)
            }
            "sealed" -> if (stage < Stage.FIND_KEYPACK) {
                setStage(Stage.FIND_KEYPACK)
                say("There's the way down. Sealed, naturally.", blocking = false)
            }
            "storage" -> if (stage == Stage.FIND_KEYPACK) {
                say("Storage. Somebody left in a hurry.", blocking = false)
            }
            "hub" -> if (stage < Stage.FETCH_BIG_WIRE) setStage(Stage.FETCH_BIG_WIRE)
            "power" -> if (stage == Stage.FETCH_BIG_WIRE && carriedCable == null) {
                say("The feeder's back the other way. I need to bring it here.", blocking = false)
            }
            "lift" -> {
                if (stage == Stage.REACH_ELEVATOR) {
                    setStage(Stage.ELEVATOR_DENIED)
                    say("An elevator. Subfloor 1. That's got to be where he is.", blocking = false)
                }
            }
            "archive" -> if (stage == Stage.ELEVATOR_DENIED) setStage(Stage.ENCOUNTER)
            "vent" -> if (stage == Stage.VENT_CRAWL) {
                say("Tight. Keep low and keep moving.", blocking = false)
            }
        }
    }

    private fun updateStory() {
        when (stage) {
            Stage.ENCOUNTER -> {
                if (cut == Cut.NONE && room.id == "archive" && player.x >= Chapter1.ENCOUNTER_X) {
                    beginEncounter()
                }
            }
            Stage.VENT_CRAWL -> {
                if (cut == Cut.NONE && room.id == "vent" && player.x >= Chapter1.VENT_SWIPE_X) {
                    beginVentSwipe()
                }
            }
        }
    }

    // ---- cutscenes -------------------------------------------------------

    private fun beginEncounter() {
        cut = Cut.ENCOUNTER
        cutTime = 0f
        player.controlEnabled = false
        monster.mode = Monster.Mode.LURKING
        // Reveal it exactly where the chase route begins, so the cut and the
        // chase are continuous rather than snapping the figure across the room.
        monster.place("archive", Chapter1.ENCOUNTER_X - 6f, 0f, 1)
        camera.shake(0.18f, 0.6f)
        audio.play(Sfx.Id.RUMBLE)
        say("...that's not a machine.", blocking = false)
    }

    private fun beginVentSwipe() {
        cut = Cut.VENT_SWIPE
        cutTime = 0f
        player.controlEnabled = false
        monster.mode = Monster.Mode.VENT_SWIPE
        monster.place("vent", player.x + 4.2f, 0f, -1)
        audio.play(Sfx.Id.SCREAM, 0.8f)
        camera.shake(0.25f, 0.9f)
        onHaptic?.invoke(60)
    }

    private fun updateCutscene(dt: Float) {
        if (cut == Cut.NONE) return
        cutTime += dt
        when (cut) {
            Cut.INTRO -> {
                // Hand control back as soon as the monologue is done.
                if (lines.isEmpty() && currentLine == null) {
                    cut = Cut.NONE
                    player.controlEnabled = true
                }
            }
            Cut.ENCOUNTER -> {
                if (cutTime > 0.9f && monster.mode == Monster.Mode.LURKING) {
                    monster.mode = Monster.Mode.ALERTED
                    audio.play(Sfx.Id.SCREAM)
                    camera.shake(0.3f, 0.8f)
                    onHaptic?.invoke(80)
                }
                if (cutTime > 1.9f) {
                    cut = Cut.NONE
                    startChase()
                }
            }
            Cut.VENT_SWIPE -> {
                if (cutTime > 0.75f && !ventFloorBroken) {
                    ventFloorBroken = true
                    audio.play(Sfx.Id.THUD)
                    camera.shake(0.35f, 0.7f)
                }
                if (cutTime > 1.15f) {
                    cut = Cut.FALL
                    cutTime = 0f
                    monster.mode = Monster.Mode.HIDDEN
                    // Drop into the lift landing through the duct in its ceiling.
                    room = level.room("lift")
                    player.teleport(16.4f, -8.2f)
                    player.vy = 4.5f
                    camera.follow(player.x, player.y, room.bounds, 0f, snap = true)
                    exitCooldown = 0.6f
                }
            }
            Cut.FALL -> {
                if (player.onGround || cutTime > 2.2f) {
                    camera.shake(0.22f, 0.5f)
                    audio.play(Sfx.Id.THUD)
                    onHaptic?.invoke(40)
                    cut = Cut.PEER
                    cutTime = 0f
                    monster.mode = Monster.Mode.PEERING
                    monster.place("lift", 16.4f, -8.2f, -1)
                    setStage(Stage.AFTER_FALL)
                    say("...ow. Okay. Okay.", blocking = false)
                }
            }
            Cut.PEER -> {
                if (cutTime > PEER_SECONDS) {
                    monster.mode = Monster.Mode.HIDDEN
                    cut = Cut.NONE
                    player.controlEnabled = true
                    setStage(Stage.ELEVATOR_READY)
                    say("It just... watched. Then it left.", blocking = false)
                }
            }
            Cut.ENDING -> {
                val before = endingProgress
                endingProgress += dt / ENDING_SECONDS
                if (before < LANDING_CUE && endingProgress >= LANDING_CUE) {
                    audio.play(Sfx.Id.THUD)
                    audio.play(Sfx.Id.SCREAM)
                    onHaptic?.invoke(160)
                }
                if (endingProgress >= 1f) {
                    endingProgress = 1f
                    setStage(Stage.COMPLETE)
                }
            }
            Cut.DEATH -> {
                deathFlash = (1f - cutTime / DEATH_SECONDS).coerceIn(0f, 1f)
                if (cutTime > DEATH_SECONDS) {
                    cut = Cut.NONE
                    restart(Stage.checkpointFor(stage))
                }
            }
            else -> {}
        }
    }

    // ---- chase -----------------------------------------------------------

    private fun startChase() {
        setStage(Stage.CHASE)
        chaseTime = 0f
        monster.mode = Monster.Mode.CHASING
        monster.resetChase()
        player.speedScale = CHASE_SPEED_BOOST
        say("RUN.", blocking = false, hold = 0.6f)
    }

    private fun updateChase(dt: Float) {
        if (stage != Stage.CHASE || cut != Cut.NONE) return
        chaseTime += dt
        monster.advanceChase(dt, Chapter1.CHASE_SECONDS)
        camera.shake(0.03f + 0.05f * (chaseTime / Chapter1.CHASE_SECONDS), 0.1f)
        if (monster.wantsScream()) audio.play(Sfx.Id.SCREAM, 0.55f)

        val caught = chaseTime >= Chapter1.CHASE_SECONDS ||
            (monster.roomId == room.id && monster.touching(player))
        if (caught) die()
    }

    private fun survivedChase() {
        setStage(Stage.CHASE_SURVIVED)
        player.speedScale = 1f
        monster.mode = Monster.Mode.HIDDEN
        (level.room("endchase").props.firstOrNull { it is Door && it.name == "shutter" } as? Door)?.let {
            it.forceClose()
        }
        audio.play(Sfx.Id.RUMBLE)
        camera.shake(0.3f, 0.8f)
        onHaptic?.invoke(50)
        say("The bulkhead. It's on the other side of it.")
        say("I can still hear it. Move.", blocking = false)
    }

    private fun die() {
        if (cut == Cut.DEATH) return
        cut = Cut.DEATH
        cutTime = 0f
        deathFlash = 1f
        pendingRespawn = true
        player.controlEnabled = false
        player.speedScale = 1f
        audio.play(Sfx.Id.SCREAM)
        camera.shake(0.6f, 0.5f)
        onHaptic?.invoke(200)
    }

    // ---- prop callbacks --------------------------------------------------

    fun onBreakersSolved(gateId: String) {
        val gate = room.props.firstOrNull { it is Door && it.name == gateId } as? Door
        gate?.locked = false
        gate?.forceOpen()
        audio.play(Sfx.Id.POWER)
        camera.shake(0.12f, 0.5f)
        say("Intake's live. The gate's open.", blocking = false)
    }

    fun onKeyPackTaken() {
        hasKeyPack = true
        audio.play(Sfx.Id.PICKUP)
        setStage(Stage.REPAIR_KEYPACK)
        say("A key pack. This opens everything in here.")
        say("Or it would, if its loom weren't torn to pieces.")
        // Let the monologue finish before the puzzle covers the screen.
        repairQueued = true
    }

    /** Also reachable from the HUD icon, so a closed puzzle is never a dead end. */
    fun openRepairPuzzle() {
        if (!hasKeyPack || keyPackRepaired || overlay != null) return
        openOverlay(WiringPuzzle(puzzleSeed + 17)) { ok ->
            if (ok) {
                keyPackRepaired = true
                setStage(Stage.UNLOCK_FIRST_DOOR)
                audio.play(Sfx.Id.POWER, 0.6f)
                say("There. It's reading again.", blocking = false)
            }
        }
    }

    fun grabCable(cable: CableCoil) {
        if (carriedCable != null) return
        carriedCable = cable
        cable.held = true
        player.carrying = true
        audio.play(Sfx.Id.PICKUP)
        if (stage == Stage.FETCH_BIG_WIRE) {
            setStage(Stage.POWER_SUBFLOOR)
            say("Heavy. Feeder line. It has to reach the vault.", blocking = false)
        }
    }

    fun onCableThrown(station: ConnectionStation) {
        val cable = carriedCable ?: return
        cable.held = false
        cable.consumed = true
        carriedCable = null
        player.carrying = false
        audio.play(Sfx.Id.CONNECT)
        // Surviving is credited on the throw, not on the animation finishing:
        // the player earned it the moment the cable left their hands.
        if (stage == Stage.CHASE && station.id == "st_chase") survivedChase()
    }

    fun onStationConnected(station: ConnectionStation) {
        audio.play(Sfx.Id.POWER)
        camera.shake(0.15f, 0.6f)
        when (station.id) {
            "st_main" -> {
                subfloorPowered = true
                setStage(Stage.REACH_ELEVATOR)
                say("Subfloor zero is live.")
                say("Now the pack can talk to the locks down here.", blocking = false)
            }
            "st_chase" -> { /* handled on throw */ }
            "st_c1", "st_c2" -> say("Feed's seated. There's a data shard in the return.", blocking = false)
        }
    }

    fun onShardTaken() {
        shards++
        audio.play(Sfx.Id.PICKUP)
        if (shards >= 2) {
            circuitAssembled = true
            setStage(Stage.CIRCUIT_ASSEMBLED)
            audio.play(Sfx.Id.CHIME)
            say("Two halves. They lock together into one circuit.")
            say("The archive panel will take this.", blocking = false)
        } else {
            say("One shard. There's another feed on the far side.", blocking = false)
        }
    }

    fun onUploadStarted() {
        audio.play(Sfx.Id.CHIME)
        say("Writing the lift into the pack. Come on...", blocking = false)
    }

    fun onUploadFinished() {
        audio.play(Sfx.Id.CONFIRM)
        camera.shake(0.1f, 0.4f)
        setStage(Stage.PANEL_UPLOADED)
    }

    fun onPackRetrieved() {
        keyPackHasElevator = true
        audio.play(Sfx.Id.PICKUP)
        setStage(Stage.VENT_CRAWL)
        say("Got it. Subfloor 1 is in the database now.")
        say("The vent goes back to the lift. Faster than the long way.", blocking = false)
    }

    fun onElevatorUse(elevator: Elevator) {
        if (!hasKeyPack || !keyPackRepaired) {
            say("No pack, no lift.", blocking = false)
            audio.play(Sfx.Id.DENY)
            return
        }
        if (!keyPackHasElevator) {
            audio.play(Sfx.Id.DENY)
            say("What — it's not in the database?")
            say("I guess I have to add it myself.")
            say("There has to be an archive on this floor.", blocking = false)
            return
        }
        elevator.doorsOpen = true
        audio.play(Sfx.Id.UNLOCK)
        say("Subfloor 1. Accepted.", blocking = false)
    }

    fun onElevatorBoard() {
        if (stage >= Stage.ENDING) return
        setStage(Stage.ENDING)
        cut = Cut.ENDING
        cutTime = 0f
        endingProgress = 0f
        player.controlEnabled = false
        player.visible = false
        audio.play(Sfx.Id.RUMBLE)
    }

    fun onDoorOpened(door: Door) {
        when (door.name) {
            "door_main" -> if (stage < Stage.FETCH_BIG_WIRE) {
                setStage(Stage.FETCH_BIG_WIRE)
                say("Open. Subfloor zero.", blocking = false)
            }
            "door_power" -> if (stage < Stage.REACH_ELEVATOR) setStage(Stage.REACH_ELEVATOR)
            "door_circuit" -> if (stage < Stage.CIRCUIT_ROOM) {
                setStage(Stage.CIRCUIT_ROOM)
                say("Data spine. Two dead feeds and a write panel.", blocking = false)
            }
        }
    }

    // ---- rendering -------------------------------------------------------

    private val scenery = HashMap<String, Scenery>()

    private fun sceneryFor(r: Room): Scenery = scenery.getOrPut(r.id) { Scenery(r) }

    /** 0 = pitch dark room, 1 = fully lit. Drives dust, sheen and light cones. */
    private fun ambient(): Float = if (room.needsPower && !subfloorPowered) 0.18f else 1f

    fun render(c: Canvas, d: Draw, widthPx: Float, heightPx: Float) {
        val sc = sceneryFor(room)
        val amb = ambient()

        drawSky(c, d, widthPx, heightPx)
        sc.drawFar(c, d, camera)
        sc.drawMid(c, d, camera)
        sc.drawNear(c, d, camera)

        for (dec in room.decor) drawDecor(c, d, dec)
        for (s in room.solids) drawSolid(c, d, s)
        drawFloorSheen(c, d, amb)
        drawLightCones(c, d)

        for (p in room.props) if (camera.isVisible(p.box.inflated(3f))) p.draw(c, d, camera, this)

        if (monster.mode != Monster.Mode.HIDDEN && monster.roomId == room.id) {
            if (monster.mode == Monster.Mode.PEERING) drawPeering(c, d)
            else if (monster.mode == Monster.Mode.VENT_SWIPE) drawVentSwipe(c, d)
            else monster.draw(c, d, camera, time)
        }

        player.draw(c, d, camera, time)
        carriedCable?.let { drawCarriedCable(c, d, it) }

        sc.drawDust(c, d, camera, time, player.x, player.y - 1f, 0.35f + amb * 0.65f)
        drawLighting(c, d, widthPx, heightPx)
    }

    /**
     * The single background pass. It carries the deep vertical wash, the
     * separation between parallax and playfield, and the cool grade all at
     * once: each of those used to be its own full-screen fill.
     */
    private fun drawSky(c: Canvas, d: Draw, w: Float, h: Float) {
        d.vGradient(
            c, 0f, 0f, w, h,
            Palette.mix(Palette.mix(Palette.BG_FAR, Palette.VOID, 0.42f), Palette.ACCENT_DIM, 0.10f),
            Palette.mix(Palette.mix(Palette.BG_FAR, Palette.BG_NEAR, 0.30f), Palette.ACCENT_DIM, 0.06f)
        )
    }

    private fun drawDecor(c: Canvas, d: Draw, dec: Decor) {
        if (!camera.isVisible(dec.box, 1.5f)) return
        val l = camera.sx(dec.box.l); val r = camera.sx(dec.box.r)
        val t = camera.sy(dec.box.t); val b = camera.sy(dec.box.b)
        when (dec.kind) {
            Decor.Kind.LIGHT -> {
                val powered = dec.lit || subfloorPowered || !room.needsPower
                // An unpowered fixture still runs on the emergency bus: a dim
                // red ember. A pitch black room the player cannot read is not
                // atmosphere, it is a wall.
                val tint = if (powered) dec.color else Palette.BAD
                val flicker = if (powered) 0.78f + 0.22f * sin(time * 9.3f + dec.box.l)
                else 0.30f + 0.16f * sin(time * 1.7f + dec.box.l)
                d.surface(
                    c, l, t, r, b,
                    Palette.mix(Palette.WALL_LIT, tint, if (powered) 0.75f else 0.30f),
                    Palette.mix(Palette.WALL, Palette.VOID, 0.3f),
                    Palette.withAlpha(tint, flicker),
                    MathX.min(camera.s(0.05f), 3f)
                )
                d.glow(c, (l + r) * 0.5f, b, camera.s(if (powered) 2.0f else 1.1f), tint, 0.72f * flicker)
                d.circle(c, (l + r) * 0.5f, b - camera.s(0.05f), camera.s(0.10f), Palette.withAlpha(tint, flicker))
            }
            Decor.Kind.GRATE -> {
                d.vGradient(c, l, t, r, b, Palette.mix(dec.color, Palette.WALL_LIT, 0.4f),
                    Palette.mix(dec.color, Palette.VOID, 0.45f))
                var x = l
                val step = MathX.min(camera.s(0.16f), 24f)
                while (x < r && step > 1.5f) {
                    d.line(c, x, t, x, b, Palette.withAlpha(Palette.VOID, 0.55f), 2f)
                    x += step
                }
                d.rect(c, l, t, r, t + 2f, Palette.withAlpha(Palette.TRIM, 0.5f))
            }
            Decor.Kind.PIPE -> {
                d.vGradient(c, l, t, r, b, Palette.mix(dec.color, Palette.TEXT_DIM, 0.35f),
                    Palette.mix(dec.color, Palette.VOID, 0.5f))
                d.rect(c, l, t, r, t + MathX.min(camera.s(0.05f), 3f), Palette.withAlpha(Palette.TEXT_DIM, 0.35f))
            }
            Decor.Kind.STRIPE -> {
                d.vGradient(c, l, t, r, b, Palette.mix(dec.color, Palette.TRIM, 0.35f),
                    Palette.mix(dec.color, Palette.VOID, 0.4f))
            }
            else -> d.vGradient(c, l, t, r, b, dec.color, Palette.mix(dec.color, Palette.VOID, 0.4f))
        }
    }

    private fun drawSolid(c: Canvas, d: Draw, s: Solid) {
        if (!camera.isVisible(s.box, 1.5f)) return
        val l = camera.sx(s.box.l); val r = camera.sx(s.box.r)
        val t = camera.sy(s.box.t); val b = camera.sy(s.box.b)
        val lip = MathX.min(camera.s(0.07f), 5f)
        when (s.kind) {
            Solid.Kind.CRATE -> {
                d.surface(
                    c, l, t, r, b,
                    Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.30f),
                    Palette.mix(Palette.WALL, Palette.VOID, 0.45f),
                    Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.35f),
                    lip, occlusionPx = camera.s(0.30f)
                )
                // Banding so a crate never reads as a plain block.
                val band = t + (b - t) * 0.34f
                d.rect(c, l + lip, band, r - lip, band + lip * 0.8f, Palette.withAlpha(Palette.VOID, 0.35f))
                d.rect(c, l + lip, band + lip * 0.8f, r - lip, band + lip * 1.3f,
                    Palette.withAlpha(Palette.TRIM, 0.25f))
            }
            Solid.Kind.PLATFORM -> {
                d.surface(
                    c, l, t, r, b,
                    Palette.mix(Palette.FLOOR_EDGE, Palette.TRIM, 0.45f),
                    Palette.mix(Palette.FLOOR, Palette.VOID, 0.35f),
                    Palette.mix(Palette.TRIM, Palette.ACCENT_DIM, 0.30f),
                    lip
                )
                // Under-lit edge so a thin ledge is obvious against a dark wall.
                d.rect(c, l, b, r, b + lip * 0.7f, Palette.withAlpha(Palette.VOID, 0.5f))
            }
            else -> {
                d.surface(
                    c, l, t, r, b,
                    Palette.mix(Palette.WALL, Palette.WALL_LIT, 0.35f),
                    Palette.mix(Palette.WALL, Palette.VOID, 0.55f),
                    Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.30f),
                    lip
                )
                // Panel seams across large structure.
                val step = MathX.min(camera.s(1.6f), 220f)
                if (step > 12f && (r - l) > step) {
                    var x = l + step
                    while (x < r) {
                        d.line(c, x, t, x, b, Palette.withAlpha(Palette.VOID, 0.30f), 2f)
                        x += step
                    }
                }
            }
        }
    }

    /** A long specular smear along the floor line; cheap, and sells the depth. */
    private fun drawFloorSheen(c: Canvas, d: Draw, amb: Float) {
        val floorY = camera.sy(room.bounds.b)
        val height = camera.s(0.9f)
        d.vGradient(
            c, 0f, floorY - height, camera.viewW * camera.scale, floorY,
            Palette.withAlpha(Palette.ACCENT, 0f),
            Palette.withAlpha(Palette.ACCENT, 0.055f * amb)
        )
        d.rect(c, 0f, floorY - 2f, camera.viewW * camera.scale, floorY,
            Palette.withAlpha(Palette.TRIM, 0.35f * amb))
    }

    /** Volumetric shafts under every fixture that is actually burning. */
    private fun drawLightCones(c: Canvas, d: Draw) {
        for (dec in room.decor) {
            if (dec.kind != Decor.Kind.LIGHT) continue
            if (!camera.isVisible(dec.box, 6f)) continue
            val powered = dec.lit || subfloorPowered || !room.needsPower
            val tint = if (powered) dec.color else Palette.BAD
            val flicker = if (powered) 0.8f + 0.2f * sin(time * 9.3f + dec.box.l) else 0.45f
            val drop = (room.bounds.b - dec.box.b).coerceAtLeast(0.5f)
            d.lightCone(
                c,
                camera.sx(dec.box.cx), camera.sy(dec.box.b),
                camera.s(dec.box.w * 0.45f), camera.s(dec.box.w * 1.9f),
                camera.s(drop), tint,
                (if (powered) 1f else 0.55f) * flicker
            )
        }
    }

    private fun drawCarriedCable(c: Canvas, d: Draw, cable: CableCoil) {
        // Slack line from the player's shoulder to the coil in their hands.
        val sx = camera.sx(player.x)
        val sy = camera.sy(player.y - player.height * 0.75f)
        val cx = camera.sx(cable.box.cx)
        val cy = camera.sy(cable.box.cy)
        d.line(c, sx, sy, cx, cy + camera.s(0.18f), Palette.withAlpha(Palette.VOID, 0.5f), camera.s(0.09f))
        d.line(c, sx, sy, cx, cy + camera.s(0.18f), Palette.withAlpha(Palette.WARN, 0.8f), camera.s(0.06f))
    }

    private fun drawPeering(c: Canvas, d: Draw) {
        // Two eyes in the ceiling duct, nothing else.
        val cx = camera.sx(16.4f)
        val cy = camera.sy(-8.8f)
        val fade = MathX.clamp(1f - (cutTime - (PEER_SECONDS - 0.5f)) / 0.5f, 0f, 1f)
        val gap = camera.s(0.17f)
        d.glow(c, cx, cy, camera.s(0.9f), Palette.MONSTER_CRACK, 0.5f * fade)
        d.glow(c, cx - gap, cy, camera.s(0.30f), Palette.MONSTER_EYE, 0.9f * fade)
        d.glow(c, cx + gap, cy, camera.s(0.30f), Palette.MONSTER_EYE, 0.9f * fade)
        d.circle(c, cx - gap, cy, camera.s(0.055f), Palette.withAlpha(Palette.MONSTER_EYE, fade))
        d.circle(c, cx + gap, cy, camera.s(0.055f), Palette.withAlpha(Palette.MONSTER_EYE, fade))
    }

    private fun drawVentSwipe(c: Canvas, d: Draw) {
        val reach = MathX.smoothStep((cutTime / 0.7f).coerceIn(0f, 1f))
        val fromX = camera.sx(monster.x)
        val toX = camera.sx(player.x + 0.2f)
        val y = camera.sy(-0.55f)
        val handX = MathX.lerp(fromX, toX, reach)
        d.glow(c, fromX, y, camera.s(1.1f), Palette.MONSTER_CRACK, 0.7f)
        d.glow(c, fromX, y, camera.s(0.6f), Palette.MONSTER_EYE, 0.8f)
        d.line(c, fromX, y, handX, y + camera.s(0.1f), Palette.MONSTER, camera.s(0.22f))
        d.circle(c, handX, y + camera.s(0.1f), camera.s(0.20f), Palette.MONSTER)
        for (i in -1..1) {
            d.line(c, handX, y + camera.s(0.1f), handX - camera.s(0.35f), y + camera.s(0.1f + i * 0.16f),
                Palette.MONSTER, camera.s(0.06f))
        }
        d.circle(c, fromX + camera.s(0.2f), y - camera.s(0.25f), camera.s(0.05f), Palette.MONSTER_EYE)
        d.circle(c, fromX + camera.s(0.38f), y - camera.s(0.25f), camera.s(0.05f), Palette.MONSTER_EYE)
    }

    private fun drawLighting(c: Canvas, d: Draw, w: Float, h: Float) {
        if (room.needsPower && !subfloorPowered) {
            val cx = camera.sx(player.x)
            val cy = camera.sy(player.y - player.height * 0.5f)
            // Wide and soft. The old pool was so tight the room read as a black
            // screen, which made judging a jump impossible.
            d.darkness(
                c, w, h, cx, cy, camera.s(10f),
                Palette.mix(Palette.VOID, Palette.BG_NEAR, 0.30f), depth = 0.86f
            )
            // A soft carry-light around the figure so the metre in front of the
            // player — the metre they have to judge a jump from — is readable.
            d.glow(c, cx, cy, camera.s(5.0f), Palette.mix(Palette.ACCENT, Palette.TEXT, 0.45f), 0.34f)
        }
        val chaseHeat = if (stage == Stage.CHASE) (chaseTime / Chapter1.CHASE_SECONDS).coerceIn(0f, 1f) else 0f
        d.vignette(
            c, w, h, 0.34f + chaseHeat * 0.34f,
            if (chaseHeat > 0f) Palette.mix(Palette.VOID, Palette.BAD, chaseHeat * 0.45f) else Palette.VOID
        )
    }

    companion object {
        const val CHAR_SECONDS = 0.022f
        const val AUTO_HOLD = 1.9f
        const val PEER_SECONDS = 2.0f
        const val ENDING_SECONDS = 7.5f
        const val DEATH_SECONDS = 1.9f
        const val CHASE_SPEED_BOOST = 1.15f
        /** Progress at which the thing lands on the car's roof. */
        const val LANDING_CUE = 0.60f
    }
}
