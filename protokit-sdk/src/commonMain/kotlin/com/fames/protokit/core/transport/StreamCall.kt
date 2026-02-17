package com.fames.protokit.core.transport

import kotlinx.coroutines.flow.Flow

interface StreamCall {
    val incoming: Flow<ByteArray>
    suspend fun cancel()
}