package com.fames.protokit.core

import com.fames.protokit.core.transport.GrpcTransport
import com.fames.protokit.core.transport.StreamCall
import com.fames.protokit.core.transport.TransportResponse
import com.fames.protokit.sdk.models.GrpcStatus
import com.fames.protokit.sdk.models.GrpcTrailers
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.stub.ClientCalls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit

internal class DesktopGrpcTransport : GrpcTransport {

    override var baseUrl: String = ""
    
    private var _channel: ManagedChannel? = null
    private val channel: ManagedChannel
        get() {
            if (_channel == null) {
                val uri = URI(baseUrl)
                val host = uri.host ?: ""
                val port = if (uri.port != -1) uri.port else (if (uri.scheme == "https") 443 else 80)
                
                val builder = ManagedChannelBuilder.forAddress(host, port)
                    .executor(Dispatchers.IO.asExecutor())
                
                if (uri.scheme != "https") {
                    builder.usePlaintext()
                }
                
                _channel = builder.build()
            }
            return _channel!!
        }

    override fun initIos() {
        // No-op on Desktop
    }

    override suspend fun unaryCall(
        method: String,
        requestBytes: ByteArray,
        timeoutMillis: Long?,
        headers: Map<String, String>
    ): TransportResponse = withContext(Dispatchers.IO) {
        
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

        try {
            val responseBytes = ClientCalls.blockingUnaryCall(
                channel,
                methodDescriptor,
                callOptions,
                requestBytes
            )

            TransportResponse(
                body = responseBytes,
                trailers = GrpcTrailers(status = GrpcStatus.OK)
            )
        } catch (e: io.grpc.StatusRuntimeException) {
            TransportResponse(
                body = byteArrayOf(),
                trailers = GrpcTrailers(
                    status = GrpcStatus.fromCode(e.status.code.value()),
                    message = e.status.description,
                    raw = e.trailers?.let { t -> 
                        t.keys().associateWith { key -> 
                            t.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)) ?: ""
                        }
                    } ?: emptyMap()
                )
            )
        }
    }

    override fun serverStream(
        method: String,
        requestBytes: ByteArray,
        headers: Map<String, String>
    ): StreamCall {
        TODO("Streaming not implemented in Desktop native yet")
    }
}

private class ByteArrayMarshaller : MethodDescriptor.Marshaller<ByteArray> {
    override fun stream(value: ByteArray): InputStream = value.inputStream()
    override fun parse(stream: InputStream): ByteArray = stream.readBytes()
}
