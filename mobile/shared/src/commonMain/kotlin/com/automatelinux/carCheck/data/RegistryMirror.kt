package com.automatelinux.carCheck.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The copy of data.gov.il's main private-car file that this app's own backend keeps
 * (`npm run sync-registry` there). Asked only while the government datastore is empty —
 * for hours after each nightly upload — and answered with the same row the datastore
 * would give, plus when the copied file was uploaded, which is what the report is "as of".
 */
class RegistryMirror(baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed class Result {
        data class Found(val record: JsonObject, val fileModified: String?) : Result()
        data class NotFound(val fileModified: String?) : Result()
        /** Backend unreachable, never synced, or answered something else; [reason] says which. */
        data class Unavailable(val reason: String) : Result()
    }

    suspend fun privateRecord(plate: Plate): Result {
        val r = httpGet("$base/api/registry/private?plate=${plate.digits}")
        if (r.code == 0) return Result.Unavailable(r.error ?: "no connection")
        val obj = try {
            json.parseToJsonElement(r.body).jsonObject
        } catch (_: Exception) {
            return Result.Unavailable("HTTP ${r.code}, not JSON")
        }
        if (r.code !in 200..299) {
            return Result.Unavailable(obj["detail"]?.jsonPrimitive?.content ?: obj["error"]?.jsonPrimitive?.content ?: "HTTP ${r.code}")
        }
        val modified = obj["fileModified"]?.jsonPrimitive?.content
        val record = obj["record"] as? JsonObject
        return if (obj["found"]?.jsonPrimitive?.content == "true" && record != null) Result.Found(record, modified) else Result.NotFound(modified)
    }
}
