package com.fames.protokit.sdk

import com.fames.protokit.core.transport.GrpcTransportProvider
import com.fames.protokit.core.InternalProtoClient
import com.fames.protokit.core.provideGrpcTransport
import com.fames.protokit.sdk.models.Response
import com.fames.protokit.core.transport.GrpcTransport
import kotlinx.coroutines.flow.Flow

class ProtoClient(
    private val baseUrl: String,
    private val defaultTimeoutMillis: Long? = 15_000,
    private val defaultHeaders: (suspend () -> Map<String, String>)? = null
) {
    private var transport: GrpcTransport

    init {
        provideGrpcTransport()
        transport = GrpcTransportProvider.grpcTransport
        transport.baseUrl = baseUrl
        transport.initIos()
    }

    private val client = InternalProtoClient(
        transport = transport,
        defaultTimeoutMillis = defaultTimeoutMillis,
        defaultHeaders = defaultHeaders
    )

    suspend fun <Req, Res> unary(
        method: String,
        request: Req,
        encoder: (Req) -> ByteArray,
        decoder: (ByteArray) -> Res,
        timeoutMillis: Long? = null,
        headers: Map<String, String> = emptyMap()
    ): Response<Res> = client.unary(
        method = method,
        request = request,
        encoder = encoder,
        decoder = decoder,
        timeoutMillis = timeoutMillis,
        headers = headers
    )

    fun stream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String> = emptyMap()
    ): Flow<ByteArray> = client.stream(
        method = method,
        requestBytes = requestBytes,
        headers = headers
    )
}
