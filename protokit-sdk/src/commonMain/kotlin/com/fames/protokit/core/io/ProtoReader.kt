package com.fames.protokit.core.io

import kotlin.experimental.and

class ProtoReader(private val bytes: ByteArray) {
    private var pos = 0

    fun readTag(): Pair<Int, Int>? {
        if (pos >= bytes.size) return null
        val key = readVarInt().toInt()
        val fieldNumber = key ushr 3
        val wireType = key and 7
        return Pair(fieldNumber, wireType)
    }

    // --- Primitive Types ---

    fun readDouble(): Double {
        val bits = (bytes[pos].toLong() and 0xFF) or
                ((bytes[pos + 1].toLong() and 0xFF) shl 8) or
                ((bytes[pos + 2].toLong() and 0xFF) shl 16) or
                ((bytes[pos + 3].toLong() and 0xFF) shl 24) or
                ((bytes[pos + 4].toLong() and 0xFF) shl 32) or
                ((bytes[pos + 5].toLong() and 0xFF) shl 40) or
                ((bytes[pos + 6].toLong() and 0xFF) shl 48) or
                ((bytes[pos + 7].toLong() and 0xFF) shl 56)
        pos += 8
        return Double.fromBits(bits)
    }

    fun readFloat(): Float {
        val bits = (bytes[pos].toInt() and 0xFF) or
                ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return Float.fromBits(bits)
    }

    fun readInt64(): Long = readVarInt()

    fun readUInt64(): Long = readVarInt()

    fun readSInt64(): Long {
        val zigzag = readVarInt()
        return (zigzag ushr 1) xor -(zigzag and 1)
    }

    fun readInt32(): Int = readVarInt().toInt()

    fun readUInt32(): Int = readVarInt().toInt()

    fun readSInt32(): Int {
        val zigzag = readVarInt()
        val value = (zigzag ushr 1) xor -(zigzag and 1)
        return value.toInt()
    }

    fun readFixed64(): Long = readDouble().toBits()

    fun readFixed32(): Int = readFloat().toBits()

    fun readBool(): Boolean = readVarInt() != 0L

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

    fun readEnum(): Int = readInt32()

    fun skip(wireType: Int) {
        when (wireType) {
            0 -> readVarInt() // Varint
            1 -> pos += 8      // 64-bit
            2 -> pos += readVarInt().toInt() // Length-delimited
            3 -> {             // Start group
                while (true) {
                    val (_, nextWire) = readTag() ?: break
                    if (nextWire == 4) break // End group
                    skip(nextWire)
                }
            }
            4 -> return        // End group: do nothing, handled by start group.
            5 -> pos += 4      // 32-bit
            //else -> error("Unsupported wire type: $wireType")
        }
    }

    private fun readVarInt(): Long {
        var shift = 0
        var result = 0L
        while (shift < 64) {
            if (pos >= bytes.size) error("Malformed varint: premature end of stream")
            val b = bytes[pos++]
            result = result or ((b.toLong() and 0x7F) shl shift)
            if ((b and 0x80.toByte()) == 0.toByte()) return result
            shift += 7
        }
        error("Malformed varint")
    }
}
