package com.fames.protokit.core.io

class ProtoWriter {

    private val buffer = mutableListOf<Byte>()

    fun writeTag(tag: Int, wireType: Int) {
        writeVarInt(((tag shl 3) or wireType).toLong())
    }

    fun writeString(tag: Int, value: String) {
        if (value.isEmpty()) return
        writeTag(tag, 2)
        val bytes = value.encodeToByteArray()
        writeVarInt(bytes.size.toLong())
        buffer.addAll(bytes.toList())
    }

    fun writeBytes(tag: Int, value: ByteArray) {
        if (value.isEmpty()) return
        writeTag(tag, 2)
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

    fun writeInt32(tag: Int, value: Int) {
        if (value == 0) return
        writeTag(tag, 0)
        writeVarInt(value.toLong())
    }

    fun writeEnum(tag: Int, value: Int) {
        if (value == 0) return
        writeTag(tag, 0)
        writeVarInt(value.toLong())
    }

    fun writeInt64(tag: Int, value: Long) {
        if (value == 0L) return
        writeTag(tag, 0)
        writeVarInt(value)
    }

    fun writeBool(tag: Int, value: Boolean) {
        if (!value) return
        // Use writeTag and writeVarInt directly to avoid the zero-check in writeInt32
        writeTag(tag, 0)
        writeVarInt(1L)
    }

    fun writeFloat(tag: Int, value: Float) {
        if (value == 0f) return
        writeTag(tag, 5)
        val intValue = value.toBits()
        buffer.add((intValue and 0xFF).toByte())
        buffer.add(((intValue ushr 8) and 0xFF).toByte())
        buffer.add(((intValue ushr 16) and 0xFF).toByte())
        buffer.add(((intValue ushr 24) and 0xFF).toByte())
    }

    fun writeDouble(tag: Int, value: Double) {
        if (value == 0.0) return
        writeTag(tag, 1)
        val longValue = value.toBits()
        buffer.add((longValue and 0xFF).toByte())
        buffer.add(((longValue ushr 8) and 0xFF).toByte())
        buffer.add(((longValue ushr 16) and 0xFF).toByte())
        buffer.add(((longValue ushr 24) and 0xFF).toByte())
        buffer.add(((longValue ushr 32) and 0xFF).toByte())
        buffer.add(((longValue ushr 40) and 0xFF).toByte())
        buffer.add(((longValue ushr 48) and 0xFF).toByte())
        buffer.add(((longValue ushr 56) and 0xFF).toByte())
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