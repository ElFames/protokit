package com.fames.protokit.core

import com.fames.protokit.core.transport.GrpcTransport

internal actual fun provideGrpcTransport(url: String): GrpcTransport {
    return AndroidGrpcTransport(url)
}