package com.fames.protokit.runtime.models

enum class GrpcStatus(val code: Int) {
    OK(0),
    CANCELLED(1),
    UNKNOWN(2),
    INVALID_ARGUMENT(3),
    DEADLINE_EXCEEDED(4),
    NOT_FOUND(5),
    ALREADY_EXISTS(6),
    PERMISSION_DENIED(7),
    UNAUTHENTICATED(16),
    INTERNAL(13),
    UNAVAILABLE(14);

    companion object {
        fun fromCode(code: Int): GrpcStatus = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
