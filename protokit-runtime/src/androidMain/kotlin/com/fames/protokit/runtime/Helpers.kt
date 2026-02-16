package com.fames.protokit.runtime

import com.fames.protokit.runtime.models.GrpcStatus
import com.fames.protokit.runtime.models.GrpcTrailers
import okhttp3.Headers

fun Headers.toGrpcTrailers(): GrpcTrailers {
    val status = this["grpc-status"]?.toIntOrNull()?.let {
        GrpcStatus.fromCode(it)
    } ?: GrpcStatus.UNKNOWN

    return GrpcTrailers(
        status = status,
        message = this["grpc-message"],
        raw = toMultimap().mapValues { it.value.joinToString(",") }
    )
}
