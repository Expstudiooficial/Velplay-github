package com.expstudio.facilitycore.game

/**
 * Linear progression of Chapter 1. The value is persisted, so entries must only
 * ever be appended — never renumbered.
 */
object Stage {
    const val INTRO = 0
    const val LOBBY_PUZZLE = 1
    const val FIND_KEYPACK = 2
    const val REPAIR_KEYPACK = 3
    const val UNLOCK_FIRST_DOOR = 4
    const val FETCH_BIG_WIRE = 5
    const val POWER_SUBFLOOR = 6
    const val REACH_ELEVATOR = 7
    const val ELEVATOR_DENIED = 8
    const val ENCOUNTER = 9
    const val CHASE = 10
    const val CHASE_SURVIVED = 11
    const val CIRCUIT_ROOM = 12
    const val CIRCUIT_ASSEMBLED = 13
    const val PANEL_UPLOADED = 14
    const val VENT_CRAWL = 15
    const val AFTER_FALL = 16
    const val ELEVATOR_READY = 17
    const val ENDING = 18
    const val COMPLETE = 19

    /**
     * The checkpoint a given stage rolls back to. The chase sends the player all
     * the way back to the locked elevator, exactly as the chapter demands.
     */
    fun checkpointFor(stage: Int): Int = when {
        stage >= ELEVATOR_READY -> ELEVATOR_READY
        stage >= PANEL_UPLOADED -> PANEL_UPLOADED
        stage >= CHASE_SURVIVED -> CHASE_SURVIVED
        stage >= ELEVATOR_DENIED -> ELEVATOR_DENIED
        stage >= POWER_SUBFLOOR -> POWER_SUBFLOOR
        stage >= UNLOCK_FIRST_DOOR -> UNLOCK_FIRST_DOOR
        stage >= LOBBY_PUZZLE -> LOBBY_PUZZLE
        else -> INTRO
    }

    fun objectiveFor(stage: Int): String = when (stage) {
        INTRO -> "Find a way deeper into the facility"
        LOBBY_PUZZLE -> "Reroute the intake breakers"
        FIND_KEYPACK -> "The door is locked — find something that opens it"
        REPAIR_KEYPACK -> "Repair the key pack's broken wiring"
        UNLOCK_FIRST_DOOR -> "Unlock the sealed door with the key pack"
        FETCH_BIG_WIRE -> "Find the heavy feeder cable"
        POWER_SUBFLOOR -> "Throw the cable up to the connection station"
        REACH_ELEVATOR -> "Subfloor 0 is live — reach the elevator"
        ELEVATOR_DENIED -> "The elevator isn't in the database. Find the archive."
        ENCOUNTER -> "Something is in here with you"
        CHASE -> "RUN"
        CHASE_SURVIVED -> "Catch your breath — then keep moving"
        CIRCUIT_ROOM -> "Connect both feeder cables and take the data shards"
        CIRCUIT_ASSEMBLED -> "Insert the circuit into the archive panel"
        PANEL_UPLOADED -> "Take the key pack — the elevator is in the database"
        VENT_CRAWL -> "Crawl back through the vent"
        AFTER_FALL -> "Get up. The elevator is right there."
        ELEVATOR_READY -> "Unlock the elevator and descend to Subfloor 1"
        ENDING -> "Descend"
        else -> "Chapter 1 complete"
    }
}
