package com.automatelinux.carCheck.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class MarketPriceResult {
    /**
     * Asking prices on Yad2: the median and the middle half's range, over [priced] ads of [total].
     * [comparedTo] says of what — "רנו קנגו 2017", or for a truck "משאיות איסוזו 2007–2009, כל הדגמים".
     */
    data class Found(
        val comparedTo: String,
        val total: Int,
        val priced: Int,
        val median: Int,
        val low: Int,
        val high: Int,
        val url: String,
    ) : MarketPriceResult()
    /** Yad2 has no priced ad for this model and year, or no such model. */
    data object None : MarketPriceResult()
    data class Failed(val detail: String) : MarketPriceResult()
}

/**
 * What the model and year is being offered for now — asking prices, not sale prices or a valuation.
 * The backend reads them from Yad2 (`GET /api/price`) and keeps them for a week.
 */
class MarketPrices(baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun find(report: VehicleReport): MarketPriceResult {
        if (report.year == null) return MarketPriceResult.None
        val params = CarPhotos.backendParams(report) ?: return MarketPriceResult.None
        val r = httpGet("$base/api/price?$params")
        if (r.code == 0) return MarketPriceResult.Failed(r.error ?: "no connection")
        val obj = try {
            json.parseToJsonElement(r.body).jsonObject
        } catch (_: Exception) {
            return MarketPriceResult.Failed("HTTP ${r.code}, not JSON")
        }
        if (r.code !in 200..299) {
            val error = obj["error"]?.jsonPrimitive?.content
            return MarketPriceResult.Failed(
                if (error == "blocked") "יד 2 ביקש אימות שזה לא רובוט" else obj["detail"]?.jsonPrimitive?.content ?: "HTTP ${r.code}",
            )
        }
        if (obj["found"]?.jsonPrimitive?.content != "true") return MarketPriceResult.None
        fun int(k: String) = obj[k]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
        return MarketPriceResult.Found(
            comparedTo = obj["comparedTo"]?.jsonPrimitive?.content ?: return MarketPriceResult.Failed("no comparedTo"),
            total = int("total") ?: 0,
            priced = int("priced") ?: 0,
            median = int("median") ?: return MarketPriceResult.Failed("no median"),
            low = int("low") ?: return MarketPriceResult.Failed("no low"),
            high = int("high") ?: return MarketPriceResult.Failed("no high"),
            url = obj["url"]?.jsonPrimitive?.content ?: return MarketPriceResult.Failed("no url"),
        )
    }
}
