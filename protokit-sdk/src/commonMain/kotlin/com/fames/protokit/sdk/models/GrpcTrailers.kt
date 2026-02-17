package com.fames.protokit.sdk.models

data class GrpcTrailers(
    val status: GrpcStatus,
    val message: String?,
    val raw: Map<String, String>
)