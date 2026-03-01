package com.fames.protokit.core

import com.fames.protokit.core.transport.GrpcTransport
import com.fames.protokit.core.transport.StreamCall
import com.fames.protokit.core.transport.TransportResponse
import com.fames.protokit.sdk.models.GrpcStatus
import com.fames.protokit.sdk.models.GrpcTrailers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.IOException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AndroidGrpcTransport: GrpcTransport {

    override var baseUrl: String = ""
    private val httpClient = OkHttpClient.Builder().protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)).build()

    override fun initIos() {

    }

    override suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        timeoutMillis: Long?,
        headers: Map<String, String>
    ): TransportResponse = suspendCancellableCoroutine { cont ->

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

        val call = callClient.newCall(request)

        cont.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = response.body.bytes()
                    val trailers = response.trailers().toGrpcTrailers()

                    cont.resume(
                        TransportResponse(
                            body = unframe(body),
                            trailers = trailers
                        )
                    )
                }
            }
        })
    }


    override fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String>
    ): StreamCall {
        TODO("Not yet implemented.")
    }

    private fun frame(data: ByteArray): ByteArray =
        ByteBuffer.allocate(5 + data.size)
            .put(0)
            .putInt(data.size)
            .put(data)
            .array()

    private fun unframe(data: ByteArray): ByteArray {
        val compressed = data[0].toInt()
        if (compressed != 0) {
            error("gRPC message compression is not supported yet")
        }
        val length = ByteBuffer.wrap(data, 1, 4).int
        return data.copyOfRange(5, 5 + length)
    }

}

internal fun Headers.toGrpcTrailers(): GrpcTrailers {
    val status = this["grpc-status"]?.toIntOrNull()?.let {
        GrpcStatus.fromCode(it)
    } ?: GrpcStatus.UNKNOWN

    return GrpcTrailers(
        status = status,
        message = this["grpc-message"],
        raw = toMultimap().mapValues { it.value.joinToString(",") }
    )
}