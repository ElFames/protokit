package com.fames.protokit.runtime.transport

import com.fames.protokit.runtime.models.GrpcTrailers

data class TransportResponse(
    val body: ByteArray,
    val trailers: GrpcTrailers
)
