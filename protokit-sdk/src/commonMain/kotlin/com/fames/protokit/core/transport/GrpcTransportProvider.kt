package com.fames.protokit.core.transport

object GrpcTransportProvider {
    internal lateinit var grpcTransport: GrpcTransport

    fun provide(implementation: GrpcTransport) {
        grpcTransport = implementation
    }

}