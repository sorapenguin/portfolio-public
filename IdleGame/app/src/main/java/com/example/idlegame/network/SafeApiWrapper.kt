package com.example.idlegame.network

import retrofit2.HttpException
import java.io.IOException

/**
 * API 呼び出し結果を 3 状態で表現する。
 * ゲームロジックは Success 以外を無視するだけでよい。
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String, val code: Int? = null) : ApiResult<Nothing>()
    object Offline : ApiResult<Nothing>()
}

/** null を返す拡張。ゲーム側でサーバー値が不要なときに使う。 */
fun <T> ApiResult<T>.dataOrNull(): T? = if (this is ApiResult.Success) data else null

/**
 * API 呼び出しを安全にラップする。
 * - ネットワーク未接続 → Offline（ゲーム動作に影響しない）
 * - HTTP エラー      → Failure（ゲーム動作に影響しない）
 * - その他例外       → Failure
 * 呼び出し元は try/catch 不要。
 */
object SafeApiWrapper {
    suspend fun <T> call(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: IOException) {
        ApiResult.Offline
    } catch (e: HttpException) {
        ApiResult.Failure(e.message(), e.code())
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Unknown error")
    }
}
