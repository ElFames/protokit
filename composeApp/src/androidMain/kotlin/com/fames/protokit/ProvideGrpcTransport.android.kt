package com.fames.protokit

import com.fames.protokit.runtime.AndroidGrpcTransport
import com.fames.protokit.runtime.transport.GrpcTransport

actual fun provideGrpcTransport(url: String): GrpcTransport {
    return AndroidGrpcTransport(url)
}