package com.fames.protokit.runtime

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
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
