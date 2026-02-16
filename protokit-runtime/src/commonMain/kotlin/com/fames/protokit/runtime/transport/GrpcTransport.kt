package com.fames.protokit.runtime.transport

interface GrpcTransport {

    suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        timeoutMillis: Long?,
        headers: Map<String, String> = emptyMap()
    ): TransportResponse

    fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String> = emptyMap()
    ): StreamCall
}
