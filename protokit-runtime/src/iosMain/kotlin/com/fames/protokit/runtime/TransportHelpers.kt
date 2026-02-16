package com.fames.protokit.runtime

import com.fames.protokit.runtime.models.GrpcStatus
import com.fames.protokit.runtime.models.GrpcTrailers
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLResponse
import platform.Foundation.create
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun ByteArray.toNSData(): NSData =
    memScoped {
        NSData.create(
            bytes = allocArrayOf(this@toNSData),
            length = size.toULong()
        )
    }

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val bytes = ByteArray(length)
    memScoped {
        memcpy(bytes.refTo(0), this@toByteArray.bytes, length.convert())
    }
    return bytes
}

fun NSURLResponse?.toGrpcTrailers(): GrpcTrailers {
    val http = this as? NSHTTPURLResponse
    val headers = http?.allHeaderFields
        ?.mapKeys { it.key.toString() }
        ?.mapValues { it.value.toString() }
        ?: emptyMap()

    val status =
        headers["grpc-status"]?.toIntOrNull()?.let {
            GrpcStatus.fromCode(it)
        } ?: GrpcStatus.UNKNOWN

    return GrpcTrailers(
        status = status,
        message = headers["grpc-message"],
        raw = headers
    )
}
