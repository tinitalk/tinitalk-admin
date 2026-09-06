package org.tinitalk.admin.server

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.tinitalk.admin.io.readBytesLimited
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TiniTalkHealthInfo(
    val apiVersion: Int?,
    val commit: String?,
)

class UnexpectedTiniTalkServiceException : IllegalStateException()

class UnhealthyTiniTalkServiceException : IllegalStateException()

class TiniTalkHealthHttpException(val statusCode: Int) : IllegalStateException()

class TiniTalkHealthChecker {
    suspend fun check(serverAddress: String): TiniTalkHealthInfo = withContext(Dispatchers.IO) {
        val connection = URL("https://$serverAddress/healthz")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw TiniTalkHealthHttpException(statusCode)
            }
            readResponse(connection.inputStream)
        } finally {
            connection.disconnect()
        }
    }

    internal fun readResponse(input: InputStream): TiniTalkHealthInfo {
        val body = input.use { it.readBytesLimited(MAX_HEALTH_RESPONSE_BYTES) }
        val health = gson.fromJson(body.toString(Charsets.UTF_8), HealthResponse::class.java)
        if (health?.service != TINITALK_SERVICE) {
            throw UnexpectedTiniTalkServiceException()
        }
        if (health.status != HEALTHY_STATUS) {
            throw UnhealthyTiniTalkServiceException()
        }
        return TiniTalkHealthInfo(
            apiVersion = health.apiVersion.takeIf { it > 0 },
            commit = health.commit?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private data class HealthResponse(
        @SerializedName("service") val service: String?,
        @SerializedName("status") val status: String?,
        @SerializedName("api_version") val apiVersion: Int = 0,
        @SerializedName("commit") val commit: String? = null,
    )

    internal companion object {
        const val MAX_HEALTH_RESPONSE_BYTES = 64 * 1024
        private const val TINITALK_SERVICE = "tinitalk"
        private const val HEALTHY_STATUS = "ok"
        private const val TIMEOUT_MILLIS = 5_000
        private val gson = Gson()
    }
}
