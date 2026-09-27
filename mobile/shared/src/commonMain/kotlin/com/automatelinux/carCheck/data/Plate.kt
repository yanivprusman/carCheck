package com.automatelinux.carCheck.data

/**
 * An Israeli registration number.
 *
 * The registry keys every vehicle by `mispar_rechev`, a plain integer — so a plate is
 * its digits with leading zeros dropped, and the dashes are presentation only.
 * Current plates carry 7 digits (12-345-67) or 8 (123-45-678); older ones fewer —
 * the registry still holds a 1955 Chevrolet at plate 870, so the floor is two digits.
 */
data class Plate(val digits: String) {
    val number: Long get() = digits.toLong()

    /** Zero-padded to 8, the way the pre-2017 cancellation archives store it as text. */
    val padded8: String get() = digits.padStart(8, '0')

    /** As printed on the plate. */
    val display: String get() = format(digits)

    companion object {
        const val MIN_DIGITS = 2
        const val MAX_DIGITS = 8

        /**
         * Read off a picture, a number is trusted only at a modern plate's length. Older
         * short plates exist, but a picture is full of short digit runs that are not plates.
         */
        const val PHOTO_MIN_DIGITS = 7

        fun parse(raw: String): Plate? {
            val d = raw.filter { it.isDigit() }.trimStart('0')
            return if (d.length in MIN_DIGITS..MAX_DIGITS) Plate(d) else null
        }

        /** Dashes for the group layout the plate would carry; partial input groups as far as it goes. */
        fun format(digits: String): String {
            val groups = groupsFor(digits.length)
            val sb = StringBuilder()
            var i = 0
            for (g in groups) {
                if (i >= digits.length) break
                if (sb.isNotEmpty()) sb.append('-')
                sb.append(digits, i, minOf(digits.length, i + g))
                i += g
            }
            return sb.toString()
        }

        /**
         * 8 digits: 3-2-3; 7: 2-3-2; 6: 3-3; 5: 2-3; anything shorter is one group.
         * While typing, the 7-digit pattern is assumed until an 8th digit arrives.
         */
        fun groupsFor(length: Int): IntArray = when {
            length >= 8 -> intArrayOf(3, 2, 3)
            length == 7 -> intArrayOf(2, 3, 2)
            length == 6 -> intArrayOf(3, 3)
            length == 5 -> intArrayOf(2, 3)
            length <= 4 -> intArrayOf(length.coerceAtLeast(1))
            else -> intArrayOf(2, 3, 2)
        }
    }
}
