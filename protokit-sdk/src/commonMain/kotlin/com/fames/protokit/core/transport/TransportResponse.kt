package com.fames.protokit.core.transport

import com.fames.protokit.sdk.models.GrpcTrailers

internal data class TransportResponse(
    val body: ByteArray,
    val trailers: GrpcTrailers
)
