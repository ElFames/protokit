package com.fames.protokit.core.transport

interface GrpcTransport {
    var baseUrl: String

    fun initIos()
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
