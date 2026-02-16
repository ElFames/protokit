package com.fames.protokit.codegen

class KotlinGenerator {

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

    // ------------------------------------------------------------
    // MESSAGE
    // ------------------------------------------------------------

    private fun generateMessage(pkg: String, msg: ProtoMessage): String =
        """
package $pkg

import com.fames.protokit.runtime.io.ProtoReader
import com.fames.protokit.runtime.io.ProtoWriter

data class ${msg.name}(
${msg.fields.joinToString(",\n") {
            "    val ${it.name}: ${mapType(it.type)}"
        }}
) {

    internal fun encode(): ByteArray =
        ProtoWriter().apply {
${msg.fields.joinToString("\n") {
            protoWriteLine(it)
        }}
        }.toByteArray()

    companion object {

        internal fun decode(bytes: ByteArray): ${msg.name} {
            val reader = ProtoReader(bytes)
            return ${msg.name}(
${msg.fields.joinToString(",\n") {
            "                ${it.name} = ${protoReadLine(it)}"
        }}
            )
        }
    }
}
""".trimIndent()

    // ------------------------------------------------------------
    // SERVICE CLIENT
    // ------------------------------------------------------------

    private fun generateService(pkg: String, svc: ProtoService): String =
        """
package $pkg

import com.fames.protokit.runtime.ProtoClient
import com.fames.protokit.runtime.models.Response

class ${svc.name}Client(
    private val client: ProtoClient
) {

${svc.rpcs.joinToString("\n\n") { rpc ->
            """
    suspend fun ${rpc.name.replaceFirstChar { it.lowercase() }}(
        request: ${rpc.requestType}
    ): Response<${rpc.responseType}> =
        client.unary(
            method = "/$pkg.${svc.name}/${rpc.name}",
            request = request,
            encoder = { it.encode() },
            decoder = { bytes -> ${rpc.responseType}.decode(bytes) }
        )
""".trimIndent()
        }}
}
""".trimIndent()

    // ------------------------------------------------------------
    // PROTO FIELD HELPERS
    // ------------------------------------------------------------

    private fun protoWriteLine(f: ProtoField): String =
        when (f.type) {
            "string" -> "            writeString(${f.index}, ${f.name})"
            "int32"  -> "            writeInt32(${f.index}, ${f.name})"
            "bool"   -> "            writeBool(${f.index}, ${f.name})"
            else -> error("Unsupported proto type: ${f.type}")
        }

    private fun protoReadLine(f: ProtoField): String =
        when (f.type) {
            "string" -> "reader.readString(${f.index})"
            "int32"  -> "reader.readInt32(${f.index})"
            "bool"   -> "reader.readBool(${f.index})"
            else -> error("Unsupported proto type: ${f.type}")
        }

    private fun mapType(type: String): String =
        when (type) {
            "string" -> "String"
            "int32"  -> "Int"
            "bool"   -> "Boolean"
            else -> type
        }
}
