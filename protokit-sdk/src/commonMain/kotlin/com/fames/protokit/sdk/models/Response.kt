package com.fames.protokit.sdk.models

import com.fames.protokit.sdk.models.GrpcTrailers

sealed class Response<out T> {

    data class Success<out T>(
        val value: T,
        val trailers: GrpcTrailers
    ) : Response<T>()

    data class Failure(
        val status: GrpcStatus,
        val message: String?,
        val trailers: GrpcTrailers
    ) : Response<Nothing>()

}

inline fun <T> Response<T>.onSuccess(action: (T) -> Unit): Response<T> {
    if (this is Response.Success) action(value)
    return this
}

inline fun <T> Response<T>.onFailure(action: (Response.Failure) -> Unit): Response<T> {
    if (this is Response.Failure) action(this)
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
        is Response.Failure -> trailers
    }
}

fun <T> Response<T>.getError(): Response.Failure? =
    this as? Response.Failure

inline fun <T, R> Response<T>.map(transform: (T) -> R): Response<R> =
    when (this) {
        is Response.Success ->
            Response.Success(
                value = transform(value),
                trailers = trailers
            )
        is Response.Failure -> this
    }
