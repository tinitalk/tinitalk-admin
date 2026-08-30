package org.tinitalk.admin.server

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
            val health = connection.inputStream.bufferedReader().use {
                gson.fromJson(it, HealthResponse::class.java)
            }
            if (health.service != TINITALK_SERVICE) {
                throw UnexpectedTiniTalkServiceException()
            }
            if (health.status != HEALTHY_STATUS) {
                throw UnhealthyTiniTalkServiceException()
            }
            TiniTalkHealthInfo(
                apiVersion = health.apiVersion.takeIf { it > 0 },
                commit = health.commit?.trim()?.takeIf(String::isNotEmpty),
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class HealthResponse(
        val service: String?,
        val status: String?,
        @SerializedName("api_version") val apiVersion: Int = 0,
        val commit: String? = null,
    )

    private companion object {
        const val TINITALK_SERVICE = "tinitalk"
        const val HEALTHY_STATUS = "ok"
        const val TIMEOUT_MILLIS = 5_000
        val gson = Gson()
    }
}
