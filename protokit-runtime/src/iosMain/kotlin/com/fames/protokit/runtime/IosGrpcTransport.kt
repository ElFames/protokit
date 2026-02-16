package com.fames.protokit.runtime

import com.fames.protokit.runtime.transport.GrpcTransport
import com.fames.protokit.runtime.transport.StreamCall
import com.fames.protokit.runtime.transport.TransportResponse
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
    private val urlSession =
        session ?: NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration().apply {
                timeoutIntervalForRequest = 15.0
            }
        )

    override suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        timeoutMillis: Long?,
        headers: Map<String, String>
    ): TransportResponse = suspendCancellableCoroutine { cont ->

        val framed = frame(requestBytes)
        val url = NSURL(string = "$baseUrl$method")
        val timeoutInterval = (timeoutMillis ?: 15_000) / 1000.0

        val request = NSMutableURLRequest(url).apply {
            HTTPMethod = "POST"
            setValue("application/grpc", forHTTPHeaderField = "Content-Type")
            setValue("trailers", forHTTPHeaderField = "TE")
            setTimeoutInterval(timeoutInterval)
            HTTPBody = framed.toNSData()
            headers.forEach { (k, v) -> setValue(v, forHTTPHeaderField = k) }
        }

        val task = urlSession.dataTaskWithRequest(request) { data, response, error ->
            when {
                error != null -> cont.resumeWithException(Throwable(error.localizedDescription))
                data == null -> cont.resumeWithException(IllegalStateException("Empty response"))
                else -> {
                    val body = unframe(data.toByteArray())
                    val trailers = response.toGrpcTrailers()
                    cont.resume(TransportResponse(body, trailers))
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
        TODO("Not yet implemented")
    }

    private fun frame(data: ByteArray): ByteArray =
        ByteArray(5 + data.size).apply {
            this[0] = 0
            val size = data.size
            this[1] = ((size shr 24) and 0xFF).toByte()
            this[2] = ((size shr 16) and 0xFF).toByte()
            this[3] = ((size shr 8) and 0xFF).toByte()
            this[4] = (size and 0xFF).toByte()
            data.copyInto(this, 5)
        }

    private fun unframe(data: ByteArray): ByteArray {
        val length =
            ((data[1].toInt() and 0xFF) shl 24) or
                    ((data[2].toInt() and 0xFF) shl 16) or
                    ((data[3].toInt() and 0xFF) shl 8) or
                    (data[4].toInt() and 0xFF)

        return data.copyOfRange(5, 5 + length)
    }
}
