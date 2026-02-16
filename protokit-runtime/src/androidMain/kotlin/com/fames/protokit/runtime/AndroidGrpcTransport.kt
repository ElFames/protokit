package com.fames.protokit.runtime

import com.fames.protokit.runtime.transport.GrpcTransport
import com.fames.protokit.runtime.transport.StreamCall
import com.fames.protokit.runtime.transport.TransportResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class AndroidGrpcTransport(
    private val baseUrl: String,
    client: OkHttpClient? = null
) : GrpcTransport {

    private val httpClient =
        client ?: OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()

    override suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        timeoutMillis: Long?,
        headers: Map<String, String>
    ): TransportResponse = withContext(Dispatchers.IO) {

        val framed = frame(requestBytes)

        val callClient =
            timeoutMillis?.let {
                httpClient.newBuilder()
                    .callTimeout(it, TimeUnit.MILLISECONDS)
                    .build()
            } ?: httpClient

        val request = Request.Builder()
            .url("$baseUrl$method")
            .post(framed.toRequestBody("application/grpc".toMediaType()))
            .header("TE", "trailers")
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
            }
            .build()

        callClient.newCall(request).execute().use { res ->
            val body = res.body?.bytes() ?: error("Empty body")
            val trailers = res.trailers().toGrpcTrailers()

            TransportResponse(
                body = unframe(body),
                trailers = trailers
            )
        }
    }

    override fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String>
    ): StreamCall {
        TODO("Not yet implemented")
    }

    private fun frame(data: ByteArray): ByteArray =
        ByteBuffer.allocate(5 + data.size)
            .put(0)
            .putInt(data.size)
            .put(data)
            .array()

    private fun unframe(data: ByteArray): ByteArray {
        val length = ByteBuffer.wrap(data, 1, 4).int
        return data.copyOfRange(5, 5 + length)
    }
}
