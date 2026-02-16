package com.fames.protokit

import com.fames.protokit.runtime.transport.GrpcTransport

expect fun provideGrpcTransport(url: String): GrpcTransport