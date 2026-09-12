package com.expstudio.facilitycore

import com.expstudio.facilitycore.core.Box
import com.expstudio.facilitycore.game.Cut
import com.expstudio.facilitycore.game.GameSession

fun GameSession.roomId(): String = room.id
fun GameSession.cutIsDeath(): Boolean = cut == Cut.DEATH

/** Test-only room placement; production code only moves via exits. */
fun GameSession.enterRoomForTest(roomId: String, x: Float, y: Float = 0f) {
    val target = level.rooms[roomId] ?: return
    val field = GameSession::class.java.getDeclaredField("room")
    field.isAccessible = true
    field.set(this, target)
    player.teleport(x, y)
    camera.follow(x, y - 1.1f, target.bounds, 0f, snap = true)
}

/** Fires a switch's story beat directly, for screenshots of what follows it. */
fun GameSession.onSwitchUsedForTest(sw: com.expstudio.facilitycore.game.KeySwitch) {
    script.onSwitchUsed(this, sw)
}

/** The solid list the session is currently colliding against. */
fun GameSession.solidsForTest(): List<Box> {
    val out = ArrayList<Box>()
    for (s in room.solids) out.add(s.box)
    for (p in room.props) p.solid?.let { out.add(it) }
    return out
}
