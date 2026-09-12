package com.expstudio.facilitycore.game

/**
 * Everything that makes one chapter different from another: its rooms, its
 * progression, and the beats that fire as the player moves through it.
 *
 * [GameSession] owns the parts every chapter shares — physics, dialogue,
 * interaction, the chase machinery, rendering — and calls into the script
 * whenever the story has a decision to make.
 */
abstract class ChapterScript {

    abstract val chapter: Int
    abstract val chaseSeconds: Float
    /** Stage value that means "the chapter is finished". */
    abstract val completeStage: Int

    abstract fun build(): Level
    abstract fun applyStage(g: GameSession, stage: Int)
    abstract fun spawnFor(stage: Int): Triple<String, Float, Float>
    abstract fun checkpointFor(stage: Int): Int
    abstract fun objectiveFor(stage: Int): String

    /** Fired once the world has been built and the player placed. */
    open fun onStart(g: GameSession, checkpoint: Int) {}

    open fun onRoomEntered(g: GameSession, roomId: String) {}

    /** Called every frame the world is running. */
    open fun update(g: GameSession, dt: Float) {}

    /**
     * Drives a chapter-specific cutscene. Returning true means the script
     * handled this frame; false lets the session run its shared handling.
     */
    open fun updateCut(g: GameSession, dt: Float): Boolean = false

    // ---- prop events -----------------------------------------------------

    open fun onDoorOpened(g: GameSession, door: Door) {}
    open fun onBreakersSolved(g: GameSession, gateId: String) {}
    open fun onKeyPackTaken(g: GameSession) {}
    open fun onCableGrabbed(g: GameSession, cable: CableCoil) {}
    open fun onCableThrown(g: GameSession, station: ConnectionStation) {}
    open fun onStationConnected(g: GameSession, station: ConnectionStation) {}
    open fun onShardTaken(g: GameSession) {}
    open fun onUploadStarted(g: GameSession) {}
    open fun onUploadFinished(g: GameSession) {}
    open fun onPackRetrieved(g: GameSession) {}
    open fun onElevatorUse(g: GameSession, elevator: Elevator) {}
    open fun onElevatorBoard(g: GameSession) {}
    open fun onCubeTaken(g: GameSession, cube: PowerCube) {}
    open fun onCubeInserted(g: GameSession, socket: CubeSocket) {}
    open fun onSwitchUsed(g: GameSession, sw: KeySwitch) {}
    open fun onTaskCompleted(g: GameSession, task: LiftTask) {}
    open fun onAnchorPulled(g: GameSession, anchor: ReachAnchor) {}
    open fun onValvePulled(g: GameSession, valve: SnapValve, group: String) {}
    open fun onArchiveNode(g: GameSession, node: ArchiveNode) {}
    open fun onArchiveCoreTaken(g: GameSession) {}
    open fun onEmitterArmed(g: GameSession, emitter: LaserEmitter) {}
    open fun onSuperDatabaseTaken(g: GameSession) {}

    companion object {
        fun forChapter(chapter: Int): ChapterScript = when {
            chapter >= 3 -> Chapter3Script()
            chapter == 2 -> Chapter2Script()
            else -> Chapter1Script()
        }
    }
}
