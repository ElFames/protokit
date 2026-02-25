package com.fames.protokit.core

import com.fames.protokit.core.io.Framer
import com.fames.protokit.core.transport.GrpcTransport
import com.fames.protokit.core.transport.StreamCall
import com.fames.protokit.core.transport.TransportResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CFNetwork.kCFHTTPVersion2_0
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.objc.protocol_addProtocol
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class IosGrpcTransport(
    private val baseUrl: String
) : GrpcTransport {
    
    @OptIn(ExperimentalForeignApi::class)
    private val client = HttpClient(Darwin) {
        engine {
            configureSession {
                protocol_addProtocol(kCFHTTPVersion2_0)
            }
        }
    }

    override suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        timeoutMillis: Long?,
        headers: Map<String, String>
    ): TransportResponse = suspendCancellableCoroutine { cont ->

        val framed = Framer.frame(requestBytes)
        val url = NSURL(string = "$baseUrl$method")
        val timeoutInterval = (timeoutMillis ?: 15_000) / 1000.0

        val request = NSMutableURLRequest(url)
        request.HTTPMethod = "POST"
        request.setValue("application/grpc", forHTTPHeaderField = "Content-Type")
        request.setValue("trailers", forHTTPHeaderField = "TE")
        request.setTimeoutInterval(timeoutInterval)
        request.HTTPBody = framed.toNSData()
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }


        val task = urlSession.dataTaskWithRequest(request) { data, response, error ->
            when {
                error != null -> cont.resumeWithException(Throwable(error.localizedDescription))
                data == null -> cont.resumeWithException(IllegalStateException("Empty response"))
                else -> {
                    val body = Framer.unframe(data.toByteArray())
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
}
