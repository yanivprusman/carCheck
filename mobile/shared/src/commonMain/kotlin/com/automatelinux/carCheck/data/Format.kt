package com.automatelinux.carCheck.data

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * The registries write dates four ways: `2027-09-30`, `2013-9` (month only),
 * `20230419` (an integer), and `202303` (year-month integer). All come out as
 * the day-first form an Israeli reader expects.
 */
fun formatDate(raw: String?): String? {
    val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val iso = Regex("""^(\d{4})-(\d{1,2})(?:-(\d{1,2}))?""").find(s)
    if (iso != null) {
        val (y, m, d) = iso.destructured
        return if (d.isEmpty()) "${m.padStart(2, '0')}/$y" else "${d.padStart(2, '0')}.${m.padStart(2, '0')}.$y"
    }
    if (s.length == 8 && s.all { it.isDigit() }) {
        return "${s.substring(6)}.${s.substring(4, 6)}.${s.substring(0, 4)}"
    }
    if (s.length == 6 && s.all { it.isDigit() }) {
        return "${s.substring(4)}/${s.substring(0, 4)}"
    }
    return s
}

/**
 * CKAN's `last_modified` ("2026-09-28T02:46:44.138164") is UTC with no zone marker; shown
 * as the local wall-clock time it happened at, "05:46, 28.09.2026".
 */
fun formatUtcDateTime(raw: String?): String? {
    val s = raw?.trim() ?: return null
    if (s.length < 19 || s[10] != 'T') return formatDate(s)
    val local = try {
        LocalDateTime.parse(s.substring(0, 19)).toInstant(TimeZone.UTC).toLocalDateTime(TimeZone.currentSystemDefault())
    } catch (_: Exception) {
        return formatDate(s)
    }
    val hh = local.hour.toString().padStart(2, '0')
    val mm = local.minute.toString().padStart(2, '0')
    return "$hh:$mm, ${formatDate(local.date.toString())}"
}

fun isoDateOrNull(raw: String?): LocalDate? {
    val s = raw?.trim()?.takeIf { it.length >= 10 } ?: return null
    return try { LocalDate.parse(s.substring(0, 10)) } catch (_: Exception) { null }
}

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** 114529 → "114,529". */
fun formatInt(n: Int): String {
    val s = n.toString()
    val neg = s.startsWith("-")
    val digits = if (neg) s.substring(1) else s
    val sb = StringBuilder()
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return (if (neg) "-" else "") + sb
}

fun formatDouble(d: Double): String {
    val r = kotlin.math.round(d * 10) / 10
    return if (r == kotlin.math.floor(r)) r.toInt().toString() else r.toString()
}

private val COUNTRY_SUFFIXES = listOf(
    "יפן", "גרמניה", "צרפת", "קוריאה", "סין", "ארהב\"", "ארה\"ב", "ארהב", "ספרד", "איטליה", "בריטניה",
    "אנגליה", "צ'כיה", "צכיה", "שבדיה", "הודו", "טורקיה", "תאילנד", "בלגיה", "הולנד", "פולין",
    "סלובקיה", "הונגריה", "רומניה", "מקסיקו", "ברזיל", "אוסטריה", "פורטוגל", "טאיוון", "מלזיה",
    "אינדונזיה", "דרום אפריקה", "ארגנטינה", "אוסטרליה", "רוסיה", "אוקראינה", "וייטנאם", "מרוקו",
    "סרביה", "סלובניה", "פינלנד", "שוויץ", "ישראל", "אירלנד", "דנמרק", "יוון", "גרמנ",
)

/** "טויוטה יפן" → "טויוטה". The registry appends the country of manufacture to the make. */
fun cleanMake(raw: String?, country: String?): String? {
    var s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val c = country?.trim()
    if (!c.isNullOrEmpty() && s.endsWith(c) && s.length > c.length) {
        s = s.substring(0, s.length - c.length).trim()
    }
    for (suffix in COUNTRY_SUFFIXES) {
        if (s.endsWith(" $suffix")) { s = s.substring(0, s.length - suffix.length).trim(); break }
    }
    return s.ifEmpty { raw.trim() }
}

/** ARGB for the colour the registry names, or null when a swatch would be a guess. */
fun colorSwatch(name: String?): Long? {
    val n = name?.trim() ?: return null
    fun has(vararg keys: String) = keys.any { n.contains(it) }
    return when {
        has("לבן", "שנהב", "פנינה") -> 0xFFF5F5F2
        has("שחור") -> 0xFF1B1B1F
        has("כסף", "כסוף") -> 0xFFC4C7CE
        has("אפור", "גרפיט", "פחם") -> 0xFF7D818A
        has("אדום", "בורדו", "יין") -> 0xFFB8232E
        has("תכלת") -> 0xFF7FB8E6
        has("כחול") -> 0xFF2E56A6
        has("ירוק", "ירקרק", "זית") -> 0xFF3F8E4F
        has("צהוב") -> 0xFFF2C51D
        has("כתום") -> 0xFFE7742A
        has("חום", "ברונזה", "שוקולד") -> 0xFF6E4A2F
        has("בז", "שמפניה", "קרם") -> 0xFFD8C7A5
        has("זהב") -> 0xFFC9A227
        has("סגול", "לילך") -> 0xFF6B4FA0
        has("ורוד") -> 0xFFE38BB5
        has("טורקיז") -> 0xFF2FA8A0
        else -> null
    }
}

/** "כלמוביל" phone strings arrive as `*5606` or `1-800-22-1514`; keep them dialable. */
fun dialable(phone: String?): String? = phone?.trim()?.takeIf { it.any { c -> c.isDigit() } }
