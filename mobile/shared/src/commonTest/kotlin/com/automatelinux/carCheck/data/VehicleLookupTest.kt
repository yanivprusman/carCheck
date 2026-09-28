package com.automatelinux.carCheck.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleLookupTest {
    @Test
    fun specYearForACarOfUnknownYear() {
        // Registered February 2017, specs on file for 2016 and 2017: the 2017 spec.
        assertEquals(2017, VehicleLookup.chooseYear(listOf(2016, 2017), 2017))
        // Registered 2018, the model's last spec year is 2017: the 2017 spec.
        assertEquals(2017, VehicleLookup.chooseYear(listOf(2016, 2017), 2018))
        // Registered before the first spec year on file: the earliest.
        assertEquals(2016, VehicleLookup.chooseYear(listOf(2016, 2017), 2015))
        // No registration date at all: the latest.
        assertEquals(2017, VehicleLookup.chooseYear(listOf(2016, 2017), null))
        assertNull(VehicleLookup.chooseYear(emptyList(), 2017))
    }

    private fun price(year: Int, nis: Int) = buildJsonObject { put("shnat_yitzur", year); put("mehir", nis) }

    @Test
    fun listPriceOnlyWhenTheCandidateYearsAgree() {
        val same = listOf(price(2015, 120000), price(2016, 134990), price(2017, 134990))
        assertEquals(134990, VehicleLookup.unambiguousPrice(same, 2017)?.int("mehir"))
        val differ = listOf(price(2016, 129990), price(2017, 134990))
        assertNull(VehicleLookup.unambiguousPrice(differ, 2017))
        assertNull(VehicleLookup.unambiguousPrice(same, null))
        assertNull(VehicleLookup.unambiguousPrice(emptyList(), 2017))
    }
}
