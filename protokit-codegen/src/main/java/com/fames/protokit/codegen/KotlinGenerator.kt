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

    private fun generateMessage(pkg: String, msg: ProtoMessage): String =
        """
        package $pkg

        data class ${msg.name}(
        ${msg.fields.joinToString(",\n") {
            "    val ${it.name}: ${mapType(it.type)}"
        }}
        )
        """.trimIndent()

    private fun generateService(pkg: String, svc: ProtoService): String =
        """
        package $pkg

        import protokit.runtime.ProtoKitClient

        class ${svc.name}Client(
            private val client: ProtoKitClient
        ) {
        ${svc.rpcs.joinToString("\n\n") { rpc ->
            """
            suspend fun ${rpc.name.replaceFirstChar { it.lowercase() }}(
                request: ${rpc.requestType}
            ): ${rpc.responseType} =
                client.unary(
                    method = "/$pkg.${svc.name}/${rpc.name}",
                    request = request,
                    encoder = ${rpc.requestType}Encoder,
                    decoder = ${rpc.responseType}Decoder
                )
            """.trimIndent()
        }}
        }
        """.trimIndent()

    private fun mapType(type: String): String =
        when (type) {
            "string" -> "String"
            "int32" -> "Int"
            "bool" -> "Boolean"
            else -> type
        }
}
