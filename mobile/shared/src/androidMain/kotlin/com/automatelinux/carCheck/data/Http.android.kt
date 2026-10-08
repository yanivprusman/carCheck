package com.automatelinux.carCheck.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Android half of [httpGet], on the JDK's own client.
 *
 * No HTTP library on purpose: the app makes one request shape (a GET of JSON) to
 * one host, and a dependency would be more code to keep in version-lockstep
 * with Compose than it would replace. data.gov.il sits behind a WAF that
 * answers some clients with an HTML page, so the User-Agent names the app.
 */
actual suspend fun httpGet(url: String): HttpResult = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "carCheck/1 (Android; +https://ya-niv.com)")
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        HttpResult(code, body)
    } catch (e: IOException) {
        HttpResult(0, "", e.message ?: "no connection")
    } finally {
        conn?.disconnect()
    }
}

/** Same client, for photos; Wikimedia refuses requests without a descriptive User-Agent. */
actual suspend fun httpGetBytes(url: String): BytesResult = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "carCheck/1 (Android; +https://ya-niv.com)")
        }
        val code = conn.responseCode
        val bytes = if (code in 200..299) conn.inputStream.use { it.readBytes() } else ByteArray(0)
        BytesResult(code, bytes)
    } catch (e: IOException) {
        BytesResult(0, ByteArray(0), e.message ?: "no connection")
    } finally {
        conn?.disconnect()
    }
}
