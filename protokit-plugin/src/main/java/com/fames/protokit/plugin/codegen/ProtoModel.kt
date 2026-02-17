package com.fames.protokit.plugin.codegen

internal data class ProtoFile(
    val pkg: String,
    val messages: List<ProtoMessage>,
    val services: List<ProtoService>
)

internal data class ProtoMessage(
    val name: String,
    val fields: List<ProtoField>
)

internal data class ProtoField(
    val type: String,
    val name: String,
    val index: Int
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
