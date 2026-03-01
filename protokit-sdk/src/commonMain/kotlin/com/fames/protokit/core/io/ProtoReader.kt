package com.fames.protokit.core.io
class ProtoReader(private val bytes: ByteArray) {
    private var pos = 0

    fun readTag(): Pair<Int, Int>? {
        if (pos >= bytes.size) return null
        val key = readVarInt().toInt()
        val field = key ushr 3
        val wire = key and 7
        return Pair(field, wire)
    }

    fun readString(): String {
        val len = readVarInt().toInt()
        val str = bytes.decodeToString(pos, pos + len)
        pos += len
        return str
    }

    fun readBytes(): ByteArray {
        val len = readVarInt().toInt()
        val result = bytes.copyOfRange(pos, pos + len)
        pos += len
        return result
    }

    fun readObject(): ProtoReader {
        val len = readVarInt().toInt()
        val reader = ProtoReader(bytes.copyOfRange(pos, pos + len))
        pos += len
        return reader
    }

    fun readInt32(): Int = readVarInt().toInt()

    fun readEnum(): Int = readInt32()

    fun readInt64(): Long = readVarInt()

    fun readBool(): Boolean = readVarInt() != 0L

    fun readFloat(): Float {
        val value = Float.fromBits(
            (bytes[pos].toInt() and 0xFF) or
                    ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 3].toInt() and 0xFF) shl 24)
        )
        pos += 4
        return value
    }

    fun readDouble(): Double {
        val value = Double.fromBits(
            (bytes[pos].toLong() and 0xFF) or
                    ((bytes[pos + 1].toLong() and 0xFF) shl 8) or
                    ((bytes[pos + 2].toLong() and 0xFF) shl 16) or
                    ((bytes[pos + 3].toLong() and 0xFF) shl 24) or
                    ((bytes[pos + 4].toLong() and 0xFF) shl 32) or
                    ((bytes[pos + 5].toLong() and 0xFF) shl 40) or
                    ((bytes[pos + 6].toLong() and 0xFF) shl 48) or
                    ((bytes[pos + 7].toLong() and 0xFF) shl 56)
        )
        pos += 8
        return value
    }

    fun skip(wire: Int) {
        when (wire) {
            0 -> readVarInt() // Skip varint
            1 -> pos += 8      // Skip 64-bit
            2 -> pos += readVarInt().toInt() // Skip length-delimited
            5 -> pos += 4      // Skip 32-bit
            else -> error("Unsupported wire type: $wire")
        }
    }

    private fun readVarInt(): Long {
        var shift = 0
        var result = 0L
        while (shift < 64) {
            val b = bytes[pos++]
            result = result or ((b.toLong() and 0x7F) shl shift)
            if ((b.toInt() and 0x80) == 0) return result
            shift += 7
        }
        error("Malformed varint")
    }
}
