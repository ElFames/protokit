package com.fames.protokit.plugin.codegen

internal object KotlinGenerator {

    fun generate(proto: ProtoFile): Map<String, String> {
        val files = mutableMapOf<String, String>()

        proto.messages.forEach { msg ->
            files["${msg.name}.kt"] = generateMessage(proto.pkg, msg)
        }

        proto.services.forEach { svc ->
            files["${svc.name}Client.kt"] = generateService(proto.pkg, svc)
        }

        return files
    }

    private fun generateMessage(pkg: String, msg: ProtoMessage): String {
        val fields = msg.fields.joinToString(",\n") { f ->
            val type = mapType(f.type, isList = f.label == "repeated")
            "    val ${f.name}: $type"
        }

        val encoder = msg.fields.joinToString("\n") { f ->
            if (f.label == "repeated") {
                "            ${f.name}.forEach { ${protoWriteRepeated(f, "it")} }"
            } else {
                protoWriteLine(f)
            }
        }

        val decoderVars = msg.fields.map { f ->
            val initialValue = if (f.label == "repeated") "mutableListOf()" else protoDefault(f)
            "            var ${f.name}: ${mapType(f.type, isMutable = true, isList = f.label == "repeated")} = $initialValue"
        }.joinToString("\n")

        val readerCases = msg.fields.map { f ->
            val readCall = if (f.label == "repeated") {
                "                ${f.name}.add(${protoReadSingle(f.type)})"
            } else {
                "                ${f.name} = ${protoReadSingle(f.type)}"
            }
            "                ${f.index} -> {\n$readCall\n                }"
        }.joinToString("\n")

        val defaultInstanceFields = msg.fields.joinToString(",\n") { f ->
            val defaultValue = if (f.label == "repeated") "emptyList()" else protoDefault(f)
            "            ${f.name} = $defaultValue"
        }

        val defaultInstance = """
        val defaultInstance = ${msg.name}(
$defaultInstanceFields
        )
""".trimIndent()

        return """
package $pkg

import com.fames.protokit.core.io.ProtoReader
import com.fames.protokit.core.io.ProtoWriter

data class ${msg.name}(
$fields
) {
    internal fun encode(): ByteArray =
        ProtoWriter().apply {
$encoder
        }.toByteArray()

    companion object {
$defaultInstance

        internal fun decode(bytes: ByteArray): ${msg.name} {
            val reader = ProtoReader(bytes)
$decoderVars

            while(true) {
                val tag = reader.readTag()
                if (tag == null) break
                when (tag.first) {
$readerCases
                    else -> reader.skip(tag.second)
                }
            }

            return ${msg.name}(
${msg.fields.joinToString(",\n") { "                ${it.name} = ${it.name}" }}
            )
        }
    }
}
""".trimIndent()
    }

    private fun generateService(pkg: String, svc: ProtoService): String {
        // This part is unchanged and kept for completeness, but truncated in display
        return """
package $pkg

import com.fames.protokit.sdk.ProtoClient
import com.fames.protokit.sdk.models.Response

class ${svc.name}Client(
    private val client: ProtoClient
) {

${svc.rpcs.joinToString("\n\n") { rpc ->
    "    suspend fun ${rpc.name.replaceFirstChar { it.lowercase() }}(\n" +
    "        request: ${rpc.requestType}\n" +
    "    ): Response<${rpc.responseType}> =\n" +
    "        client.unary(\n" +
    "            method = \"/$pkg.${svc.name}/${rpc.name}\",\n" +
    "            request = request,\n" +
    "            encoder = { it.encode() },\n" +
    "            decoder = { bytes -> ${rpc.responseType}.decode(bytes) }\n" +
    "        )"
}}
}
"""
    }

    private fun protoWriteLine(f: ProtoField): String =
        protoWriteRepeated(f, f.name)

    private fun protoWriteRepeated(f: ProtoField, value: String): String = when (f.type) {
        "string" -> "            writeString(${f.index}, $value)"
        "int32"  -> "            writeInt32(${f.index}, $value)"
        "int64"  -> "            writeInt64(${f.index}, $value)"
        "bool"   -> "            writeBool(${f.index}, $value)"
        "float"  -> "            writeFloat(${f.index}, $value)"
        "double" -> "            writeDouble(${f.index}, $value)"
        "bytes"  -> "            writeBytes(${f.index}, $value)"
        else     -> "            writeObject(${f.index}, $value.encode())"
    }

    private fun protoReadSingle(type: String): String = when (type) {
        "string" -> "reader.readString()"
        "int32"  -> "reader.readInt32()"
        "int64"  -> "reader.readInt64()"
        "bool"   -> "reader.readBool()"
        "float"  -> "reader.readFloat()"
        "double" -> "reader.readDouble()"
        "bytes"  -> "reader.readBytes()"
        else     -> "$type.decode(reader.readBytes())"
    }

    private fun protoDefault(f: ProtoField): String = when (f.type) {
        "string" -> "\"\""
        "int32"  -> "0"
        "int64"  -> "0L"
        "bool"   -> "false"
        "float"  -> "0f"
        "double" -> "0.0"
        "bytes"  -> "ByteArray(0)"
        else     -> "${f.type}.defaultInstance"
    }

    private fun mapType(type: String, isMutable: Boolean = false, isList: Boolean = false): String {
        val baseType = when (type) {
            "string" -> "String"
            "int32"  -> "Int"
            "int64"  -> "Long"
            "bool"   -> "Boolean"
            "float"  -> "Float"
            "double" -> "Double"
            "bytes"  -> "ByteArray"
            else     -> type
        }
        return when {
            isList && isMutable -> "MutableList<$baseType>"
            isList              -> "List<$baseType>"
            else                -> baseType
        }
    }
}
