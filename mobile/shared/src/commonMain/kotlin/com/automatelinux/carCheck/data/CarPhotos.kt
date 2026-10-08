package com.automatelinux.carCheck.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One photo of the model, from the Wikipedia article about it. */
data class CarPhoto(
    /** A ~500px-wide rendition, for the strip. */
    val thumbUrl: String,
    /** The file's page on Commons: author, licence, full size. Tapping a photo opens it. */
    val pageUrl: String,
    val fileName: String,
)

data class CarPhotoSet(val article: String, val articleUrl: String, val photos: List<CarPhoto>)

sealed class CarPhotosResult {
    data class Found(val set: CarPhotoSet) : CarPhotosResult()
    /** No article is about this model, or it has no photos of it. The report shows no strip. */
    data object None : CarPhotosResult()
    data class Failed(val detail: String) : CarPhotosResult()
}

/**
 * Photos of the MODEL — the registry has none of the car itself — taken from its Wikipedia article.
 *
 * The registry names the make in Hebrew ("טויוטה") and the model in Latin ("COROLLA"), which is
 * exactly what Hebrew Wikipedia's search matches well: "טויוטה COROLLA" finds "טויוטה קורולה".
 * A hit is only accepted when its title holds the make and its own or its English twin's title
 * holds the model name; otherwise there are no photos rather than a different car's.
 *
 * The photos come from the English article when there is one: it has a photo per generation,
 * and Commons file names start with the years they show ("2007-2010 Toyota Corolla …"), so the
 * photos nearest the car's production year are shown first.
 */
object CarPhotos {
    private const val HE_API = "https://he.wikipedia.org/w/api.php"
    private const val EN_API = "https://en.wikipedia.org/w/api.php"
    const val MAX_PHOTOS = 8
    /** A width Wikimedia pre-renders; arbitrary widths are throttled. */
    private const val THUMB_WIDTH = 500

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun find(make: String?, commercialName: String?, model: String?, year: Int?): CarPhotosResult {
        if (make.isNullOrBlank()) return CarPhotosResult.None
        if (commercialName.isNullOrBlank()) {
            val known = knownModel(make, model) ?: return CarPhotosResult.None
            return fromArticle(EN_API, "en", known.enArticle, known.photoKey, year)
        }
        val hits = try {
            search("$make $commercialName")
        } catch (e: PhotoException) {
            return CarPhotosResult.Failed(e.message ?: "search failed")
        }
        val hit = pickArticle(hits, make, commercialName) ?: return CarPhotosResult.None
        return if (hit.enTitle != null) fromArticle(EN_API, "en", hit.enTitle, commercialName, year)
        else fromArticle(HE_API, "he", hit.heTitle, commercialName, year)
    }

    private suspend fun fromArticle(api: String, host: String, title: String, photoKey: String, year: Int?): CarPhotosResult {
        val files = try {
            images(api, title)
        } catch (e: PhotoException) {
            return CarPhotosResult.Failed(e.message ?: "images failed")
        }
        val photos = rankPhotos(files, photoKey, year).take(MAX_PHOTOS)
        if (photos.isEmpty()) return CarPhotosResult.None
        val articleUrl = "https://$host.wikipedia.org/wiki/" + encodeUrlComponent(title.replace(' ', '_'))
        return CarPhotosResult.Found(CarPhotoSet(title, articleUrl, photos))
    }

    /**
     * A model the registry names only by the manufacturer's internal code.
     *
     * The heavy-vehicle file (over 3.5 t: big vans, trucks, buses) has no commercial name at all,
     * only `degem_nm` — "250" for a Fiat Ducato, "907.657" for a Sprinter — so there is nothing to
     * search Wikipedia for. These are the codes that file actually holds most (counted 2026-10-08
     * over its 420k rows) and whose meaning is certain; each points straight at its English article.
     * A code not listed here gets no photos.
     */
    data class KnownModel(val makePrefix: String, val code: Regex, val enArticle: String, val photoKey: String)

    val KNOWN_MODELS = listOf(
        // Fiat's type number for the Ducato since 2006; the VIN carries it too (ZFA250…).
        KnownModel("פיאט", Regex("^250"), "Fiat Ducato", "Ducato"),
        // 906 / 907 are the Sprinter's generation codes, written alone or after "SPRINTER" / "519CDI".
        KnownModel("מרצדס", Regex("(^|\\s)(SPRINTER|90[67]\\.)"), "Mercedes-Benz Sprinter", "Sprinter"),
        KnownModel("מרצדס", Regex("^AROCS"), "Mercedes-Benz Arocs", "Arocs"),
        KnownModel("מרצדס", Regex("^ATEGO"), "Mercedes-Benz Atego", "Atego"),
        KnownModel("מרצדס", Regex("^ACTROS"), "Mercedes-Benz Actros", "Actros"),
        // Daily type designations: gross tonnes, C/S chassis, horsepower/10 — 35S14, 50C18, 70C18.
        KnownModel("איווקו", Regex("^\\d{2}[CS]\\d{2}"), "Iveco Daily", "Daily"),
        // Isuzu's N-series (Elf) and F-series (Forward) trucks: NPR75, NQR, FRR90, FSR90.
        KnownModel("איסוזו", Regex("^N[KMNPQ]R"), "Isuzu Elf", "Isuzu"),
        KnownModel("איסוזו", Regex("^F[RSTV]R"), "Isuzu Forward", "Isuzu"),
        // GM model codes: C/K/T + series (20 = 2500, 35 = 3500) + body — the heavy-duty Silverado.
        KnownModel("שברולט", Regex("^C[KT][23]\\d{4}"), "Chevrolet Silverado", "Silverado"),
        // Ford's VIN line/series code for the F-250/F-350 Super Duty: W2B, W3B.
        KnownModel("פורד", Regex("^W[1-5][A-Z]$"), "Ford Super Duty", "Ford"),
        KnownModel("וולבו", Regex("^FH"), "Volvo FH", "Volvo"),
        KnownModel("וולבו", Regex("^FM"), "Volvo FM", "Volvo"),
        KnownModel("וולבו", Regex("^FL"), "Volvo FL", "Volvo"),
        KnownModel("וולבו", Regex("^FE"), "Volvo FE", "Volvo"),
        // DAF prefixes the range with its axle layout: "FA LF210H12", "FAG CF340AD".
        KnownModel("דאף", Regex("(^|\\s)LF"), "DAF LF", "DAF"),
        KnownModel("דאף", Regex("(^|\\s)CF"), "DAF CF", "DAF"),
        KnownModel("דאף", Regex("(^|\\s)XF"), "DAF XF", "DAF"),
        KnownModel("מאן", Regex("^TG[LMSX]"), "MAN TG-range", "MAN"),
    )

    fun knownModel(make: String, model: String?): KnownModel? {
        val m = model?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        val mk = normalize(make)
        return KNOWN_MODELS.firstOrNull { mk.startsWith(normalize(it.makePrefix)) && it.code.containsMatchIn(m) }
    }

    data class ArticleHit(val heTitle: String, val enTitle: String?)

    /** First hit, in search order, that is plainly about this make and model. */
    fun pickArticle(hits: List<ArticleHit>, make: String, commercialName: String): ArticleHit? {
        val m = normalize(make)
        val names = modelKeys(commercialName)
        return hits.firstOrNull { h ->
            normalize(h.heTitle).contains(m) &&
                names.any { k -> normalize(h.heTitle).contains(k) || (h.enTitle?.let { normalize(it).contains(k) } == true) }
        }
    }

    data class FileInfo(val title: String, val mime: String, val thumbUrl: String, val pageUrl: String)

    /**
     * Photos of the model, nearest to [year] first. Files whose names do not mention the model
     * (a rival, a factory, a logo) are dropped; the rest keep the article's order within a score.
     */
    fun rankPhotos(files: List<FileInfo>, commercialName: String, year: Int?): List<CarPhoto> {
        val names = modelKeys(commercialName)
        return files
            .filter { it.mime in PHOTO_MIMES }
            .filter { f -> val n = normalize(f.title); names.any { n.contains(it) } }
            .filter { f -> NOT_PHOTOS.none { f.title.lowercase().contains(it) } }
            .sortedBy { f -> yearDistance(f.title, year) }
            .map { CarPhoto(it.thumbUrl, it.pageUrl, it.title.removePrefix("File:")) }
    }

    /** 0 when the file's leading years cover [year], else how far off; undated files after all dated ones. */
    fun yearDistance(fileTitle: String, year: Int?): Int {
        val m = LEADING_YEARS.find(fileTitle.removePrefix("File:")) ?: return UNDATED
        if (year == null) return 0
        val from = m.groupValues[1].toInt()
        val to = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) from / 100 * 100 + it else it } ?: from
        return when {
            year < from -> from - year
            year > to -> year - to
            else -> 0
        }
    }

    /** "COROLLA CROSS" → ["corollacross", "corolla"]: the full name, then its first word if that is specific enough. */
    fun modelKeys(commercialName: String): List<String> {
        val full = normalize(commercialName)
        val first = normalize(commercialName.trim().split(' ', '-').first())
        return listOf(full, first).filter { it.isNotEmpty() }.distinct()
            .filter { it == full || it.length >= 3 }
    }

    /** Lower case, letters and digits only, Latin accents dropped ("Škoda" → "skoda", "I-20" → "i20"). */
    fun normalize(s: String): String = buildString {
        for (c in s.lowercase()) {
            val plain = ACCENTS[c] ?: c
            if (plain.isLetterOrDigit()) append(plain)
        }
    }

    private suspend fun search(query: String): List<ArticleHit> {
        val url = "$HE_API?action=query&format=json&generator=search&gsrlimit=5" +
            "&gsrsearch=" + encodeUrlComponent(query) +
            "&prop=langlinks&lllang=en"
        val pages = pagesOf(call(url))
        return pages
            .sortedBy { it["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { p ->
                val title = p["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val en = (p["langlinks"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("*")?.jsonPrimitive?.content
                ArticleHit(title, en)
            }
    }

    private suspend fun images(api: String, title: String): List<FileInfo> {
        val url = "$api?action=query&format=json&redirects=1&generator=images&gimlimit=max" +
            "&titles=" + encodeUrlComponent(title) +
            "&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=$THUMB_WIDTH"
        // The generator returns pages in title order; that is the order kept within a year score.
        return pagesOf(call(url))
            .sortedBy { it["title"]?.jsonPrimitive?.content }
            .mapNotNull { p ->
                val t = p["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val ii = (p["imageinfo"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return@mapNotNull null
                FileInfo(
                    title = t,
                    mime = ii["mime"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    thumbUrl = ii["thumburl"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    pageUrl = ii["descriptionurl"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                )
            }
    }

    private fun pagesOf(obj: JsonObject): List<JsonObject> =
        obj["query"]?.jsonObject?.get("pages")?.jsonObject?.values?.map { it.jsonObject } ?: emptyList()

    private class PhotoException(message: String) : Exception(message)

    private suspend fun call(url: String): JsonObject {
        val r = httpGet(url)
        if (r.code == 0) throw PhotoException(r.error ?: "no connection")
        if (r.code !in 200..299) throw PhotoException("Wikipedia HTTP ${r.code}")
        return try {
            json.parseToJsonElement(r.body).jsonObject
        } catch (e: Exception) {
            throw PhotoException("Wikipedia answered something that is not JSON")
        }
    }

    private const val UNDATED = 1000
    private val LEADING_YEARS = Regex("^((?:19|20)\\d{2})(?:\\s*[-–]\\s*(\\d{4}|\\d{2})(?!\\d))?")
    private val PHOTO_MIMES = setOf("image/jpeg", "image/png", "image/webp")
    private val NOT_PHOTOS = listOf("logo", "emblem", "badge", "icon", "map", "diagram", "flag of", "chart")
    private val ACCENTS = mapOf(
        'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a', 'ã' to 'a', 'å' to 'a',
        'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e', 'ě' to 'e',
        'í' to 'i', 'ï' to 'i', 'î' to 'i', 'ó' to 'o', 'ö' to 'o', 'ô' to 'o', 'ø' to 'o',
        'ú' to 'u', 'ü' to 'u', 'û' to 'u', 'ů' to 'u', 'ç' to 'c', 'č' to 'c', 'š' to 's',
        'ž' to 'z', 'ř' to 'r', 'ñ' to 'n', 'ý' to 'y',
    )
}
