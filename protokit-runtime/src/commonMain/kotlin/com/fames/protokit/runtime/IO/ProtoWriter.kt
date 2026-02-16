package com.fames.protokit.runtime.IO

class ProtoWriter {

    private val buffer = mutableListOf<Byte>()

    fun writeString(tag: Int, value: String) {
        writeTag(tag, 2)
        val bytes = value.encodeToByteArray()
        writeVarInt(bytes.size)
        buffer.addAll(bytes.toList())
    }

    fun writeInt32(tag: Int, value: Int) {
        writeTag(tag, 0)
        writeVarInt(value)
    }

    fun writeBool(tag: Int, value: Boolean) {
        writeInt32(tag, if (value) 1 else 0)
    }

    private fun writeTag(tag: Int, wireType: Int) =
        writeVarInt((tag shl 3) or wireType)

    private fun writeVarInt(value: Int) {
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                buffer.add(v.toByte())
                return
            }
            buffer.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}
