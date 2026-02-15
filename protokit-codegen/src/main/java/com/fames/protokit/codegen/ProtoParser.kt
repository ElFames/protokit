package com.fames.protokit.codegen

class ProtoParser {

    fun parse(content: String): ProtoFile {
        val pkg = Regex("""package\s+([a-zA-Z0-9_.]+);""")
            .find(content)
            ?.groupValues?.get(1)
            ?: ""

        val messages = parseMessages(content)
        val services = parseServices(content)

        return ProtoFile(pkg, messages, services)
    }

    private fun parseMessages(content: String): List<ProtoMessage> {
        val messageRegex = Regex("""message\s+(\w+)\s*\{([^}]*)}""", RegexOption.DOT_MATCHES_ALL)

        return messageRegex.findAll(content).map { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]

            val fields = Regex("""(\w+)\s+(\w+)\s*=\s*(\d+);""")
                .findAll(body)
                .map {
                    ProtoField(
                        type = it.groupValues[1],
                        name = it.groupValues[2],
                        index = it.groupValues[3].toInt()
                    )
                }.toList()

            ProtoMessage(name, fields)
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
