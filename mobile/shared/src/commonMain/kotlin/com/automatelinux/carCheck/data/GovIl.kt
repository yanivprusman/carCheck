package com.automatelinux.carCheck.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The Ministry of Transport's registries on data.gov.il (CKAN datastore API).
 *
 * Every dataset is keyed by the plate number, so one lookup is a handful of
 * independent `datastore_search` calls fanned out in parallel. The phone talks
 * to data.gov.il directly: a lookup then works from any network, with nothing
 * at home switched on.
 */
object GovIl {
    private const val BASE = "https://data.gov.il/api/3/action/"

    /** Resource ids, from `package_search?q=רכב`. Names say which file each one is. */
    object Res {
        /** Active private cars (1996+) and light commercial (1998+): the main registry. */
        const val PRIVATE = "053cea08-09bc-40ec-8f7a-156f0677aff3"
        /** Second half of the same rows: tow hitch, tyre load/speed codes. */
        const val PRIVATE_EXTRA = "0866573c-40cd-4ca8-91d2-9dd2d7a492e5"
        /** Odometer at the last test, first registration, structural/colour/gas/tyre change flags. */
        const val HISTORY = "56063a99-8a3e-4ff4-912e-5966c0279bad"
        /** Every change of hands: year-month and the kind of owner it went to. */
        const val OWNERSHIP = "bb2355dc-9ec7-4f06-9c3f-3344672171da"
        const val MOTORCYCLE = "bf9df4e2-d90d-4c0a-a400-19e15af8e95f"
        /** Over 3.5 t, and anything without a model code. */
        const val HEAVY = "cd3acc5c-03c3-4c89-9c54-d40f93c0d790"
        /** Buses, taxis and other public-service vehicles. */
        const val PUBLIC = "cf29862d-ca25-4691-84f6-1be60dcb4a1e"
        const val PERSONAL_IMPORT = "03adc637-b6fe-402b-9937-7c3d3afc9140"
        const val INACTIVE_WITH_MODEL = "f6efe89a-fb3d-43a4-bb61-9bf12a9b9099"
        const val INACTIVE_NO_MODEL = "6f6acd03-f351-4a8f-8ecf-df792f4f573a"
        /** Taken off the road, final cancellation — current file, then the two archives (text keys, zero-padded). */
        const val OFF_ROAD = "851ecab1-0622-4dbe-a6c7-f950cf82abf9"
        const val OFF_ROAD_2010_2016 = "4e6b9724-4c1e-43f0-909a-154d4cc4e046"
        const val OFF_ROAD_2000_2009 = "ec8cbc34-72e1-4b69-9c48-22821ba0bd6c"
        /** Per-model specification (WLTP): power, seats, body, safety systems, emissions. */
        const val MODEL_SPEC = "142afde2-6228-49f9-8a29-9b6c3a0cbe40"
        /** Importer list price for the model in its production year. */
        const val LIST_PRICE = "39f455bf-6db0-4926-859d-017f34eacbcb"
        /** Vehicles with a recall the owner has not had done. */
        const val RECALL_OPEN = "36bf1404-0be4-49d2-82dc-2f1ead4a8b93"
        /** The recall notices themselves: importer, phone, what to fix. */
        const val RECALL_NOTICES = "2c33523f-87aa-44ec-a736-edbb0a82975e"
        const val DISABLED_TAG = "c8b9f9c8-4612-4068-934f-d4acd2e3c06e"
        /** Diesel particulate filters fitted after the fact. */
        const val PARTICLE_FILTER = "7cb2bd95-bf2e-49b6-aea1-fcb5ff6f0473"
    }

    enum class FailureKind { Offline, Http, Malformed }

    class GovIlException(val kind: FailureKind, message: String) : Exception(message)

    data class Rows(val total: Long, val records: List<JsonObject>)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** `datastore_search` with an exact-match filter set. Throws [GovIlException]; never returns a guess. */
    suspend fun search(resource: String, filters: Map<String, Any>? = null, limit: Int = 10): Rows {
        val sb = StringBuilder(BASE)
            .append("datastore_search?resource_id=").append(resource)
            .append("&limit=").append(limit)
        if (!filters.isNullOrEmpty()) {
            val f = buildJsonObject {
                for ((k, v) in filters) when (v) {
                    is Number -> put(k, v)
                    is Boolean -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
            sb.append("&filters=").append(encodeUrlComponent(f.toString()))
        }
        val obj = call(sb.toString())
        val result = obj["result"]?.jsonObject
            ?: throw GovIlException(FailureKind.Malformed, "no result object")
        val records = (result["records"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
        val total = result["total"]?.jsonPrimitive?.content?.toLongOrNull() ?: records.size.toLong()
        return Rows(total, records)
    }

    /** When the resource's file was last replaced: the "data as of" line at the foot of a report. */
    suspend fun lastModified(resource: String): String? {
        val obj = call(BASE + "resource_show?id=" + resource)
        return obj["result"]?.jsonObject?.get("last_modified")?.jsonPrimitive?.content
    }

    private suspend fun call(url: String): JsonObject {
        val r = httpGet(url)
        if (r.code == 0) throw GovIlException(FailureKind.Offline, r.error ?: "no connection")
        if (r.code !in 200..299) throw GovIlException(FailureKind.Http, "HTTP ${r.code}")
        val obj = try {
            json.parseToJsonElement(r.body).jsonObject
        } catch (e: Exception) {
            // data.gov.il answers a WAF block or maintenance with an HTML page and a 200.
            throw GovIlException(FailureKind.Malformed, "not JSON")
        }
        if (obj["success"]?.jsonPrimitive?.content != "true") {
            throw GovIlException(FailureKind.Http, "success=false")
        }
        return obj
    }
}

// ---- Lenient field readers: the archives store numbers as zero-padded text, the
// ---- current files as numbers, and empty strings stand in for null everywhere.

fun JsonObject.text(key: String): String? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p.content == "null") return null
    // The registry pads some text fields to fixed width and doubles spaces inside
    // model names ("E-250    207.336"); one space is what a reader wants.
    val s = p.content.trim().replace(Regex("\\s+"), " ")
    return s.ifEmpty { null }
}

fun JsonObject.int(key: String): Int? = text(key)?.toDoubleOrNull()?.toInt()

fun JsonObject.long(key: String): Long? = text(key)?.toDoubleOrNull()?.toLong()

fun JsonObject.double(key: String): Double? = text(key)?.toDoubleOrNull()

/** `*_ind` flags: 1 = yes, 0 = no, missing = unknown. */
fun JsonObject.flag(key: String): Boolean? = int(key)?.let { it != 0 }
