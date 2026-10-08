package com.automatelinux.carCheck.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One photo of the model, as the backend saved it. */
data class CarPhoto(val url: String, val alt: String)

sealed class CarPhotosResult {
    data class Found(val query: String, val photos: List<CarPhoto>) : CarPhotosResult()
    /** The registry names neither make nor model, or Google had no photo for them. The report shows no strip. */
    data object None : CarPhotosResult()
    data class Failed(val detail: String) : CarPhotosResult()
}

/**
 * Photos of the MODEL — the registry has none of the car itself — from a Google Images search.
 *
 * The phone does not search: it asks this app's backend (`GET /api/photos?q=…`), which runs the
 * search once per query, keeps the photos on its disk, and answers every later plate of the same
 * model and year from there. The first ask for a model takes several seconds; later ones do not.
 */
class CarPhotos(baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun find(report: VehicleReport): CarPhotosResult {
        val q = query(report) ?: return CarPhotosResult.None
        val r = httpGet("$base/api/photos?q=" + encodeUrlComponent(q))
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
            CarPhoto(base + url, p["alt"]?.jsonPrimitive?.content ?: "")
        } ?: emptyList()
        return if (photos.isEmpty()) CarPhotosResult.None else CarPhotosResult.Found(q, photos)
    }

    companion object {
        /**
         * "<make> <model> <year>", plus what kind of vehicle it is when that is not a private car.
         *
         * Vehicles over 3.5 t are named in the registry only by a type code ("פיאט 250"), and
         * Google reads "פיאט 250 2023" as a Fiat 500 or a Ford F-250. Saying what the vehicle is
         * fixes that: "פיאט 250 2023 רכב מסחרי" is a page of Ducatos (measured 2026-10-08).
         */
        fun query(report: VehicleReport): String? {
            val name = report.commercialName ?: report.model
            if (report.make == null || name == null) return null
            return listOfNotNull(report.make, name, report.year?.toString(), kindWord(report)).joinToString(" ")
        }

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

        /** The same search in Google Images in the browser, for a tap on a photo. */
        fun googleImagesUrl(query: String): String = "https://www.google.com/search?udm=2&q=" + encodeUrlComponent(query)
    }
}
