package com.fames.protokit.runtime.transport

import kotlinx.coroutines.flow.Flow

interface StreamCall {
    val incoming: Flow<ByteArray>
    suspend fun cancel()
}