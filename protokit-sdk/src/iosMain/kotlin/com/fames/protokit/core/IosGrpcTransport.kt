package com.fames.protokit.core

import com.fames.protokit.core.io.Framer
import com.fames.protokit.core.transport.GrpcTransport
import com.fames.protokit.core.transport.StreamCall
import com.fames.protokit.core.transport.TransportResponse
import com.fames.protokit.sdk.models.GrpcStatus
import com.fames.protokit.sdk.models.GrpcTrailers
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal class IosGrpcTransport(
    private val baseUrl: String
) : GrpcTransport {

    private val client = HttpClient(Darwin) {
        install(HttpTimeout)
    }

    override suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        timeoutMillis: Long?,
        headers: Map<String, String>
    ): TransportResponse {
        val framedRequest = Framer.frame(requestBytes)

        val httpResponse = client.post("$baseUrl$method") {
            headers {
                append("Content-Type", "application/grpc")
                append("TE", "trailers")
                headers.forEach { (k, v) -> append(k, v) }
            }
            timeout {
                requestTimeoutMillis = timeoutMillis ?: 15_000
            }
            setBody(framedRequest)
        }

        val responseData = httpResponse.body<ByteArray>()
        val body = Framer.unframe(responseData)

        val rawTrailers = httpResponse.headers.entries()
            .filter { it.key.lowercase().startsWith("grpc-") }
            .associate { it.key to it.value.joinToString(",") }

        val grpcStatusCode = rawTrailers["grpc-status"]?.toIntOrNull() ?: GrpcStatus.UNKNOWN.code
        val grpcStatus = GrpcStatus.fromCode(grpcStatusCode)
        val grpcMessage = rawTrailers["grpc-message"]

        val trailers = GrpcTrailers(grpcStatus, grpcMessage, rawTrailers)

        return TransportResponse(body, trailers)
    }

    override fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String>
    ): StreamCall {
        TODO("Not yet implemented")
    }
}
