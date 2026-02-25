package com.fames.protokit.core.io

internal object Framer {

    fun frame(data: ByteArray): ByteArray {
        require(data.size <= Int.MAX_VALUE)

        return ByteArray(5 + data.size).apply {
            this[0] = 0
            val size = data.size
            this[1] = (size ushr 24).toByte()
            this[2] = (size ushr 16).toByte()
            this[3] = (size ushr 8).toByte()
            this[4] = size.toByte()
            data.copyInto(this, 5)
        }
    }

    fun unframe(data: ByteArray): ByteArray {
        require(data.size >= 5) { "Invalid gRPC frame" }

        require(data[0].toInt() == 0) {
            "Compressed gRPC not supported"
        }

        val length =
            ((data[1].toInt() and 0xFF) shl 24) or
                    ((data[2].toInt() and 0xFF) shl 16) or
                    ((data[3].toInt() and 0xFF) shl 8) or
                    (data[4].toInt() and 0xFF)

        require(data.size >= 5 + length) {
            "Corrupted gRPC frame"
        }

        return data.copyOfRange(5, 5 + length)
    }

}