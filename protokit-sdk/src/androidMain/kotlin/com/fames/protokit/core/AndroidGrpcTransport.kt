package com.fames.protokit.core

import com.fames.protokit.core.transport.GrpcTransport
import com.fames.protokit.core.transport.StreamCall
import com.fames.protokit.core.transport.TransportResponse
import com.fames.protokit.sdk.models.GrpcStatus
import com.fames.protokit.sdk.models.GrpcTrailers
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal class AndroidGrpcTransport : GrpcTransport {

    override var baseUrl: String = ""

    private val channel: ManagedChannel by lazy {
        check(baseUrl.isNotBlank()) { "baseUrl must be set before making a call." }

        val uri = java.net.URI(baseUrl)
        val host = uri.host ?: error("Invalid baseUrl, host could not be parsed from $baseUrl")
        val port = if (uri.port != -1) uri.port else (if (uri.scheme == "https") 443 else 80)

        val builder = ManagedChannelBuilder.forAddress(host, port)
            .executor(Dispatchers.IO.asExecutor())

        if (uri.scheme == "https") {
            builder.useTransportSecurity()
        } else {
            builder.usePlaintext()
        }

        builder.build()
    }

    override fun initIos() {
        // No-op on Android
    }

    override suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        timeoutMillis: Long?,
        headers: Map<String, String>
    ): TransportResponse = suspendCancellableCoroutine { cont ->

        val methodDescriptor = MethodDescriptor.newBuilder<ByteArray, ByteArray>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(method.removePrefix("/"))
            .setRequestMarshaller(ByteArrayMarshaller())
            .setResponseMarshaller(ByteArrayMarshaller())
            .build()

        var callOptions = CallOptions.DEFAULT
        if (timeoutMillis != null) {
            callOptions = callOptions.withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)
        }

        val metadata = Metadata()
        headers.forEach { (key, value) ->
            metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
        }

        val call = channel.newCall(methodDescriptor, callOptions)

        var responseBody: ByteArray? = null

        call.start(object : ClientCall.Listener<ByteArray>() {
            override fun onMessage(message: ByteArray) {
                responseBody = message
            }

            override fun onClose(status: Status, trailers: Metadata) {
                val grpcTrailers = GrpcTrailers(
                    status = GrpcStatus.fromCode(status.code.value()),
                    message = status.description,
                    raw = trailers.toMap()
                )

                cont.resume(
                    TransportResponse(
                        body = responseBody ?: byteArrayOf(),
                        trailers = grpcTrailers
                    )
                )
            }
        }, metadata)

        call.request(1)
        call.sendMessage(requestBytes)
        call.halfClose()

        cont.invokeOnCancellation {
            call.cancel("Cancelled by coroutine", it)
        }
    }

    override fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String>
    ): StreamCall {
        TODO("Streaming not implemented in Android native yet")
    }
}

private class ByteArrayMarshaller : MethodDescriptor.Marshaller<ByteArray> {
    override fun stream(value: ByteArray): InputStream = value.inputStream()
    override fun parse(stream: InputStream): ByteArray = stream.readBytes()
}

private fun Metadata.toMap(): Map<String, String> {
    return keys().associateWith { key ->
        get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)) ?: ""
    }
}
