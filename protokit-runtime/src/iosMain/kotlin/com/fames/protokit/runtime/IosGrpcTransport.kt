package com.fames.protokit.runtime

import com.fames.protokit.runtime.transport.GrpcTransport
import com.fames.protokit.runtime.transport.StreamCall
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
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
        headers: Map<String, String>
    ): ByteArray = suspendCancellableCoroutine { cont ->

        val framed = frameGrpcMessage(requestBytes)

        val url = NSURL(string = "$baseUrl$method")

        val request = NSMutableURLRequest(url).apply {
            HTTPMethod = "POST"
            setValue("application/grpc", forHTTPHeaderField = "content-type")
            setValue("trailers", forHTTPHeaderField = "te")

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
                    cont.resumeWithException(IllegalStateException("Empty gRPC response"))
                }

                else -> {
                    val bytes = data.toByteArray()
                    cont.resume(unframeGrpcMessage(bytes))
                }
            }
        }

        task.resume()
    }

    override fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String>
    ): StreamCall {
        TODO("Implement HTTP/2 streaming later")
    }

    private fun frameGrpcMessage(data: ByteArray): ByteArray {
        val result = ByteArray(5 + data.size)
        result[0] = 0 // no compression
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

