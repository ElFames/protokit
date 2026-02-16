package com.fames.protokit.runtime.models

data class GrpcTrailers(
    val status: GrpcStatus,
    val message: String?,
    val raw: Map<String, String>
)
