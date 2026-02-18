package com.fames.protokit.plugin.codegen

internal data class ProtoFile(
    val pkg: String,
    val javaPackage: String?,
    val outerClassName: String?,
    val messages: List<ProtoMessage>,
    val enums: List<ProtoEnum>,
    val services: List<ProtoService>
)

internal data class ProtoMessage(
    val name: String,
    val fields: List<ProtoField>,
    val oneofs: List<ProtoOneOf>,
    val enums: List<ProtoEnum>
)

internal data class ProtoField(
    val label: String?,
    val type: String,
    val name: String,
    val index: Int,
    val keyType: String? = null,
    val valueType: String? = null,
    val oneofIndex: Int? = null
)

internal data class ProtoOneOf(
    val name: String,
    val fields: List<ProtoField>
)

internal data class ProtoEnum(
    val name: String,
    val fields: List<ProtoField>
)

internal data class ProtoService(
    val name: String,
    val rpcs: List<ProtoRpc>
)

internal data class ProtoRpc(
    val name: String,
    val requestType: String,
    val responseType: String
)
