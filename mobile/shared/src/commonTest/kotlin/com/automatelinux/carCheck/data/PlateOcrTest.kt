package com.automatelinux.carCheck.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlateOcrTest {
    private fun plates(vararg lines: String) = lines.flatMap { PlateOcr.platesIn(it) }

    @Test
    fun readsThePrintedForms() {
        assertEquals(listOf("43458203"), plates("434-58-203"))
        assertEquals(listOf("43458203"), plates("434 58 203"))
        assertEquals(listOf("43458203"), plates("43458203"))
        assertEquals(listOf("43458203"), plates("434·58·203"))
        assertEquals(listOf("43458203"), plates("IL 434-58-203"))
        assertEquals(listOf("1234567"), plates("12-345-67"))
    }

    @Test
    fun fixesLookAlikeLettersOnlyInsideDigitRuns() {
        assertEquals(listOf("43458203"), plates("434-5B-2O3"))
        assertEquals(listOf("1234567"), plates("I2-345-67"))
        assertEquals(emptyList(), plates("Bosch"))
        assertEquals(emptyList(), plates("ISO 9001"))
    }

    @Test
    fun stripsTheBandGluedToTheNumber() {
        assertEquals(listOf("43458203"), plates("I434:58-203"))    // as read off the Ducato listing
        assertEquals(listOf("43458203"), plates("1434:58-203"))    // the band's I read as a digit
        assertEquals(listOf("43458203"), plates("|434-58-203"))
        assertEquals(listOf("43458203"), plates("IL434-58-203"))
        assertEquals(listOf("43458203"), plates("IL 434-58-203"))
        assertEquals(listOf("43458203"), plates("I43458203"))
        assertEquals(listOf("1234567"), plates("112-345-67"))      // seven digits with the band's 1
        assertEquals(emptyList(), plates("143458203"))             // no separators: cannot be told from an ID
    }

    @Test
    fun ignoresWhatIsNotAPlate() {
        assertEquals(emptyList(), plates("28.09.2026"))       // a date: a four-digit group
        assertEquals(emptyList(), plates("28.9.2026"))        // seven digits, still a date
        assertEquals(emptyList(), plates("052-1234567"))      // a phone number
        assertEquals(emptyList(), plates("052 123 4567"))
        assertEquals(emptyList(), plates("160,000 ₪"))        // a price
        assertEquals(emptyList(), plates("59,000 ק\"מ"))       // mileage
        assertEquals(emptyList(), plates("2023"))              // a year
        assertEquals(emptyList(), plates("434 58203"))         // a dash swallowed into a five-digit group
        assertEquals(emptyList(), plates("123456789"))         // an ID number
    }

    @Test
    fun rejectsShortPlatesFromPictures() {
        assertEquals(emptyList(), plates("123-45"))
        assertEquals(emptyList(), plates("870"))
    }

    @Test
    fun ordersByHeightAndDedupes() {
        val found = PlateOcr.candidates(
            listOf(
                OcrLine("129-31-201", 18f),
                OcrLine("434-58-203", 42f),
                OcrLine("43458203", 30f),
                OcrLine("₪ 160,000", 60f),
                OcrLine("שנה 2023", 25f),
            ),
        )
        assertEquals(listOf("43458203", "12931201"), found.map { it.digits })
        assertEquals(42f, found[0].size)
    }
}
