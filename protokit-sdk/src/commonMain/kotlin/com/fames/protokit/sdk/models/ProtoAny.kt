package com.fames.protokit.sdk.models

import com.fames.protokit.core.io.ProtoReader
import com.fames.protokit.core.io.ProtoWriter

/**
 * Represents the `google.protobuf.Any` type, allowing for the serialization
 * of any proto message along with its type URL.
 */
public data class ProtoAny(
    /**
     * A URL that identifies the type of the serialized message.
     * The format is `type.googleapis.com/full.qualified.name`.
     */
    public val typeUrl: String,
    /**
     * The serialized protobuf message data.
     */
    public val value: ByteArray,
) {
    public fun encode(): ByteArray {
        val writer = ProtoWriter()
        writer.writeString(1, typeUrl)
        writer.writeBytes(2, value)
        return writer.toByteArray()
    }

    public companion object {
        public fun decode(bytes: ByteArray): ProtoAny = decode(ProtoReader(bytes))

        public fun decode(reader: ProtoReader): ProtoAny {
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