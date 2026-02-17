package com.fames.protokit.core

import com.fames.protokit.sdk.models.GrpcTrailers
import com.fames.protokit.sdk.models.GrpcStatus
import okhttp3.Headers

internal fun Headers.toGrpcTrailers(): GrpcTrailers {
    val status = this["grpc-status"]?.toIntOrNull()?.let {
        GrpcStatus.fromCode(it)
    } ?: GrpcStatus.UNKNOWN

    return GrpcTrailers(
        status = status,
        message = this["grpc-message"],
        raw = toMultimap().mapValues { it.value.joinToString(",") }
    )
}
