package com.fames.protokit.runtime.transport

interface GrpcTransport {

    suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String> = emptyMap()
    ): ByteArray

    fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String> = emptyMap()
    ): StreamCall
}
