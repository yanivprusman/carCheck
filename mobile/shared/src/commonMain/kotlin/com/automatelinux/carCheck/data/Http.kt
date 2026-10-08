package com.automatelinux.carCheck.data

/** Code 0 = the request never reached anyone (no network); [error] then says why. */
data class HttpResult(val code: Int, val body: String, val error: String? = null)

expect suspend fun httpGet(url: String): HttpResult

/** A binary GET (a photo). Code 0 = never reached anyone; [BytesResult.error] says why. */
class BytesResult(val code: Int, val bytes: ByteArray, val error: String? = null)

expect suspend fun httpGetBytes(url: String): BytesResult

/** Percent-encodes one query-string value (RFC 3986 unreserved set kept as is). */
fun encodeUrlComponent(value: String): String {
    val sb = StringBuilder()
    for (b in value.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            sb.append(ch)
        } else {
            sb.append('%')
            sb.append(HEX[c shr 4])
            sb.append(HEX[c and 0x0F])
        }
    }
    return sb.toString()
}

private const val HEX = "0123456789ABCDEF"
