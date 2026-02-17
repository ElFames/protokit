package com.fames.protokit.core

import com.fames.protokit.core.transport.GrpcTransport

internal expect fun provideGrpcTransport(url: String): GrpcTransport