package com.fames.protokit.core

import com.fames.protokit.core.transport.GrpcTransport
import com.fames.protokit.core.transport.StreamCall
import com.fames.protokit.core.transport.TransportResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class IosGrpcTransport(
    private val baseUrl: String,
    session: NSURLSession? = null
) : GrpcTransport {

    private val urlSession: NSURLSession =
        session ?: NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration()
        )

    override suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        timeoutMillis: Long?,
        headers: Map<String, String>
    ): TransportResponse = suspendCancellableCoroutine { cont ->

        val framed = frameGrpcMessage(requestBytes)
        val url = NSURL(string = "$baseUrl$method")

        val request = NSMutableURLRequest(url).apply {
            HTTPMethod = "POST"
            setValue("application/grpc", forHTTPHeaderField = "Content-Type")
            setValue("trailers", forHTTPHeaderField = "TE")

            headers.forEach { (k, v) ->
                setValue(v, forHTTPHeaderField = k)
            }

            HTTPBody = framed.toNSData()
        }

        val task = urlSession.dataTaskWithRequest(request) { data, response, error ->
            when {
                error != null -> {
                    cont.resumeWithException(Throwable(error.localizedDescription))
                }

                data == null -> {
                    cont.resumeWithException(
                        IllegalStateException("Empty gRPC response")
                    )
                }

                else -> {
                    val bytes = data.toByteArray()
                    val trailers = response.toGrpcTrailers()

                    if (bytes.size < 5) {
                        cont.resumeWithException(
                            IllegalStateException("Invalid gRPC frame size=${bytes.size}")
                        )
                        return@dataTaskWithRequest
                    }

                    val http = response as? NSHTTPURLResponse
                    val contentType =
                        http?.allHeaderFields?.get("content-type") as? String ?: ""

                    if (!contentType.startsWith("application/grpc")) {
                        cont.resumeWithException(
                            IllegalStateException(
                                "Not a gRPC response: Content-Type=$contentType"
                            )
                        )
                        return@dataTaskWithRequest
                    }

                    cont.resume(TransportResponse(body = unframeGrpcMessage(bytes), trailers = trailers))
                }
            }
        }

        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    override fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String>
    ): StreamCall {
        TODO("HTTP/2 streaming not implemented yet")
    }

    private fun frameGrpcMessage(data: ByteArray): ByteArray {
        val result = ByteArray(5 + data.size)
        result[0] = 0
        val size = data.size
        result[1] = ((size shr 24) and 0xFF).toByte()
        result[2] = ((size shr 16) and 0xFF).toByte()
        result[3] = ((size shr 8) and 0xFF).toByte()
        result[4] = (size and 0xFF).toByte()
        data.copyInto(result, 5)
        return result
    }

    private fun unframeGrpcMessage(data: ByteArray): ByteArray {
        val length =
            ((data[1].toInt() and 0xFF) shl 24) or
                    ((data[2].toInt() and 0xFF) shl 16) or
                    ((data[3].toInt() and 0xFF) shl 8) or
                    (data[4].toInt() and 0xFF)

        return data.copyOfRange(5, 5 + length)
    }
}



/**
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
**/