package com.fames.protokit.codegen

data class ProtoFile(
    val pkg: String,
    val messages: List<ProtoMessage>,
    val services: List<ProtoService>
)

data class ProtoMessage(
    val name: String,
    val fields: List<ProtoField>
)

data class ProtoField(
    val type: String,
    val name: String,
    val index: Int
)

data class ProtoService(
    val name: String,
    val rpcs: List<ProtoRpc>
)

data class ProtoRpc(
    val name: String,
    val requestType: String,
    val responseType: String
)
