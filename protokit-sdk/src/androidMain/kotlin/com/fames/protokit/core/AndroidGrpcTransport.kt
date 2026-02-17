package com.fames.protokit.core

import com.fames.protokit.core.transport.GrpcTransport
import com.fames.protokit.core.transport.StreamCall
import com.fames.protokit.core.transport.TransportResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.IOException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AndroidGrpcTransport(
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

            override fun onResponse(call: Call, res: Response) {
                res.use {
                    val body = res.body?.bytes() ?: ByteArray(0)
                    val trailers = res.trailers().toGrpcTrailers()

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

        val framed = frame(requestBytes)

        val request = Request.Builder()
            .url("$baseUrl$method")
            .post(framed.toRequestBody("application/grpc".toMediaType()))
            .header("TE", "trailers")
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
            }
            .build()

        val call = httpClient.newCall(request)

        val flow = channelFlow {
            val response = call.execute()
            val source = response.body?.source() ?: error("Empty stream")

            try {
                while (!source.exhausted()) {
                    val compressed = source.readByte()
                    if (compressed.toInt() != 0) {
                        throw UnsupportedOperationException(
                            "gRPC message compression is not supported yet"
                        )
                    }
                    val length = source.readInt()


                    val message = source.readByteArray(length.toLong())
                    send(message)
                }
            } catch (e: Throwable) {
                close(e)
            } finally {
                response.close()
            }
        }

        return object : StreamCall {
            override val incoming: Flow<ByteArray> = flow
            override suspend fun cancel() {
                call.cancel()
            }
        }
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
