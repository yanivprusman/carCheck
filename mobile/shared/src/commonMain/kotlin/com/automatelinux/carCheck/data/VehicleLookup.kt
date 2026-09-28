package com.automatelinux.carCheck.data

import com.automatelinux.carCheck.data.GovIl.FailureKind
import com.automatelinux.carCheck.data.GovIl.Res
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

sealed class LookupResult {
    data class Found(val report: VehicleReport) : LookupResult()
    /** Every registry answered, and none of them has this plate. */
    data object NotFound : LookupResult()
    /** The main registry answered with an empty table: data.gov.il is mid-reload. */
    data object RegistryRefreshing : LookupResult()
    data object Offline : LookupResult()
    data class Failed(val detail: String) : LookupResult()
}

/**
 * One plate → one [VehicleReport].
 *
 * Wave 1 asks every plate-keyed file at once. Wave 2 joins the model-keyed files
 * (specification, list price, recall notices) using codes from the primary record.
 *
 * A primary registry that fails to answer fails the lookup — a report that says
 * "not found" because the network dropped one request would be a lie. Enrichment
 * files that fail are named in [VehicleReport.unavailable] instead of silently
 * leaving their section out.
 */
class VehicleLookup(private val mirror: RegistryMirror? = null) {

    private class Wave1(
        val main: Rows?, val extra: Rows?, val motorcycle: Rows?, val heavy: Rows?, val public: Rows?,
        val import: Rows?, val inactiveModel: Rows?, val inactiveNoModel: Rows?,
        val offRoad: Rows?, val offRoad2010: Rows?, val offRoad2000: Rows?,
        val history: Rows?, val owners: Rows?, val recallOpen: Rows?, val disabledTag: Rows?, val filter: Rows?,
        val asOf: String?,
    )

    private class Rows(val records: List<JsonObject>)

    suspend fun lookup(plate: Plate): LookupResult {
        val n = plate.number
        val unavailable = mutableListOf<String>()

        val w = try {
            coroutineScope {
                // Primary registries: a failure here is the lookup's failure.
                val main = primary(Res.PRIVATE, n)
                val motorcycle = primary(Res.MOTORCYCLE, n)
                val heavy = primary(Res.HEAVY, n)
                val public = primary(Res.PUBLIC, n)
                val import = primary(Res.PERSONAL_IMPORT, n)
                val inactiveModel = primary(Res.INACTIVE_WITH_MODEL, n)
                val inactiveNoModel = primary(Res.INACTIVE_NO_MODEL, n)
                val offRoad = primary(Res.OFF_ROAD, n)
                val offRoad2010 = primary(Res.OFF_ROAD_2010_2016, plate.padded8)
                val offRoad2000 = primary(Res.OFF_ROAD_2000_2009, plate.padded8)
                // Enrichment: a failure is reported, not fatal.
                val extra = optional("נתוני גרירה וצמיגים", unavailable) { rows(Res.PRIVATE_EXTRA, mapOf("mispar_rechev" to n)) }
                val history = optional("היסטוריית טסטים", unavailable) { rows(Res.HISTORY, mapOf("mispar_rechev" to n)) }
                val owners = optional("היסטוריית בעלויות", unavailable) { rows(Res.OWNERSHIP, mapOf("mispar_rechev" to n), 50) }
                val recallOpen = optional("ריקולים", unavailable) { rows(Res.RECALL_OPEN, mapOf("MISPAR_RECHEV" to n)) }
                val disabledTag = optional("תג נכה", unavailable) { rows(Res.DISABLED_TAG, mapOf("MISPAR RECHEV" to n)) }
                val filter = optional("מסנן חלקיקים", unavailable) { rows(Res.PARTICLE_FILTER, mapOf("mispar_rechev" to n)) }
                val asOf = async { try { GovIl.lastModified(Res.PRIVATE) } catch (_: Exception) { null } }
                Wave1(
                    main.await(), extra.await(), motorcycle.await(), heavy.await(), public.await(), import.await(),
                    inactiveModel.await(), inactiveNoModel.await(), offRoad.await(), offRoad2010.await(), offRoad2000.await(),
                    history.await(), owners.await(), recallOpen.await(), disabledTag.await(), filter.await(), asOf.await(),
                )
            }
        } catch (e: GovIl.GovIlException) {
            return when (e.kind) {
                FailureKind.Offline -> LookupResult.Offline
                else -> LookupResult.Failed(e.message ?: e.kind.name)
            }
        }

        val primary: Pair<JsonObject, VehicleKind>? = firstRecord(w.main)?.let { it to VehicleKind.Car }
            ?: firstRecord(w.import)?.let { it to VehicleKind.PersonalImport }
            ?: firstRecord(w.motorcycle)?.let { it to VehicleKind.Motorcycle }
            ?: firstRecord(w.heavy)?.let { it to VehicleKind.Heavy }
            ?: firstRecord(w.public)?.let { it to VehicleKind.Public }
            ?: firstRecord(w.inactiveModel)?.let { it to VehicleKind.Car }
            ?: firstRecord(w.inactiveNoModel)?.let { it to VehicleKind.Heavy }
            ?: firstRecord(w.offRoad)?.let { it to VehicleKind.Car }
            ?: firstRecord(w.offRoad2010)?.let { it to VehicleKind.Car }
            ?: firstRecord(w.offRoad2000)?.let { it to VehicleKind.Car }

        // The main registry is empty for hours after each nightly upload. The copy our own
        // backend keeps of the last complete file answers first; failing that, the sibling
        // file (the same rows, the other half of the columns) still proves the car exists,
        // and the report is built from it and every other file, and says what it lacks.
        var mainRefreshing = false
        var mirrorAsOf: String? = null
        var mirrorNote: String? = null
        val (rec, kind) = primary ?: run {
            val refreshing = try {
                GovIl.search(Res.PRIVATE, null, 1).total == 0L
            } catch (e: GovIl.GovIlException) {
                return if (e.kind == FailureKind.Offline) LookupResult.Offline else LookupResult.Failed(e.message ?: "probe")
            }
            if (!refreshing) return LookupResult.NotFound
            mainRefreshing = true
            when (val m = mirror?.privateRecord(plate)) {
                is RegistryMirror.Result.Found -> {
                    mirrorAsOf = m.fileModified
                    m.record to VehicleKind.Car
                }
                is RegistryMirror.Result.NotFound -> {
                    mirrorAsOf = m.fileModified
                    // Not in the copy: registered since it was taken, or not a private car at all.
                    val sibling = firstRecord(w.extra) ?: return LookupResult.RegistryRefreshing
                    mirrorAsOf = null
                    mirrorNote = "הרכב לא נמצא בעותק השמור מ-${formatUtcDateTime(m.fileModified) ?: "?"}"
                    sibling to VehicleKind.Car
                }
                is RegistryMirror.Result.Unavailable -> {
                    mirrorNote = m.reason
                    (firstRecord(w.extra) ?: return LookupResult.RegistryRefreshing) to VehicleKind.Car
                }
                null -> (firstRecord(w.extra) ?: return LookupResult.RegistryRefreshing) to VehicleKind.Car
            }
        }
        val status: RegistrationStatus = when {
            // A row from the copy is a main-file row: its licensing state is as of the copy.
            mainRefreshing && mirrorAsOf != null -> RegistrationStatus.Active(rec.text("tokef_dt"))
            mainRefreshing -> RegistrationStatus.Unknown
            firstRecord(w.main) != null || firstRecord(w.import) != null || firstRecord(w.motorcycle) != null ||
                firstRecord(w.heavy) != null -> RegistrationStatus.Active(rec.text("tokef_dt"))
            firstRecord(w.public) != null ->
                if (rec.text("bitul_cd").let { it != null && it != "0" }) RegistrationStatus.OffRoad(rec.text("bitul_dt"))
                else RegistrationStatus.Active(rec.text("tokef_dt"))
            firstRecord(w.inactiveModel) != null || firstRecord(w.inactiveNoModel) != null -> RegistrationStatus.Inactive
            else -> RegistrationStatus.OffRoad(rec.text("bitul_dt"))
        }

        // Wave 2: model-keyed joins.
        val tozeretCd = rec.int("tozeret_cd")
        val degemCd = rec.int("degem_cd")
        val year = rec.int("shnat_yitzur")
        val history = w.history?.records?.firstOrNull()
        // Without the main file the production year is unknown; the first registration bounds it.
        val registrationYear = history?.text("rishum_rishon_dt")?.take(4)?.toIntOrNull()
        var spec: JsonObject? = null
        var price: JsonObject? = null
        val recallIds = w.recallOpen?.records?.mapNotNull { it.int("RECALL_ID") } ?: emptyList()
        val notices = mutableMapOf<Int, JsonObject>()
        coroutineScope {
            val byModel = if (tozeretCd != null && degemCd != null) mapOf<String, Any>("tozeret_cd" to tozeretCd, "degem_cd" to degemCd) else null
            val filters = byModel?.let { m -> year?.let { m + ("shnat_yitzur" to it) } ?: m }
            val limit = if (year != null) 3 else 40
            val specD = if (filters != null) optional("מפרט הדגם", unavailable) { rows(Res.MODEL_SPEC, filters, limit) } else null
            val priceD = if (filters != null) optional("מחירון", unavailable) { rows(Res.LIST_PRICE, filters, limit) } else null
            val noticeDs = recallIds.map { id -> id to optional("פרטי ריקול", unavailable) { rows(Res.RECALL_NOTICES, mapOf("RECALL_ID" to id), 1) } }
            val specRows = specD?.await()?.records ?: emptyList()
            val priceRows = priceD?.await()?.records ?: emptyList()
            spec = if (year != null) specRows.firstOrNull() else {
                val chosen = chooseYear(specRows.mapNotNull { it.int("shnat_yitzur") }, registrationYear)
                specRows.firstOrNull { it.int("shnat_yitzur") == chosen }
            }
            price = if (year != null) priceRows.firstOrNull() else unambiguousPrice(priceRows, registrationYear)
            for ((id, d) in noticeDs) d.await()?.records?.firstOrNull()?.let { notices[id] = it }
        }

        val extra = w.extra?.records?.firstOrNull()
        val recalls = (w.recallOpen?.records ?: emptyList()).mapNotNull { r ->
            val id = r.int("RECALL_ID") ?: return@mapNotNull null
            val nt = notices[id]
            Recall(
                id = id,
                type = r.text("SUG_RECALL") ?: nt?.text("SUG_RECALL"),
                category = r.text("SUG_TAKALA") ?: nt?.text("SUG_TAKALA"),
                description = r.text("TEUR_TAKALA") ?: nt?.text("TEUR_TAKALA"),
                fix = nt?.text("OFEN_TIKUN"),
                importer = nt?.text("YEVUAN_TEUR"),
                phone = dialable(nt?.text("TELEPHONE")),
                website = nt?.text("WEBSITE")?.lowercase()?.let { if (it.startsWith("http")) it else "https://$it" },
                opened = r.text("TAARICH_PTICHA"),
            )
        }
        val owners = (w.owners?.records ?: emptyList())
            .mapNotNull { r -> r.text("baalut_dt")?.let { OwnershipChange(it, r.text("baalut") ?: "") } }
            .sortedBy { it.yearMonth }

        val country = rec.text("tozeret_eretz_nm") ?: spec?.text("tozeret_eretz_nm")
        val makeRaw = spec?.text("tozar") ?: rec.text("tozeret_nm")
        val safety = spec?.let { safetyFeatures(it) } ?: emptyList()

        val report = VehicleReport(
            plate = plate,
            kind = kind,
            status = status,
            make = cleanMake(makeRaw, country),
            makeCountry = country,
            model = rec.text("degem_nm"),
            commercialName = (rec.text("kinuy_mishari") ?: spec?.text("kinuy_mishari") ?: price?.text("kinuy_mishari"))
                ?.takeIf { it.isNotBlank() },
            trim = rec.text("ramat_gimur") ?: spec?.text("ramat_gimur"),
            year = year,
            color = rec.text("tzeva_rechev"),
            bodyType = spec?.text("merkav"),
            vehicleType = rec.text("sug_rechev_nm") ?: rec.text("kvutzat_sug_rechev"),
            euCategory = rec.text("sug_rechev_EU_cd") ?: rec.text("tkina_EU"),

            lastTest = rec.text("mivchan_acharon_dt"),
            onRoad = rec.text("moed_aliya_lakvish"),
            firstRegistration = history?.text("rishum_rishon_dt"),
            ownership = rec.text("baalut"),
            chassis = rec.text("misgeret") ?: rec.text("mispar_shilda") ?: rec.text("shilda"),
            engineNumber = rec.text("mispar_manoa") ?: history?.text("mispar_manoa"),
            ownershipHistory = owners,

            fuel = rec.text("sug_delek_nm") ?: spec?.text("delek_nm"),
            engineModel = rec.text("degem_manoa"),
            displacementCc = rec.int("nefach_manoa") ?: spec?.int("nefah_manoa"),
            horsepower = spec?.int("koah_sus") ?: rec.int("hespek"),
            drive = spec?.text("hanaa_nm")?.takeUnless { it.startsWith("לא ידוע") } ?: rec.text("hanaa_nm"),
            automatic = spec?.flag("automatic_ind"),
            driveTechnology = spec?.text("technologiat_hanaa_nm"),
            grossWeightKg = rec.int("mishkal_kolel")?.takeIf { it > 0 } ?: spec?.int("mishkal_kolel")?.takeIf { it > 0 },
            curbWeightKg = rec.int("mishkal_azmi")?.takeIf { it > 0 },
            towBrakedKg = spec?.int("kosher_grira_im_blamim")?.takeIf { it > 0 },
            towUnbrakedKg = spec?.int("kosher_grira_bli_blamim")?.takeIf { it > 0 },
            towHitch = (extra?.text("grira_nm") ?: rec.text("grira_nm"))?.let { !it.contains("אין") },

            doors = spec?.int("mispar_dlatot")?.takeIf { it > 0 },
            seats = spec?.int("mispar_moshavim")?.takeIf { it > 0 } ?: rec.int("mispar_mekomot")?.takeIf { it > 0 },
            tyreFront = rec.text("zmig_kidmi") ?: rec.text("mida_zmig_kidmi"),
            tyreRear = rec.text("zmig_ahori") ?: rec.text("mida_zmig_ahori"),
            tyreLoadCode = extra?.text("kod_omes_tzmig_kidmi"),
            tyreSpeedCode = extra?.text("kod_mehirut_tzmig_kidmi"),

            safetyLevel = rec.int("ramat_eivzur_betihuty") ?: spec?.int("ramat_eivzur_betihuty"),
            safetyScore = spec?.double("nikud_betihut"),
            airbags = spec?.int("mispar_kariot_avir"),
            safetyFeatures = safety,

            pollutionGroup = rec.int("kvutzat_zihum")?.takeIf { it > 0 } ?: spec?.int("kvutzat_zihum")?.takeIf { it > 0 },
            greenIndex = spec?.double("madad_yarok"),
            co2Wltp = spec?.double("CO2_WLTP"),
            noxWltp = spec?.double("NOX_WLTP"),
            pmWltp = spec?.double("PM_WLTP"),
            particleFilterFitted = w.filter?.records?.firstOrNull()?.text("taarich_hatkana"),

            kmAtLastTest = history?.int("kilometer_test_aharon"),
            structuralChange = history?.flag("shinui_mivne_ind"),
            gasConversion = history?.flag("gapam_ind"),
            colorChange = history?.flag("shnui_zeva_ind"),
            tyreChange = history?.flag("shinui_zmig_ind"),
            originality = history?.text("mkoriut_nm") ?: rec.text("mkoriut_nm"),

            disabledTagSince = w.disabledTag?.records?.firstOrNull()?.text("TAARICH HAFAKAT TAG"),
            importType = rec.text("sug_yevu"),
            recalls = recalls,

            listPriceNis = price?.int("mehir")?.takeIf { it > 0 },
            importer = price?.text("shem_yevuan"),

            unavailable = unavailable.distinct(),
            dataAsOf = w.asOf,
            mainRegistryRefreshing = mainRefreshing,
            mirrorAsOf = mirrorAsOf,
            mirrorNote = mirrorNote,
        )
        return LookupResult.Found(report)
    }

    companion object {
        /**
         * Which year's specification to read a car of unknown production year from: the latest
         * that is not after its first registration (a car is registered in or after its model
         * year), else the earliest on file.
         */
        internal fun chooseYear(years: List<Int>, registrationYear: Int?): Int? {
            if (years.isEmpty()) return null
            if (registrationYear == null) return years.max()
            return years.filter { it <= registrationYear }.maxOrNull() ?: years.min()
        }

        /**
         * A list price for a car of unknown production year, only when the years it could be
         * (its registration year and the one before) all carry the same price.
         */
        internal fun unambiguousPrice(rows: List<JsonObject>, registrationYear: Int?): JsonObject? {
            val candidates = if (registrationYear == null) rows else rows.filter {
                val y = it.int("shnat_yitzur") ?: return@filter false
                y in (registrationYear - 1)..registrationYear
            }
            val prices = candidates.mapNotNull { it.int("mehir") }.filter { it > 0 }.distinct()
            return if (prices.size == 1) candidates.first { it.int("mehir") == prices[0] } else null
        }
    }

    private fun firstRecord(rows: Rows?): JsonObject? = rows?.records?.firstOrNull()

    private suspend fun rows(resource: String, filters: Map<String, Any>, limit: Int = 5): Rows =
        Rows(GovIl.search(resource, filters, limit).records)

    private fun kotlinx.coroutines.CoroutineScope.primary(resource: String, key: Any): Deferred<Rows> =
        async { rows(resource, mapOf("mispar_rechev" to key)) }

    private fun kotlinx.coroutines.CoroutineScope.optional(
        label: String, sink: MutableList<String>, block: suspend () -> Rows,
    ): Deferred<Rows?> = async {
        try { block() } catch (e: GovIl.GovIlException) {
            if (e.kind == FailureKind.Offline) throw e
            sink += label; null
        }
    }

    private fun safetyFeatures(spec: JsonObject): List<SafetyFeature> {
        val out = mutableListOf<SafetyFeature>()
        fun add(flag: String, name: String, source: String? = null) {
            if (spec.flag(flag) == true) out += SafetyFeature(name, source?.let { spec.text(it) })
        }
        add("abs_ind", "ABS")
        add("bakarat_yatzivut_ind", "בקרת יציבות")
        add("blima_otomatit_nesia_leahor", "בלימה אוטומטית בנסיעה לאחור")
        add("maarechet_ezer_labalam_ind", "עזר לבלם")
        add("zihuy_matzav_hitkarvut_mesukenet_ind", "זיהוי התקרבות מסוכנת")
        add("blimat_hirum_lifnei_holhei_regel_ofanaim", "בלימת חירום לפני הולכי רגל ואופניים")
        add("zihuy_holchey_regel_ind", "זיהוי הולכי רגל", "zihuy_holchey_regel_makor_hatkana")
        add("zihuy_rechev_do_galgali", "זיהוי דו-גלגלי")
        add("bakarat_stiya_menativ_ind", "בקרת סטייה מנתיב", "bakarat_stiya_menativ_makor_hatkana")
        add("bakarat_stiya_activ_s", "שמירת נתיב אקטיבית")
        add("nitur_merhak_milfanim_ind", "ניטור מרחק מלפנים", "nitur_merhak_milfanim_makor_hatkana")
        add("bakarat_shyut_adaptivit_ind", "בקרת שיוט אדפטיבית")
        add("zihuy_beshetah_nistar_ind", "זיהוי בשטח מת")
        add("zihuy_tamrurey_tnua_ind", "זיהוי תמרורים", "zihuy_tamrurey_tnua_makor_hatkana")
        add("bakarat_mehirut_isa", "התאמת מהירות חכמה (ISA)")
        add("teura_automatit_benesiya_kadima_ind", "תאורה אוטומטית")
        add("shlita_automatit_beorot_gvohim_ind", "אורות גבוהים אוטומטיים", "shlita_automatit_beorot_gvohim_makor_hatkana")
        add("matzlemat_reverse_ind", "מצלמת רוורס")
        add("hayshaney_lahatz_avir_batzmigim_ind", "חיישני לחץ אוויר")
        add("hayshaney_hagorot_ind", "חיישני חגורות")
        add("hitnagshut_cad_shetah_met", "התרעת התנגשות בשטח מת")
        add("alco_lock", "נעילת אלכוהול")
        return out
    }
}
