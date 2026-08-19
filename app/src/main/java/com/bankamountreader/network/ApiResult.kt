package com.bankamountreader.network

sealed class ApiResult {
    data class Success(val code: Int, val body: String)   : ApiResult()
    data class ServerError(val code: Int, val body: String) : ApiResult()
    data class NetworkError(val message: String)           : ApiResult()

    fun isSuccess() = this is Success

    fun summary(): String = when (this) {
        is Success      -> "OK [$code]"
        is ServerError  -> "Server error [$code]: $body"
        is NetworkError -> "Network error: $message"
    }
}
