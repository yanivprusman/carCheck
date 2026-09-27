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
 * number as one line — "434-58-203", "434 58 203", "43458203", sometimes with the
 * band's "IL" in front — and now and then swaps a digit for a look-alike letter
 * (O/0, I/1, S/5, B/8). The same picture also holds things that are not plates: a
 * date, a price, a phone number, the mileage. So a run of digits counts only when it
 * has a plate's length and its separators, if any, split it into printed groups.
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
        for (cluster in clusters(fixLookAlikes(text))) {
            val groups = cluster.split(*SEPARATORS.toCharArray()).filter { it.isNotEmpty() }
            val total = groups.sumOf { it.length }
            if (total !in Plate.PHOTO_MIN_DIGITS..Plate.MAX_DIGITS) continue
            // One unbroken run, or printed groups: a date's "2026" or a "58203" that swallowed a dash is neither.
            if (groups.size > 1 && groups.any { it.length > 3 }) continue
            val plate = Plate.parse(groups.joinToString("")) ?: continue
            if (plate.digits.length >= Plate.PHOTO_MIN_DIGITS && plate.digits !in out) out += plate.digits
        }
        return out
    }

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

    /** Within a whitespace token that is mostly digits, a look-alike letter is a misread digit. */
    private fun fixLookAlikes(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) { sb.append(text[i]); i++; continue }
            var j = i
            while (j < text.length && !text[j].isWhitespace()) j++
            val token = text.substring(i, j)
            val digits = token.count { it.isDigit() }
            val mapped = if (token.length >= 3 && digits * 2 >= token.length) {
                token.map { lookAlike[it] ?: it }.joinToString("")
            } else token
            sb.append(mapped)
            i = j
        }
        return sb.toString()
    }
}
