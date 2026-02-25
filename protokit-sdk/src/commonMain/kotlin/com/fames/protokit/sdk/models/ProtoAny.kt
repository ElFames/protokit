package com.fames.protokit.sdk.models

import com.fames.protokit.core.io.ProtoReader
import com.fames.protokit.core.io.ProtoWriter

data class ProtoAny(
    val typeUrl: String,
    val value: ByteArray,
) {
    fun encode(): ByteArray {
        val writer = ProtoWriter()
        writer.writeString(1, typeUrl)
        writer.writeBytes(2, value)
        return writer.toByteArray()
    }

    companion object {
        fun decode(bytes: ByteArray): ProtoAny = decode(ProtoReader(bytes))

        fun decode(reader: ProtoReader): ProtoAny {
            var typeUrl = ""
            var value = ByteArray(0)
            while (true) {
                when (reader.readTag()?.first) {
                    1 -> typeUrl = reader.readString()
                    2 -> value = reader.readBytes()
                    else -> break
                }
            }
            return ProtoAny(typeUrl, value)
        }
    }
}