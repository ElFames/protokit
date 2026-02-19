package com.fames.protokit.plugin.codegen

internal object KotlinGenerator {

    fun generate(proto: ProtoFile): Map<String, String> {
        val files = mutableMapOf<String, String>()
        val kotlinPackage = proto.javaPackage ?: proto.pkg
        val enumNames = proto.enums.map { it.name }.toSet()

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
                    appendLine(generateMessage(it, enumNames, isNested = true).prependIndent("    "))
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
                files["${it.name}.kt"] = generateMessage(it, enumNames, kotlinPackage = kotlinPackage)
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

    private fun generateMessage(
        msg: ProtoMessage,
        enumNames: Set<String>,
        kotlinPackage: String? = null,
        isNested: Boolean = false
    ): String {
        val oneofSealedClasses = msg.oneofs.joinToString("\n") { generateOneof(it) }

        val fields = msg.fields.joinToString(",\n") { f ->
            "    val ${f.name}: ${mapType(f, f.label == "repeated")}"
        }

        val encoder = msg.fields.joinToString("\n") { f ->
            if (f.label == "repeated") {
                "            ${f.name}.forEach { ${protoWriteRepeated(f, "it", enumNames)} }"
            } else {
                protoWriteLine(f, enumNames)
            }
        }

        val decoderVars = msg.fields.map { f ->
            val initialValue = if (f.label == "repeated") "mutableListOf()" else protoDefault(f, enumNames)
            "            var ${f.name}: ${mapType(f, f.label == "repeated", isMutable = true)} = $initialValue"
        }.joinToString("\n")

        val readerCases = msg.fields.map { f ->
            val readCall = if (f.label == "repeated") {
                "                ${f.name}.add(${protoReadSingle(f.type, f.keyType, f.valueType, enumNames)})"
            } else {
                "                ${f.name} = ${protoReadSingle(f.type, f.keyType, f.valueType, enumNames)}"
            }
            "                ${f.index} -> {\n$readCall\n                }"
        }.joinToString("\n")

        val defaultInstanceFields = msg.fields.joinToString(",\n") { f ->
            val defaultValue = if (f.label == "repeated") "emptyList()" else protoDefault(f, enumNames)
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
            ${defaultInstance.prependIndent("            ")}

            internal fun decode(bytes: ByteArray): ${msg.name} = decode(ProtoReader(bytes))

            internal fun decode(reader: ProtoReader): ${msg.name} {
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
        val name = oneof.name.replaceFirstChar { it.uppercase() }
        val fields = oneof.fields.joinToString("\n") { f ->
            "    data class ${f.name.replaceFirstChar { it.uppercase() }}(val value: ${mapType(f)}): $name()"
        }
        return "sealed class $name {\n$fields\n}"
    }

    private fun generateService(
        protoPackage: String,
        svc: ProtoService,
        kotlinPackage: String? = null,
        isNested: Boolean = false
    ): String {
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

    private fun protoWriteLine(f: ProtoField, enumNames: Set<String>): String = protoWriteRepeated(f, f.name, enumNames)

    private fun protoWriteRepeated(f: ProtoField, value: String, enumNames: Set<String>): String = when (f.type) {
        "string" -> "            writeString(${f.index}, $value)"
        "bytes" -> "            writeBytes(${f.index}, $value)"
        "bool" -> "            writeBool(${f.index}, $value)"
        "int32", "sint32", "fixed32", "sfixed32" -> "            writeInt32(${f.index}, $value)"
        "int64", "sint64", "fixed64", "sfixed64" -> "            writeInt64(${f.index}, $value)"
        "float" -> "            writeFloat(${f.index}, $value)"
        "double" -> "            writeDouble(${f.index}, $value)"
        else -> {
            if (f.keyType != null) { // map
                "            writeObject(${f.index}) { ProtoWriter().apply { writeString(1, $value.key); write${f.valueType!!.replaceFirstChar { it.uppercase() }}(2, $value.value) }.toByteArray() }"
            } else if (enumNames.contains(f.type)) {
                "            writeInt32(${f.index}, $value.value)"
            } else { // message
                "            writeObject(${f.index}) { $value.encode() }"
            }
        }
    }

    private fun protoReadSingle(type: String, keyType: String?, valueType: String?, enumNames: Set<String>): String =
        when (type) {
            "string" -> "reader.readString()"
            "bytes" -> "reader.readBytes()"
            "bool" -> "reader.readBool()"
            "int32", "sint32", "fixed32", "sfixed32" -> "reader.readInt32()"
            "int64", "sint64", "fixed64", "sfixed64" -> "reader.readInt64()"
            "float" -> "reader.readFloat()"
            "double" -> "reader.readDouble()"
            else -> {
                if (keyType != null) { // map
                    "reader.readObject().let { val r = ProtoReader(it); val k = r.readString(); val v = r.read${valueType!!.replaceFirstChar { it.uppercase() }}(); k to v }"
                } else if (enumNames.contains(type)) {
                    "$type.fromValue(reader.readInt32())!!"
                } else { // message
                    "$type.decode(reader.readObject())"
                }
            }
        }

    private fun protoDefault(f: ProtoField, enumNames: Set<String>): String = when (f.type) {
        "string" -> "\"\""
        "bytes" -> "ByteArray(0)"
        "bool" -> "false"
        "int32", "sint32", "fixed32", "sfixed32" -> "0"
        "int64", "sint64", "fixed64", "sfixed64" -> "0L"
        "float" -> "0f"
        "double" -> "0.0"
        else -> {
            if (f.keyType != null) { // map
                "emptyMap()"
            } else if (enumNames.contains(f.type)) {
                "${f.type}.values().first()"
            } else { // message
                "${f.type}.defaultInstance"
            }
        }
    }

    private fun mapBaseType(type: String): String = when (type) {
        "string" -> "String"
        "bytes" -> "ByteArray"
        "bool" -> "Boolean"
        "int32", "sint32", "fixed32", "sfixed32" -> "Int"
        "int64", "sint64", "fixed64", "sfixed64" -> "Long"
        "float" -> "Float"
        "double" -> "Double"
        else -> type
    }

    private fun mapType(f: ProtoField, isList: Boolean = false, isMutable: Boolean = false): String {
        val baseType = when {
            f.keyType != null -> { // map
                val key = mapBaseType(f.keyType!!)
                val value = mapBaseType(f.valueType!!)
                "Map<$key, $value>"
            }
            f.oneofIndex != null -> {
                f.type.replaceFirstChar { it.uppercase() }
            }
            else -> mapBaseType(f.type)
        }

        return when {
            isList && isMutable -> "MutableList<$baseType>"
            isList -> "List<$baseType>"
            else -> baseType
        }
    }
}
