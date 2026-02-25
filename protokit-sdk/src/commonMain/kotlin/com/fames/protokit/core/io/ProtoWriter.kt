package com.fames.protokit.core.io

class ProtoWriter {

    private val buffer = mutableListOf<Byte>()

    fun writeTag(fieldNumber: Int, wireType: Int) {
        val key = (fieldNumber shl 3) or wireType
        writeVarInt(key.toLong())
    }

    // --- Primitive Types ---

    fun writeDouble(fieldNumber: Int, value: Double) {
        if (value == 0.0) return
        writeTag(fieldNumber, 1) // 64-bit
        val bits = value.toBits()
        buffer.add((bits and 0xFF).toByte())
        buffer.add((bits ushr 8 and 0xFF).toByte())
        buffer.add((bits ushr 16 and 0xFF).toByte())
        buffer.add((bits ushr 24 and 0xFF).toByte())
        buffer.add((bits ushr 32 and 0xFF).toByte())
        buffer.add((bits ushr 40 and 0xFF).toByte())
        buffer.add((bits ushr 48 and 0xFF).toByte())
        buffer.add((bits ushr 56 and 0xFF).toByte())
    }

    fun writeFloat(fieldNumber: Int, value: Float) {
        if (value == 0.0f) return
        writeTag(fieldNumber, 5) // 32-bit
        val bits = value.toBits()
        buffer.add((bits and 0xFF).toByte())
        buffer.add((bits ushr 8 and 0xFF).toByte())
        buffer.add((bits ushr 16 and 0xFF).toByte())
        buffer.add((bits ushr 24 and 0xFF).toByte())
    }

    fun writeInt64(fieldNumber: Int, value: Long) {
        if (value == 0L) return
        writeTag(fieldNumber, 0) // Varint
        writeVarInt(value)
    }

    fun writeUInt64(fieldNumber: Int, value: Long) = writeInt64(fieldNumber, value) // No special encoding for unsigned in Kotlin

    fun writeSInt64(fieldNumber: Int, value: Long) {
        if (value == 0L) return
        writeTag(fieldNumber, 0) // Varint
        writeVarInt((value shl 1) xor (value shr 63)) // ZigZag encoding
    }

    fun writeInt32(fieldNumber: Int, value: Int) {
        if (value == 0) return
        writeTag(fieldNumber, 0) // Varint
        writeVarInt(value.toLong())
    }

    fun writeUInt32(fieldNumber: Int, value: Int) = writeInt32(fieldNumber, value)

    fun writeSInt32(fieldNumber: Int, value: Int) {
        if (value == 0) return
        writeTag(fieldNumber, 0) // Varint
        writeVarInt(((value shl 1) xor (value shr 31)).toLong()) // ZigZag encoding
    }

    fun writeFixed64(fieldNumber: Int, value: Long) = writeDouble(fieldNumber, Double.fromBits(value)) // Simplified

    fun writeFixed32(fieldNumber: Int, value: Int) = writeFloat(fieldNumber, Float.fromBits(value)) // Simplified

    fun writeBool(fieldNumber: Int, value: Boolean) {
        if (!value) return
        writeTag(fieldNumber, 0)
        writeVarInt(1L)
    }

    fun writeString(fieldNumber: Int, value: String) {
        if (value.isEmpty()) return
        writeTag(fieldNumber, 2) // Length-delimited
        val bytes = value.encodeToByteArray()
        writeVarInt(bytes.size.toLong())
        buffer.addAll(bytes.toList())
    }

    fun writeBytes(fieldNumber: Int, value: ByteArray) {
        if (value.isEmpty()) return
        writeTag(fieldNumber, 2) // Length-delimited
        writeVarInt(value.size.toLong())
        buffer.addAll(value.toList())
    }
    
    fun writeObject(tag: Int, writer: () -> ByteArray) {
        val bytes = writer()
        if (bytes.isEmpty()) return
        writeTag(tag, 2)
        writeVarInt(bytes.size.toLong())
        buffer.addAll(bytes.toList())
    }

    fun writeEnum(fieldNumber: Int, value: Int) {
        if (value == 0) return
        writeInt32(fieldNumber, value)
    }

    private fun writeVarInt(value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                buffer.add(v.toByte())
                return
            }
            buffer.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}
