package com.fames.protokit.runtime

import com.fames.protokit.runtime.transport.GrpcTransport
import com.fames.protokit.runtime.transport.StreamCall
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer

class AndroidGrpcTransport(
    private val baseUrl: String,
    client: OkHttpClient? = null
) : GrpcTransport {

    private val httpClient = client ?: OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .build()

    override suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String>
    ): ByteArray {
        val framed = frameGrpcMessage(requestBytes)

        val request = Request.Builder()
            .url("$baseUrl$method")
            .post(framed.toRequestBody("application/grpc".toMediaType()))
            .header("TE", "trailers")
            .build()

        httpClient.newCall(request).execute().use { res ->
            val body = res.body?.bytes() ?: error("Empty gRPC body")
            val messageLength = ByteBuffer.wrap(body, 1, 4).int
            return body.copyOfRange(5, 5 + messageLength)
        }
    }

    override fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String>
    ): StreamCall {
        TODO("stream not implemented")
    }

    private fun frameGrpcMessage(data: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(5 + data.size)
        buffer.put(0) // no compression
        buffer.putInt(data.size)
        buffer.put(data)
        return buffer.array()
    }

}
