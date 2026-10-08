package com.automatelinux.carCheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CarPhotosTest {
    private fun hit(he: String, en: String?) = CarPhotos.ArticleHit(he, en)

    @Test
    fun picksTheArticleAboutThisModelNotTheFirstHit() {
        val hits = listOf(hit("טויוטה AE85", "Toyota AE85"), hit("טויוטה קורולה", "Toyota Corolla"))
        assertEquals("Toyota Corolla", CarPhotos.pickArticle(hits, "טויוטה", "COROLLA")?.enTitle)
    }

    @Test
    fun refusesAnotherMakesArticle() {
        val hits = listOf(hit("מרצדס-בנץ סיטאן", "Mercedes-Benz Citan"))
        assertNull(CarPhotos.pickArticle(hits, "רנו", "KANGOO"))
    }

    @Test
    fun matchesThroughAccentsAndPunctuation() {
        assertEquals("Škoda Octavia", CarPhotos.pickArticle(listOf(hit("סקודה אוקטביה", "Škoda Octavia")), "סקודה", "OCTAVIA")?.enTitle)
        assertEquals("יונדאי i20", CarPhotos.pickArticle(listOf(hit("יונדאי i20", "Hyundai i20")), "יונדאי", "I-20")?.heTitle)
    }

    @Test
    fun yearsFromFileNames() {
        assertEquals(0, CarPhotos.yearDistance("File:2007-2010 Toyota Corolla (ZRE152R).jpg", 2008))
        assertEquals(0, CarPhotos.yearDistance("File:2019–22 Toyota Corolla.jpg", 2021))
        assertEquals(3, CarPhotos.yearDistance("File:2014 Toyota Corolla 1.8 LE.jpg", 2017))
        assertEquals(1000, CarPhotos.yearDistance("File:Toyota Corolla E110 liftback.JPG", 2017))
    }

    @Test
    fun ranksByYearAndDropsWhatIsNotTheModel() {
        fun f(t: String, mime: String = "image/jpeg") = CarPhotos.FileInfo(t, mime, "thumb/$t", "page/$t")
        val files = listOf(
            f("File:1968 Toyota Corolla 1100 Deluxe.jpg"),
            f("File:2013-2016 Toyota Corolla (ZRE172R) SX sedan.jpg"),
            f("File:Toyota Corolla E110 liftback.JPG"),
            f("File:Toyota logo Corolla.png", "image/png"),
            f("File:Honda Civic 2015.jpg"),
            f("File:Corolla badge.svg", "image/svg+xml"),
        )
        val ranked = CarPhotos.rankPhotos(files, "COROLLA", 2015).map { it.fileName }
        assertEquals(
            listOf(
                "2013-2016 Toyota Corolla (ZRE172R) SX sedan.jpg",
                "1968 Toyota Corolla 1100 Deluxe.jpg",
                "Toyota Corolla E110 liftback.JPG",
            ),
            ranked,
        )
    }

    @Test
    fun heavyFileCodesResolveToTheirModel() {
        fun art(make: String, model: String?) = CarPhotos.knownModel(make, model)?.enArticle
        assertEquals("Fiat Ducato", art("פיאט", "250"))
        assertEquals("Fiat Ducato", art("פיאט", "250E7MFC"))
        assertEquals("Mercedes-Benz Sprinter", art("מרצדס בנץ", "SPRINTER 907.657"))
        assertEquals("Mercedes-Benz Sprinter", art("מרצדס בנץ", "519CDI 906.657"))
        assertEquals("Mercedes-Benz Sprinter", art("מרצדס בנץ", "907.657"))
        assertEquals("Iveco Daily", art("איווקו", "70C18"))
        assertEquals("Isuzu Elf", art("איסוזו", "NPR75"))
        assertEquals("Isuzu Forward", art("איסוזו", "FSR90"))
        assertEquals("Chevrolet Silverado", art("שברולט", "CK20743"))
        assertEquals("Ford Super Duty", art("פורד", "W3B"))
        assertEquals("Volvo FH", art("וולבו", "FH84FR"))
        assertEquals("DAF CF", art("דאף-הולנד", "FAG CF340AD"))
        assertEquals("DAF LF", art("דאף", "FA LF210H12"))
    }

    @Test
    fun anUnknownCodeOrAnotherMakeGetsNothing() {
        assertNull(CarPhotos.knownModel("פיאט", "500"))
        assertNull(CarPhotos.knownModel("טויוטה", "250"))
        assertNull(CarPhotos.knownModel("וולבו", "B11R"))
        assertNull(CarPhotos.knownModel("פיאט", null))
    }
}
