package com.fames.protokit.plugin.codegen

internal object ProtoParser {

    fun parse(content: String): ProtoFile {
        val pkg = Regex("""package\s+([a-zA-Z0-9_.]+);""")
            .find(content)
            ?.groupValues?.get(1)
            ?: ""

        val javaPackage = Regex("""option\s+java_package\s*=\s*"([^"]+)";""")
            .find(content)
            ?.groupValues?.get(1)

        val outerClassName = Regex("""option\s+java_outer_classname\s*=\s*"([^"]+)";""")
            .find(content)
            ?.groupValues?.get(1)

        val messages = parseMessages(content)
        val enums = parseEnums(content)
        val services = parseServices(content)

        return ProtoFile(pkg, javaPackage, outerClassName, messages, enums, services)
    }

    private fun parseMessages(content: String): List<ProtoMessage> {
        val messageRegex = Regex("""message\s+(\w+)\s*\{([^}]*)}""", RegexOption.DOT_MATCHES_ALL)

        return messageRegex.findAll(content).map { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]

            val oneofs = parseOneofs(body)

            val fields = Regex("""\s*(repeated|optional)?\s*(map<\s*(\w+)\s*,\s*(\w+)\s*>|([\w.]+))\s+(\w+)\s*=\s*(\d+);""")
                .findAll(body)
                .map {
                    val oneofIndex = oneofs.indexOfFirst { oneof -> oneof.fields.any { f -> f.name == it.groupValues[5] } }
                    ProtoField(
                        label = it.groupValues[1].ifEmpty { null },
                        type = it.groupValues[2],
                        name = it.groupValues[5],
                        index = it.groupValues[6].toInt(),
                        keyType = it.groupValues[3].ifEmpty { null },
                        valueType = it.groupValues[4].ifEmpty { null },
                        oneofIndex = if (oneofIndex != -1) oneofIndex else null
                    )
                }.toList()

            ProtoMessage(name, fields, oneofs, parseEnums(body))
        }.toList()
    }

    private fun parseEnums(content: String): List<ProtoEnum> {
        val enumRegex = Regex("""enum\s+(\w+)\s*\{([^}]*)}""", RegexOption.DOT_MATCHES_ALL)
        return enumRegex.findAll(content).map { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]

            val fields = Regex("""\s*(\w+)\s*=\s*(\d+);""").findAll(body).map {
                ProtoField(label = null, type = "int32", name = it.groupValues[1], index = it.groupValues[2].toInt())
            }.toList()

            ProtoEnum(name, fields)
        }.toList()
    }

    private fun parseOneofs(content: String): List<ProtoOneOf> {
        val oneofRegex = Regex("""oneof\s+(\w+)\s*\{([^}]*)}""", RegexOption.DOT_MATCHES_ALL)
        return oneofRegex.findAll(content).map { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]

            val fields = Regex("""\s*([\w.]+)\s+(\w+)\s*=\s*(\d+);""").findAll(body).map {
                ProtoField(label = "oneof", type = it.groupValues[1], name = it.groupValues[2], index = it.groupValues[3].toInt())
            }.toList()

            ProtoOneOf(name, fields)
        }.toList()
    }

    private fun parseServices(content: String): List<ProtoService> {
        val serviceRegex = Regex("""service\s+(\w+)\s*\{([^}]*)}""", RegexOption.DOT_MATCHES_ALL)

        return serviceRegex.findAll(content).map { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]

            val rpcs = Regex(
                """rpc\s+(\w+)\s*\(\s*(\w+)\s*\)\s*returns\s*\(\s*(\w+)\s*\)"""
            ).findAll(body).map {
                ProtoRpc(
                    name = it.groupValues[1],
                    requestType = it.groupValues[2],
                    responseType = it.groupValues[3]
                )
            }.toList()

            ProtoService(name, rpcs)
        }.toList()
    }
}
