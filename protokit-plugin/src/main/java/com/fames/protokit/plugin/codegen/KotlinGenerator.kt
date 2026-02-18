package com.fames.protokit.plugin.codegen

internal object KotlinGenerator {

    fun generate(proto: ProtoFile): Map<String, String> {
        val files = mutableMapOf<String, String>()
        val kotlinPackage = proto.javaPackage ?: proto.pkg

        if (proto.outerClassName != null) {
            val outerClassName = proto.outerClassName
            val content = buildString {
                appendLine("package $kotlinPackage")
                appendLine()
                appendImports(this, proto)

                appendLine("object $outerClassName {")

                proto.enums.forEach {
                    appendLine(generateEnum(it, isNested = true).prependIndent("    "))
                }
                proto.messages.forEach {
                    appendLine(generateMessage(it, isNested = true).prependIndent("    "))
                }
                proto.services.forEach {
                    appendLine(generateService(proto.pkg, it, isNested = true).prependIndent("    "))
                }
                appendLine("}")
            }
            files["${outerClassName}.kt"] = content
        } else {
            proto.enums.forEach {
                files["${it.name}.kt"] = generateEnum(it, kotlinPackage = kotlinPackage)
            }
            proto.messages.forEach {
                files["${it.name}.kt"] = generateMessage(it, kotlinPackage = kotlinPackage)
            }
            proto.services.forEach {
                files["${it.name}Client.kt"] = generateService(proto.pkg, it, kotlinPackage = kotlinPackage)
            }
        }

        return files
    }

    private fun appendImports(builder: StringBuilder, proto: ProtoFile) {
        if (proto.messages.isNotEmpty()) {
            builder.appendLine("import com.fames.protokit.core.io.ProtoReader")
            builder.appendLine("import com.fames.protokit.core.io.ProtoWriter")
        }
        if (proto.services.isNotEmpty()) {
            builder.appendLine("import com.fames.protokit.sdk.ProtoClient")
            builder.appendLine("import com.fames.protokit.sdk.models.Response")
        }
        builder.appendLine()
    }

    private fun generateEnum(enum: ProtoEnum, kotlinPackage: String? = null, isNested: Boolean = false): String {
        val enumFields = enum.fields.joinToString(",\n") { "    ${it.name}(${it.index})" }
        val classContent = """
enum class ${enum.name}(val value: Int) {
$enumFields;

    companion object {
        fun fromValue(value: Int): ${enum.name}? = values().find { it.value == value }
    }
}
"""

        return if (isNested) classContent else "package $kotlinPackage\n\n$classContent"
    }

    private fun generateMessage(msg: ProtoMessage, kotlinPackage: String? = null, isNested: Boolean = false): String {
        val oneofSealedClasses = msg.oneofs.joinToString("\n") { generateOneof(it) }

        val fields = msg.fields.joinToString(",\n") { f ->
            "    val ${f.name}: ${mapType(f, f.label == "repeated")}"
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
            "            var ${f.name}: ${mapType(f, f.label == "repeated", isMutable = true)} = $initialValue"
        }.joinToString("\n")

        val readerCases = msg.fields.map { f ->
            val readCall = if (f.label == "repeated") {
                "                ${f.name}.add(${protoReadSingle(f.type, f.keyType, f.valueType)})"
            } else {
                "                ${f.name} = ${protoReadSingle(f.type, f.keyType, f.valueType)}"
            }
            "                ${f.index} -> {\n$readCall\n                }"
        }.joinToString("\n")

        val defaultInstanceFields = msg.fields.joinToString(",\n") { f ->
            val defaultValue = if (f.label == "repeated") "emptyList()" else protoDefault(f)
            "            ${f.name} = $defaultValue"
        }

        val defaultInstance = "val defaultInstance = ${msg.name}(\n$defaultInstanceFields\n)"

        val classContent = """
$oneofSealedClasses

data class ${msg.name}(
$fields
) {
    internal fun encode(): ByteArray =
        ProtoWriter().apply {
$encoder
        }.toByteArray()

    companion object {
        ${defaultInstance.prependIndent("        ")}

        internal fun decode(bytes: ByteArray): ${msg.name} {
            val reader = ProtoReader(bytes)
$decoderVars

            while(true) {
                val tag = reader.readTag() ?: break
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
"""

        return if (isNested) classContent else "package $kotlinPackage\n\nimport com.fames.protokit.core.io.ProtoReader\nimport com.fames.protokit.core.io.ProtoWriter\n\n$classContent"
    }

    private fun generateOneof(oneof: ProtoOneOf): String {
        val name = oneof.name.capitalize()
        val fields = oneof.fields.joinToString("\n") { f ->
            "    data class ${f.name.capitalize()}(val value: ${mapType(f)}): $name()"
        }
        return "sealed class $name {\n$fields\n}"
    }

    private fun generateService(protoPackage: String, svc: ProtoService, kotlinPackage: String? = null, isNested: Boolean = false): String {
        val rpcs = svc.rpcs.joinToString("\n\n") { rpc ->
            """    suspend fun ${rpc.name.replaceFirstChar { it.lowercase() }}(
        request: ${rpc.requestType}
    ): Response<${rpc.responseType}> =
        client.unary(
            method = "/$protoPackage.${svc.name}/${rpc.name}",
            request = request,
            encoder = { it.encode() },
            decoder = { bytes -> ${rpc.responseType}.decode(bytes) }
        )"""
        }

        val classContent = "class ${svc.name}Client(private val client: ProtoClient) {\n$rpcs\n}"

        return if (isNested) classContent else "package $kotlinPackage\n\nimport com.fames.protokit.sdk.ProtoClient\nimport com.fames.protokit.sdk.models.Response\n\n$classContent"
    }

    private fun protoWriteLine(f: ProtoField): String = protoWriteRepeated(f, f.name)

    private fun protoWriteRepeated(f: ProtoField, value: String): String = when (f.type) {
        "string" -> "            writeString(${f.index}, $value)"
        "bytes"  -> "            writeBytes(${f.index}, $value)"
        "bool"   -> "            writeBool(${f.index}, $value)"
        "int32", "sint32", "fixed32", "sfixed32" -> "            writeInt32(${f.index}, $value)"
        "int64", "sint64", "fixed64", "sfixed64" -> "            writeInt64(${f.index}, $value)"
        "float"  -> "            writeFloat(${f.index}, $value)"
        "double" -> "            writeDouble(${f.index}, $value)"
        else     -> {
            if (f.keyType != null) { // map
                "            writeObject(${f.index}) { ProtoWriter().apply { writeString(1, $value.key); write${f.valueType!!.capitalize()}(2, $value.value) }.toByteArray() }"
            } else { // enum or message
                "            writeObject(${f.index}) { $value.encode() }"
            }
        }
    }

    private fun protoReadSingle(type: String, keyType: String?, valueType: String?): String = when (type) {
        "string" -> "reader.readString()"
        "bytes"  -> "reader.readBytes()"
        "bool"   -> "reader.readBool()"
        "int32", "sint32", "fixed32", "sfixed32" -> "reader.readInt32()"
        "int64", "sint64", "fixed64", "sfixed64" -> "reader.readInt64()"
        "float"  -> "reader.readFloat()"
        "double" -> "reader.readDouble()"
        else     -> {
             if (keyType != null) { // map
                "reader.readObject().let { val k = it.readString(); val v = it.read${valueType!!.capitalize()}(); k to v }"
            } else { // enum or message
                "$type.decode(reader.readObject().let { it.toByteArray() })"
            }
        }
    }

    private fun protoDefault(f: ProtoField): String = when (f.type) {
        "string" -> "\"\""
        "bytes"  -> "ByteArray(0)"
        "bool"   -> "false"
        "int32", "sint32", "fixed32", "sfixed32" -> "0"
        "int64", "sint64", "fixed64", "sfixed64" -> "0L"
        "float"  -> "0f"
        "double" -> "0.0"
        else     -> {
            if (f.keyType != null) { // map
                "emptyMap()"
            } else { // enum or message
                "${f.type}.defaultInstance"
            }
        }
    }

    private fun mapType(f: ProtoField, isList: Boolean = false, isMutable: Boolean = false): String {
        val baseType = when (f.type) {
            "string" -> "String"
            "bytes"  -> "ByteArray"
            "bool"   -> "Boolean"
            "int32", "sint32", "fixed32", "sfixed32" -> "Int"
            "int64", "sint64", "fixed64", "sfixed64" -> "Long"
            "float"  -> "Float"
            "double" -> "Double"
            else     -> {
                if (f.keyType != null) { // map
                    val key = f.keyType.let { val f = ProtoField(type = it, name = "", index = 0); mapType(f) }
                    val value = f.valueType.let { val f = ProtoField(type = it!!, name = "", index = 0); mapType(f) }
                    "Map<$key, $value>"
                } else if (f.oneofIndex != null) {
                    f.type.capitalize()
                } else { // enum or message
                    f.type
                }
            }
        }
        return when {
            isList && isMutable -> "MutableList<$baseType>"
            isList              -> "List<$baseType>"
            else                -> baseType
        }
    }
}
