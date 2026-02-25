package com.fames.protokit.sdk.models


sealed class Response<out T> {

    data class Success<out T>(
        val value: T,
        val trailers: GrpcTrailers
    ) : Response<T>()

    data class Failure(
        val error: CallError
    ) : Response<Nothing>()

}

data class CallError(
    val status: GrpcStatus,
    val message: String?,
    val trailers: GrpcTrailers
)

inline fun <T> Response<T>.onSuccess(action: (T) -> Unit): Response<T> {
    if (this is Response.Success) action(value)
    return this
}

inline fun <T> Response<T>.onFailure(action: (CallError) -> Unit): Response<T> {
    if (this is Response.Failure) action(this.error)
    return this
}

fun <T> Response<T>.isSuccess(): Boolean =
    this is Response.Success

fun <T> Response<T>.isFailure(): Boolean =
    this is Response.Failure

fun <T> Response<T>.getModelOrNull(): T? =
    (this as? Response.Success)?.value

fun <T> Response<T>.getTrailers(): GrpcTrailers {
    return when (this) {
        is Response.Success -> trailers
        is Response.Failure -> error.trailers
    }
}

fun <T> Response<T>.getError(): CallError? =
    (this as? Response.Failure)?.error

inline fun <T, R> Response<T>.map(transform: (T) -> R): Response<R> =
    when (this) {
        is Response.Success ->
            Response.Success(
                value = transform(value),
                trailers = trailers
            )
        is Response.Failure -> this
    }
