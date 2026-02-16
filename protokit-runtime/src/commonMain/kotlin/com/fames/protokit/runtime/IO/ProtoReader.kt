package com.fames.protokit.runtime.IO

class ProtoReader(
    private val bytes: ByteArray
) {
    private var pos = 0

    fun readString(tag: Int): String =
        find(tag)?.let {
            val len = readVarInt()
            val str = bytes.copyOfRange(pos, pos + len).decodeToString()
            pos += len
            str
        } ?: ""

    fun readInt32(tag: Int): Int =
        find(tag)?.let { readVarInt() } ?: 0

    fun readBool(tag: Int): Boolean =
        readInt32(tag) != 0

    private fun find(tag: Int): Boolean {
        pos = 0
        while (pos < bytes.size) {
            val key = readVarInt()
            val field = key shr 3
            val wire = key and 7
            if (field == tag) return true
            skip(wire)
        }
        return false
    }

    private fun skip(wire: Int) {
        when (wire) {
            0 -> readVarInt()
            2 -> pos += readVarInt()
            else -> error("Unsupported wire type")
        }
    }

    private fun readVarInt(): Int {
        var shift = 0
        var result = 0
        while (true) {
            val b = bytes[pos++].toInt()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
}
