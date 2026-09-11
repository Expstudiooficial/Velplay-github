package com.expstudio.facilitycore

import com.expstudio.facilitycore.game.Cut
import com.expstudio.facilitycore.game.GameSession

fun GameSession.roomId(): String = room.id
fun GameSession.cutIsDeath(): Boolean = cut == Cut.DEATH
