package com.zai.avora.service

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

object MediaBridge {
    enum class Command { TOGGLE_FAVORITE, TOGGLE_REPEAT }

    val isFavorite = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)

    val commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
}
