package com.fames.protokit

import com.fames.protokit.runtime.IosGrpcTransport
import com.fames.protokit.runtime.transport.GrpcTransport

actual fun provideGrpcTransport(url: String): GrpcTransport {
    return IosGrpcTransport(url)
}