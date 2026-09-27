package com.automatelinux.carCheck.data

/** One line of text an OCR engine read off a picture, and how tall it stood in the frame (pixels). */
data class OcrLine(val text: String, val height: Float)

/**
 * A registration number found in a picture. [size] is the text height it was read at —
 * the bigger the digits, the closer the car, so in a photo of one vehicle with others
 * behind it the largest candidate is the subject.
 */
data class PlateCandidate(val digits: String, val size: Float)

/**
 * Finds registration numbers in OCR output.
 *
 * A plate is seven digits (2-3-2) or eight (3-2-3). OCR usually returns the stamped
 * number as one line — "434-58-203", "434 58 203", "43458203" — and now and then
 * swaps a digit for a look-alike letter (O/0, I/1, S/5, B/8). The band's "IL" often
 * comes glued to the number ("I434:58-203", measured on a listing's photo), and its I
 * is itself sometimes read as a 1. The same picture also holds things that are not
 * plates: a date, a price, a phone number, the mileage, an ID number. So a run of
 * digits counts only when it has a plate's length and its separators, if any, split
 * it into printed groups.
 */
object PlateOcr {
    private const val SEPARATORS = " \t -–—.:·•"

    /** Letters OCR gives back for a stamped digit, mapped to the digit; applied only inside digit-heavy tokens. */
    private val lookAlike = mapOf(
        'O' to '0', 'o' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', '|' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5',
        'G' to '6',
        'B' to '8',
    )

    /** Every plate in [lines], largest first, each number once. */
    fun candidates(lines: List<OcrLine>): List<PlateCandidate> {
        val best = HashMap<String, Float>()
        for (line in lines) {
            for (digits in platesIn(line.text)) {
                val prev = best[digits]
                if (prev == null || line.height > prev) best[digits] = line.height
            }
        }
        return best.entries.map { PlateCandidate(it.key, it.value) }.sortedByDescending { it.size }
    }

    /** The registration numbers one OCR line contains, normalised the way the registry keys them. */
    fun platesIn(text: String): List<String> {
        val out = ArrayList<String>()
        // A look-alike at a token's edge is either a misread digit or the band's letter glued on;
        // both readings are tried, and a plate only comes out of the one that is a plate.
        for (variant in setOf(fixLookAlikes(text, edges = true), fixLookAlikes(text, edges = false))) {
            for (cluster in clusters(variant)) {
                val groups = cluster.split(*SEPARATORS.toCharArray()).filter { it.isNotEmpty() }
                val digits = plateDigits(groups) ?: continue
                val plate = Plate.parse(digits) ?: continue
                if (plate.digits.length >= Plate.PHOTO_MIN_DIGITS && plate.digits !in out) out += plate.digits
            }
        }
        return out
    }

    /** The number a run of digit groups spells, or null when the run is not a plate. */
    private fun plateDigits(groups: List<String>): String? {
        val total = groups.sumOf { it.length }
        val plateLength = Plate.PHOTO_MIN_DIGITS..Plate.MAX_DIGITS
        val sizes = groups.map { it.length }
        // One unbroken run is taken at face value.
        if (groups.size == 1) return groups[0].takeIf { total in plateLength }
        // Grouped the way a plate is printed.
        if (sizes in PRINTED) return groups.joinToString("")
        // The band's I read as a digit and glued to the first group: "1434:58-203". Dropping it
        // leaves a printed layout — which outranks reading "112-345-67" as eight digits, since
        // eight digits never print 3-3-2.
        if (groups.size == 3 && groups[0].length in 3..4) {
            val trimmed = listOf(groups[0].drop(1)) + groups.drop(1)
            if (trimmed.map { it.length } in PRINTED) return trimmed.joinToString("")
        }
        // Separators OCR misplaced, as long as no group is longer than a printed one: a date's
        // "2026" or a "58203" that swallowed a dash is not a plate.
        if (total in plateLength && groups.all { it.length <= 3 }) return groups.joinToString("")
        return null
    }

    private val PRINTED = setOf(listOf(2, 3, 2), listOf(3, 2, 3))

    /** Maximal runs of digits and separators, trimmed of separators at both ends. */
    private fun clusters(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() {
            val s = sb.toString().trim { it in SEPARATORS }
            if (s.any { it.isDigit() }) out += s
            sb.clear()
        }
        for (c in text) {
            if (c.isDigit() || c in SEPARATORS) sb.append(c) else flush()
        }
        flush()
        return out
    }

    /**
     * Within a whitespace token that is mostly digits, a look-alike letter is a misread digit.
     * With [edges] off, the first and last character are left alone.
     */
    private fun fixLookAlikes(text: String, edges: Boolean): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) { sb.append(text[i]); i++; continue }
            var j = i
            while (j < text.length && !text[j].isWhitespace()) j++
            val token = text.substring(i, j)
            val digits = token.count { it.isDigit() }
            val mapped = if (token.length >= 3 && digits * 2 >= token.length) {
                token.mapIndexed { k, c -> if (!edges && (k == 0 || k == token.lastIndex)) c else (lookAlike[c] ?: c) }.joinToString("")
            } else token
            sb.append(mapped)
            i = j
        }
        return sb.toString()
    }
}
