package com.expstudio.facilitycore.game

import android.graphics.Canvas
import com.expstudio.facilitycore.audio.Sfx
import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.core.Art
import com.expstudio.facilitycore.core.Camera
import com.expstudio.facilitycore.core.Draw
import com.expstudio.facilitycore.core.MathX
import com.expstudio.facilitycore.core.Palette
import com.expstudio.facilitycore.core.Particles
import kotlin.math.abs
import kotlin.math.sin

/** One line of the player's inner monologue. */
class Line(val text: String, val blocking: Boolean = true, val hold: Float = 0f)

/** Scripted beats that take control away from the player. */
enum class Cut {
    NONE, INTRO, ENCOUNTER, VENT_SWIPE, FALL, PEER, ENDING, DEATH,
    // Chapter 2.
    CH2_OPENING, CH2_FIGHT_INTRO, DOOR_BREAK, GRABBED, HOISTED, SMELTER_END
}

/**
 * Owns the running chapter: world state, the story state machine, and the
 * world-space rendering. The view above it only supplies input and time.
 */
class GameSession(
    val chapterNumber: Int,
    startStage: Int,
    val puzzleSeed: Long,
    private val audio: Sfx
) {
    /** The chapter being played. Owns every beat that is not shared. */
    val script: ChapterScript = ChapterScript.forChapter(chapterNumber)

    var level: Level = script.build()
        private set
    lateinit var room: Room
        private set

    val player = Player()
    val monster = Monster()
    val camera = Camera()
    val particles = Particles()

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
    /** Chapter 2's battery cube, carried the same way a coil is. */
    var carriedCube: PowerCube? = null

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
    /** Progress through a Chapter 2 cutscene, driven by that chapter's script. */
    var endingProgressCh2 = 0f

    /** Health, used by set-piece fights. maxHealth of 0 hides the pips. */
    var maxHealth = 0
    var health = 0
    /** Flashes red after a hit and grants a moment of mercy. */
    var hurtFlash = 0f
        private set
    private var invulnerable = 0f

    /** Whether the DODGE control is available in this chapter. */
    var dodgeUnlocked = false
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

    val solidScratch = ArrayList<Box>(64)
    private var exitCooldown = 0f
    private var blockedTime = 0f
    /** Set by the script when the return vent's floor gives way. */
    var ventFloorBroken = false

    /**
     * Set while the player is being stopped by something they could fit under
     * if they crouched. Without this the collapsed bulkhead reads as a dead end:
     * you run into it, jump at it because that is what you do, and die to the
     * chase timer never learning that SNEAK was the answer.
     */
    var crouchHint = false
        private set
    private var pendingRespawn = false
    private var repairQueued = false

    init {
        restart(startStage)
    }

    // ---- lifecycle -------------------------------------------------------

    /** Rebuilds the world from scratch at [targetStage]'s checkpoint. */
    fun restart(targetStage: Int) {
        val checkpoint = script.checkpointFor(targetStage)
        level = script.build()
        stage = checkpoint
        hasKeyPack = false
        keyPackRepaired = false
        keyPackHasElevator = false
        subfloorPowered = false
        circuitAssembled = false
        shards = 0
        carriedCable = null
        carriedCube = null
        ventFloorBroken = false
        pendingRespawn = false
        repairQueued = false
        blockedTime = 0f
        crouchHint = false
        cut = Cut.NONE
        cutTime = 0f
        chaseTime = 0f
        deathFlash = 0f
        endingProgress = 0f
        endingProgressCh2 = 0f
        hurtFlash = 0f
        invulnerable = 0f
        maxHealth = 0
        health = 0
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
        particles.clear()

        script.applyStage(this, checkpoint)

        val (roomId, sx, sy) = script.spawnFor(checkpoint)
        room = level.room(roomId)
        player.teleport(sx, sy)
        camera.follow(player.x, player.y - 1f, room.bounds, 0f, snap = true)
        fade = 1f
        fadeTarget = 0f

        script.onStart(this, checkpoint)
    }

    fun setStage(next: Int) {
        if (next <= stage) return
        stage = next
        onStageChanged?.invoke(stage)
        if (stage >= script.completeStage && !completed) {
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

    /** Starts a cutscene: freezes the player and resets the beat clock. */
    fun beginCut(next: Cut) {
        cut = next
        cutTime = 0f
        player.controlEnabled = false
    }

    /** Moves to the next beat of a running cutscene. */
    fun setCut(next: Cut) {
        cut = next
        cutTime = 0f
    }

    fun endCut() {
        cut = Cut.NONE
        cutTime = 0f
        player.controlEnabled = true
    }

    /** Teleports between rooms outside the normal exit flow, for cutscenes. */
    fun moveTo(roomId: String, x: Float, y: Float) {
        val next = level.rooms[roomId] ?: return
        room = next
        player.teleport(x, y)
        camera.follow(x, y - 1.1f, room.bounds, 0f, snap = true)
    }

    fun holdExits(seconds: Float) { exitCooldown = seconds }

    /** Opens the key pack repair once the current monologue has finished. */
    fun queueRepairPuzzle() { repairQueued = true }

    fun openOverlay(o: Overlay, onDone: (Boolean) -> Unit) {
        o.sfx = { id -> audio.play(id) }
        overlay = o
        overlayDone = onDone
        player.controlEnabled = false
    }

    // ---- update ----------------------------------------------------------

    fun update(
        dt: Float,
        moveX: Float,
        crouch: Boolean,
        jumpPressed: Boolean,
        interactPressed: Boolean,
        dodgePressed: Boolean = false
    ) {
        time += dt
        if (hurtFlash > 0f) hurtFlash -= dt
        if (invulnerable > 0f) invulnerable -= dt
        if (dodgePressed && dodgeUnlocked) player.startDodge(moveX)
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
        particles.update(dt)
        player.hasPack = hasKeyPack

        if (exitCooldown > 0f) exitCooldown -= dt
        updateCrouchHint(dt, moveX, crouch)
        updateInteraction(interactPressed)
        if (cut == Cut.NONE) checkExits()
        updateStory(dt)
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

    /** Decides whether to tell the player that SNEAK would get them through. */
    private fun updateCrouchHint(dt: Float, moveX: Float, crouching: Boolean) {
        val pushing = abs(moveX) > 0.2f && player.controlEnabled
        val blocked = pushing && abs(player.vx) < 0.5f && player.onGround
        if (!blocked || crouching || player.crouching) {
            blockedTime = 0f
            crouchHint = false
            return
        }
        blockedTime += dt
        if (blockedTime < HINT_DELAY) {
            crouchHint = false
            return
        }
        // Probe just ahead: standing does not fit, crouching does.
        val dir = if (moveX > 0f) 1f else -1f
        val probeX = player.x + dir * 0.55f
        crouchHint = !player.canFit(probeX, player.y, Player.STAND_HEIGHT, solidScratch) &&
            player.canFit(probeX, player.y, Player.CROUCH_HEIGHT, solidScratch)
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

    private fun onRoomEntered(roomId: String) = script.onRoomEntered(this, roomId)

    private fun updateStory(dt: Float) = script.update(this, dt)

    // ---- cutscenes -------------------------------------------------------

    private fun updateCutscene(dt: Float) {
        if (cut == Cut.NONE) return
        cutTime += dt
        // The chapter drives its own beats; only the two shared ones live here.
        if (script.updateCut(this, dt)) return
        when (cut) {
            Cut.ENDING -> {
                endingProgress += dt / ENDING_SECONDS
                if (endingProgress >= 1f) {
                    endingProgress = 1f
                    setStage(script.completeStage)
                }
            }
            Cut.DEATH -> {
                deathFlash = (1f - cutTime / DEATH_SECONDS).coerceIn(0f, 1f)
                if (cutTime > DEATH_SECONDS) {
                    cut = Cut.NONE
                    val target = if (retryStageOverride >= 0) retryStageOverride else stage
                    retryStageOverride = -1
                    restart(script.checkpointFor(target))
                }
            }
            else -> {}
        }
    }

    // ---- chase -----------------------------------------------------------

    /**
     * A hit from a set-piece attacker. Costs health, knocks the player back, and
     * kills once there is none left. A short mercy window follows so a single
     * swing cannot take two.
     */
    fun takeHit(fromFacing: Int, retryStage: Int) {
        if (invulnerable > 0f || cut != Cut.NONE) return
        invulnerable = HIT_MERCY
        hurtFlash = 0.45f
        camera.shake(0.4f, 0.5f)
        audio.play(Sfx.Id.THUD)
        onHaptic?.invoke(90)
        particles.sparkBurst(player.x, player.y - player.height * 0.5f, 16, 1.6f, 5f, Palette.BAD)
        player.vx = fromFacing * 7.5f
        player.vy = -3.5f
        health--
        if (health <= 0) {
            retryStageOverride = retryStage
            die()
        } else {
            say("Agh —", blocking = false, hold = 0.2f)
        }
    }

    /** Kills the player outright; used by timers the chapter owns. */
    fun killPlayer() = die()

    /** Ends a pursuit without the player having earned it. */
    fun stopChase() {
        if (stage == chaseStageValue) {
            monster.mode = Monster.Mode.HIDDEN
            player.speedScale = 1f
        }
    }

    /** Set when a set-piece wants death to retry somewhere specific. */
    private var retryStageOverride = -1

    /** Begins a timed pursuit; [chaseStage] is the chapter's own chase stage. */
    fun startChase(chaseStage: Int) {
        setStage(chaseStage)
        chaseStageValue = chaseStage
        chaseTime = 0f
        monster.mode = Monster.Mode.CHASING
        monster.resetChase()
        player.speedScale = CHASE_SPEED_BOOST
    }

    /** The stage value that means "being chased" in the running chapter. */
    var chaseStageValue = Stage.CHASE
        private set

    private fun updateChase(dt: Float) {
        if (stage != chaseStageValue || cut != Cut.NONE) return
        chaseTime += dt
        // The clock is the deadline, but the pursuit is anchored to the player:
        // a purely time-driven monster on a long budget crawls along at under
        // 2 m/s and is never seen, which is not a chase.
        val timeProgress = chaseTime / script.chaseSeconds
        val playerProgress = monster.progressAt(room.id, player.x)
        val lead = MathX.lerp(CHASE_LEAD_START, CHASE_LEAD_END, timeProgress) / monster.routeLength
        val anchored = if (playerProgress != null) playerProgress - lead else 0f
        monster.setChaseProgress(kotlin.math.max(timeProgress, anchored))
        camera.shake(0.03f + 0.05f * (chaseTime / script.chaseSeconds), 0.1f)
        if (monster.wantsScream()) audio.play(Sfx.Id.SCREAM, 0.55f)

        val caught = chaseTime >= script.chaseSeconds ||
            (monster.roomId == room.id && monster.touching(player))
        if (caught) die()
    }

    /** Ends a pursuit and drops the named shutter behind the player. */
    fun survivedChase(survivedStage: Int, shutterName: String) {
        setStage(survivedStage)
        player.speedScale = 1f
        monster.mode = Monster.Mode.HIDDEN
        for (r in level.rooms.values) {
            (r.props.firstOrNull { it is Door && it.name == shutterName } as? Door)?.forceClose()
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
    //
    // Each of these does the part every chapter shares — sound, particles, the
    // flag on the session — and hands the story decision to the script.

    fun onBreakersSolved(gateId: String) {
        val gate = room.props.firstOrNull { it is Door && it.name == gateId } as? Door
        gate?.locked = false
        gate?.forceOpen()
        audio.play(Sfx.Id.POWER)
        camera.shake(0.12f, 0.5f)
        particles.sparkBurst(gate?.box?.cx ?: player.x, gate?.box?.b ?: player.y, 14, 1.2f, 5f)
        script.onBreakersSolved(this, gateId)
    }

    fun onKeyPackTaken() {
        hasKeyPack = true
        audio.play(Sfx.Id.PICKUP)
        script.onKeyPackTaken(this)
    }

    /** Also reachable from the HUD icon, so a closed puzzle is never a dead end. */
    fun openRepairPuzzle() {
        if (!hasKeyPack || keyPackRepaired || overlay != null) return
        openOverlay(WiringPuzzle(puzzleSeed + 17)) { ok ->
            if (ok) {
                keyPackRepaired = true
                if (stage < Stage.UNLOCK_FIRST_DOOR && chapterNumber == 1) setStage(Stage.UNLOCK_FIRST_DOOR)
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
        script.onCableGrabbed(this, cable)
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
        script.onCableThrown(this, station)
    }

    fun onStationConnected(station: ConnectionStation) {
        audio.play(Sfx.Id.POWER)
        camera.shake(0.15f, 0.6f)
        particles.sparkBurst(station.box.cx, station.box.b, 26, 1.1f, 7f)
        particles.embers(station.box.cx, station.box.b, 6)
        script.onStationConnected(this, station)
    }

    fun onShardTaken() {
        shards++
        audio.play(Sfx.Id.PICKUP)
        script.onShardTaken(this)
    }

    fun onUploadStarted() {
        audio.play(Sfx.Id.CHIME)
        script.onUploadStarted(this)
    }

    fun onUploadFinished() {
        audio.play(Sfx.Id.CONFIRM)
        camera.shake(0.1f, 0.4f)
        script.onUploadFinished(this)
    }

    fun onPackRetrieved() {
        audio.play(Sfx.Id.PICKUP)
        script.onPackRetrieved(this)
    }

    fun onElevatorUse(elevator: Elevator) = script.onElevatorUse(this, elevator)

    fun onElevatorBoard() = script.onElevatorBoard(this)

    fun onDoorOpened(door: Door) = script.onDoorOpened(this, door)

    fun onCubeTaken(cube: PowerCube) {
        carriedCube = cube
        audio.play(Sfx.Id.PICKUP)
        script.onCubeTaken(this, cube)
    }

    fun onCubeInserted(socket: CubeSocket) {
        carriedCube = null
        audio.play(Sfx.Id.CONFIRM)
        camera.shake(0.12f, 0.5f)
        particles.sparkBurst(socket.box.cx, socket.box.cy, 18, 1.4f, 5f, Palette.ACCENT)
        script.onCubeInserted(this, socket)
    }

    fun onSwitchUsed(sw: KeySwitch) {
        audio.play(Sfx.Id.UNLOCK)
        script.onSwitchUsed(this, sw)
    }

    fun onTaskCompleted(task: LiftTask) {
        audio.play(Sfx.Id.CONFIRM)
        particles.sparkBurst(task.box.cx, task.box.cy, 12, 1.2f, 4f, Palette.GOOD)
        script.onTaskCompleted(this, task)
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

        carriedCable?.let { drawCarriedCable(c, d, it) }
        player.draw(c, d, camera, time)
        particles.draw(c, d, camera)

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
            Palette.mix(Palette.mix(Palette.BG_FAR, Palette.VOID, 0.30f), Palette.ACCENT_DIM, 0.12f),
            Palette.mix(Palette.mix(Palette.BG_FAR, Palette.BG_NEAR, 0.55f), Palette.ACCENT_DIM, 0.08f)
        )
        // Overhead ambient: without it the plating, bolts and tread that the
        // materials pass draws are all sitting in the dark doing nothing.
        val roofY = camera.sy(room.bounds.t)
        val floorY = camera.sy(room.bounds.b)
        if (floorY > roofY) {
            d.vGradient(
                c, 0f, roofY, w, floorY,
                Palette.withAlpha(Palette.TEXT, 0.055f + lightLift * 0.05f),
                Palette.withAlpha(Palette.TEXT, 0f)
            )
        }
    }

    /** Player brightness preference, 0 .. 1, applied on top of the room lighting. */
    var lightLift = 0.5f

    private fun drawDecor(c: Canvas, d: Draw, dec: Decor) {
        if (!camera.isVisible(dec.box, 1.5f)) return
        val l = camera.sx(dec.box.l); val r = camera.sx(dec.box.r)
        val t = camera.sy(dec.box.t); val b = camera.sy(dec.box.b)
        val detail = camera.scale
        when (dec.kind) {
            Decor.Kind.LIGHT -> {
                val powered = dec.lit || subfloorPowered || !room.needsPower
                // An unpowered fixture still runs on the emergency bus: a dim
                // red ember. A pitch black room the player cannot read is not
                // atmosphere, it is a wall.
                val tint = if (powered) dec.color else Palette.BAD
                val flicker = if (powered) 0.78f + 0.22f * sin(time * 9.3f + dec.box.l)
                else 0.30f + 0.16f * sin(time * 1.7f + dec.box.l)
                // Housing, then the tube itself glowing inside it.
                Art.plate(
                    c, d, l - detail * 0.06f, t - detail * 0.05f, r + detail * 0.06f, b,
                    Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.5f),
                    Palette.mix(Palette.WALL, Palette.VOID, 0.4f),
                    Palette.mix(Palette.TRIM, Palette.TEXT_DIM, 0.4f),
                    detail, dec.box.l, bolts = true
                )
                d.round(c, l + detail * 0.05f, t + detail * 0.06f, r - detail * 0.05f, b - detail * 0.04f,
                    detail * 0.04f, Palette.withAlpha(tint, 0.20f + flicker * 0.75f))
                d.glow(c, (l + r) * 0.5f, b, camera.s(if (powered) 2.2f else 1.2f), tint, 0.75f * flicker)
                d.circle(c, (l + r) * 0.5f, b - camera.s(0.05f), camera.s(0.09f), Palette.withAlpha(Palette.TEXT, flicker * 0.8f))
            }
            Decor.Kind.GRATE -> {
                d.vGradient(c, l, t, r, b, Palette.mix(dec.color, Palette.WALL_LIT, 0.4f),
                    Palette.mix(dec.color, Palette.VOID, 0.5f))
                var x = l
                val step = MathX.min(camera.s(0.16f), 24f)
                while (x < r && step > 1.5f) {
                    d.line(c, x, t, x, b, Palette.withAlpha(Palette.VOID, 0.6f), 2f)
                    d.line(c, x + 1.5f, t, x + 1.5f, b, Palette.withAlpha(Palette.TRIM, 0.16f), 1.5f)
                    x += step
                }
                d.rect(c, l, t, r, t + 2f, Palette.withAlpha(Palette.TRIM, 0.5f))
            }
            Decor.Kind.PIPE -> Art.pipe(c, d, l, t, r, b, dec.color, detail)
            Decor.Kind.STRIPE -> {
                if (dec.color == Palette.WARN || dec.color == Palette.BAD) {
                    Art.hazardBand(c, d, l, t, r, b, dec.color)
                } else {
                    d.vGradient(c, l, t, r, b, Palette.mix(dec.color, Palette.TRIM, 0.35f),
                        Palette.mix(dec.color, Palette.VOID, 0.45f))
                }
            }
            else -> d.vGradient(c, l, t, r, b, dec.color, Palette.mix(dec.color, Palette.VOID, 0.4f))
        }
    }

    private fun drawSolid(c: Canvas, d: Draw, s: Solid) {
        if (!camera.isVisible(s.box, 1.5f)) return
        val l = camera.sx(s.box.l); val r = camera.sx(s.box.r)
        val t = camera.sy(s.box.t); val b = camera.sy(s.box.b)
        val detail = camera.scale
        when (s.kind) {
            Solid.Kind.CRATE -> {
                Art.crate(c, d, l, t, r, b, detail, s.box.l)
                Art.wear(c, d, l, t, r, b, detail, s.box.l + s.box.t, count = 5)
            }
            Solid.Kind.PLATFORM -> {
                Art.plate(
                    c, d, l, t, r, b,
                    Palette.mix(Palette.FLOOR_EDGE, Palette.TRIM, 0.5f),
                    Palette.mix(Palette.FLOOR, Palette.VOID, 0.4f),
                    Palette.mix(Palette.TRIM, Palette.ACCENT_DIM, 0.35f),
                    detail, s.box.l, bolts = false
                )
                // Under-lit edge so a thin ledge is obvious against a dark wall.
                d.rect(c, l, b, r, b + MathX.clamp(detail * 0.05f, 1.5f, 4f), Palette.withAlpha(Palette.VOID, 0.55f))
                // Support brackets under the ledge.
                if (detail > 30f && r - l > detail) {
                    val bw = MathX.clamp(detail * 0.08f, 2f, 7f)
                    d.poly(c, floatArrayOf(l + detail * 0.2f, b, l + detail * 0.2f + bw, b, l + detail * 0.2f, b + detail * 0.28f),
                        Palette.withAlpha(Palette.TRIM, 0.45f))
                    d.poly(c, floatArrayOf(r - detail * 0.2f, b, r - detail * 0.2f - bw, b, r - detail * 0.2f, b + detail * 0.28f),
                        Palette.withAlpha(Palette.TRIM, 0.45f))
                }
            }
            else -> {
                // Floors get tread; walls and ceilings get plating.
                val isFloor = s.box.h <= 1.6f && s.box.w > s.box.h * 3f && s.box.t >= room.bounds.b - 0.2f
                if (isFloor) Art.floor(c, d, l, t, r, b, detail)
                else {
                    Art.plate(
                        c, d, l, t, r, b,
                        Palette.mix(Palette.WALL, Palette.WALL_LIT, 0.40f),
                        Palette.mix(Palette.WALL, Palette.VOID, 0.55f),
                        Palette.mix(Palette.WALL_LIT, Palette.TRIM, 0.35f),
                        detail, s.box.l, bolts = true
                    )
                    Art.wear(c, d, l, t, r, b, detail, s.box.l - s.box.t, count = 4)
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

    /**
     * The heavy run paying out behind the player as they haul it: a real cable
     * stretching from where it was anchored to the coil in their hands, sagging
     * more the further they drag it.
     */
    private fun drawCarriedCable(c: Canvas, d: Draw, cable: CableCoil) {
        val ax = camera.sx(cable.anchorX)
        val ay = camera.sy(cable.anchorY)
        val cx = camera.sx(cable.box.cx)
        val cy = camera.sy(cable.box.cy)
        val span = kotlin.math.hypot(cable.box.cx - cable.anchorX, cable.box.cy - cable.anchorY)
        val sag = camera.s(0.35f + span * 0.10f)
        Art.cable(c, d, ax, ay, cx, cy, sag, camera.s(0.13f))
        // The anchor plate it is bolted to.
        d.circle(c, ax, ay, camera.s(0.14f), Palette.mix(Palette.TRIM, Palette.VOID, 0.3f))
        d.circle(c, ax, ay, camera.s(0.07f), Palette.withAlpha(Palette.WARN, 0.8f))
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
                c, w, h, cx, cy, camera.s(10f + lightLift * 4f),
                Palette.mix(Palette.VOID, Palette.BG_NEAR, 0.30f),
                depth = 0.90f - lightLift * 0.14f
            )
            // A soft carry-light around the figure so the metre in front of the
            // player — the metre they have to judge a jump from — is readable.
            d.glow(c, cx, cy, camera.s(5.0f), Palette.mix(Palette.ACCENT, Palette.TEXT, 0.45f), 0.34f)
        }
        val chaseHeat = if (stage == chaseStageValue) (chaseTime / script.chaseSeconds).coerceIn(0f, 1f) else 0f
        d.vignette(
            c, w, h, 0.34f + chaseHeat * 0.34f,
            if (chaseHeat > 0f) Palette.mix(Palette.VOID, Palette.BAD, chaseHeat * 0.45f) else Palette.VOID
        )
    }

    companion object {
        const val CHAR_SECONDS = 0.022f
        const val AUTO_HOLD = 1.9f
        const val PEER_SECONDS = 2.0f
        const val ENDING_SECONDS = 9.5f
        const val DEATH_SECONDS = 1.9f
        const val CHASE_SPEED_BOOST = 1.15f
        /** How long to be stopped by a low gap before the game says so. */
        const val HINT_DELAY = 0.35f
        /** Mercy window after a hit lands. */
        const val HIT_MERCY = 1.1f
        /** Metres the pursuer hangs back at the start of the chase... */
        const val CHASE_LEAD_START = 9f
        /** ...and at the end, by which point the clock has caught up anyway. */
        const val CHASE_LEAD_END = 1.5f

    }
}
