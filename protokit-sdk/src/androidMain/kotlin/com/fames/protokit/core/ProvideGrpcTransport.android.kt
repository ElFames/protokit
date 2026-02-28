package com.fames.protokit.core

import com.fames.protokit.core.transport.GrpcTransportProvider

internal actual fun provideGrpcTransport() {
    GrpcTransportProvider.provide(AndroidGrpcTransport())
}