package com.fames.protokit.core

import com.fames.protokit.sdk.models.GrpcTrailers
import com.fames.protokit.sdk.models.GrpcStatus
import com.fames.protokit.sdk.models.Response
import com.fames.protokit.core.transport.GrpcTransport
import kotlinx.coroutines.flow.Flow

internal class InternalProtoClient(
    private val transport: GrpcTransport,
    private val defaultTimeoutMillis: Long? = 15_000,
    private val defaultHeaders: (suspend () -> Map<String, String>)? = null
) {
    suspend fun <Req, Res> unary(
        method: String,
        request: Req,
        encoder: (Req) -> ByteArray,
        decoder: (ByteArray) -> Res,
        timeoutMillis: Long? = defaultTimeoutMillis,
        headers: Map<String, String> = emptyMap()
    ): Response<Res> =
        try {
            val requestBytes = encoder(request)
            val finalHeaders = headers.ifEmpty { defaultHeaders?.invoke() ?: emptyMap() }

            val response = transport.unaryCall(
                method = method,
                requestBytes = requestBytes,
                timeoutMillis = timeoutMillis,
                headers = finalHeaders
            )

            if (response.trailers.status != GrpcStatus.OK) {
                Response.Failure(
                    status = response.trailers.status,
                    message = response.trailers.message,
                    trailers = response.trailers
                )
            } else {
                Response.Success(
                    value = decoder(response.body),
                    trailers = response.trailers
                )
            }
        } catch (t: Throwable) {
            Response.Failure(
                status = GrpcStatus.UNKNOWN,
                message = t.message,
                trailers = GrpcTrailers(
                    status = GrpcStatus.UNKNOWN,
                    message = t.message,
                    raw = emptyMap()
                )
            )
        }

    fun stream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String> = emptyMap()
    ): Flow<ByteArray> {
        return transport.serverStream(method, requestBytes, headers).incoming
    }
}