package com.automatelinux.carCheck.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One photo of the model, as the backend saved it; [link] is where a tap goes (its Commons page), if anywhere special. */
data class CarPhoto(val url: String, val alt: String, val link: String?)

enum class PhotoSource { Wikipedia, Google }

sealed class CarPhotosResult {
    /** [moreUrl]: the Wikipedia article, or the Google Images search, the photos came from. */
    data class Found(val source: PhotoSource, val moreUrl: String, val photos: List<CarPhoto>) : CarPhotosResult()
    /** The registry names neither make nor model, or no photo passed the checks. The report shows no strip. */
    data object None : CarPhotosResult()
    data class Failed(val detail: String) : CarPhotosResult()
}

/**
 * Photos of the MODEL — the registry has none of the car itself.
 *
 * The phone does not search: it asks this app's backend (`GET /api/photos`), which takes them from
 * the model's Wikipedia article when the registry gives a commercial name it has one for, and from
 * Google Images otherwise, checked against the model code and year. Each lookup runs once and is
 * kept on the backend's disk; the first ask for a model takes several seconds, later ones do not.
 */
class CarPhotos(baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun find(report: VehicleReport): CarPhotosResult {
        val params = backendParams(report) ?: return CarPhotosResult.None
        val r = httpGet("$base/api/photos?$params")
        if (r.code == 0) return CarPhotosResult.Failed(r.error ?: "no connection")
        val obj = try {
            json.parseToJsonElement(r.body).jsonObject
        } catch (_: Exception) {
            return CarPhotosResult.Failed("HTTP ${r.code}, not JSON")
        }
        if (r.code !in 200..299) {
            val error = obj["error"]?.jsonPrimitive?.content
            return CarPhotosResult.Failed(
                if (error == "google-captcha") "גוגל ביקש אימות שזה לא רובוט" else obj["detail"]?.jsonPrimitive?.content ?: "HTTP ${r.code}",
            )
        }
        val photos = (obj["photos"] as? JsonArray)?.mapNotNull { e ->
            val p = e.jsonObject
            val url = p["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val link = p["link"]?.jsonPrimitive?.content?.takeIf { it != "null" }
            CarPhoto(base + url, p["alt"]?.jsonPrimitive?.content ?: "", link)
        } ?: emptyList()
        if (photos.isEmpty()) return CarPhotosResult.None
        val source = if (obj["source"]?.jsonPrimitive?.content == "wikipedia") PhotoSource.Wikipedia else PhotoSource.Google
        val more = obj["moreUrl"]?.jsonPrimitive?.content ?: return CarPhotosResult.Failed("no moreUrl")
        return CarPhotosResult.Found(source, more, photos)
    }

    companion object {
        /** The model as the backend's /api/photos and /api/price take it; null when the registry names no make or model. */
        fun backendParams(report: VehicleReport): String? {
            val make = report.make ?: return null
            if (report.commercialName == null && report.model == null) return null
            return listOfNotNull(
                "make" to make,
                report.model?.let { "model" to it },
                report.commercialName?.let { "name" to it },
                report.year?.let { "year" to it.toString() },
                kindWord(report)?.let { "kind" to it },
            ).joinToString("&") { (k, v) -> k + "=" + encodeUrlComponent(v) }
        }

        /**
         * What kind of vehicle it is, for the backend's Google query: vehicles over 3.5 t are named
         * only by a type code, and "פיאט 250 2023" alone is Fiat 500s and F-250s, while
         * "פיאט 250 2023 רכב מסחרי" is a page of Ducatos (measured 2026-10-08). The backend adds
         * it only when the code alone is ambiguous.
         */
        fun kindWord(report: VehicleReport): String? = when (report.kind) {
            VehicleKind.Car, VehicleKind.PersonalImport -> null
            VehicleKind.Motorcycle -> "אופנוע"
            VehicleKind.Heavy, VehicleKind.Public -> when (val t = report.vehicleType) {
                null -> null
                // A cargo vehicle up to 7.5 t is a van or light truck; above that, a truck.
                "משא" -> if ((report.grossWeightKg ?: Int.MAX_VALUE) <= 7_500) "רכב מסחרי" else "משאית"
                "מסחרי" -> "רכב מסחרי"
                else -> t
            }
        }
    }
}
