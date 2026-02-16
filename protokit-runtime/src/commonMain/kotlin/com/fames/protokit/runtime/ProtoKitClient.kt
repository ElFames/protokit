package com.fames.protokit.runtime

import com.fames.protokit.runtime.transport.GrpcTransport
import kotlinx.coroutines.flow.Flow

class ProtoKitClient(
    private val transport: GrpcTransport
) {

    suspend fun unary(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String> = emptyMap()
    ): ByteArray {
        return transport.unaryCall(method, requestBytes, headers)
    }

    fun stream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String> = emptyMap()
    ): Flow<ByteArray> {
        return transport.serverStream(method, requestBytes, headers).incoming
    }
}
