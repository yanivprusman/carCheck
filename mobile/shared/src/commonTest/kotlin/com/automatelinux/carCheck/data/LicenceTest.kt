package com.automatelinux.carCheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LicenceTest {
    private fun grade(
        kind: VehicleKind,
        eu: String? = null,
        type: String? = null,
        kg: Int? = null,
        seats: Int? = null,
        hp: Double? = null,
    ) = requiredLicence(kind, eu, type, kg, seats, hp)?.grade

    @Test
    fun byGrossWeight() {
        assertEquals("B", grade(VehicleKind.Car, "M1", kg = 1_900, seats = 5))
        assertEquals("B", grade(VehicleKind.Car, "N1", kg = 3_500))
        // The Fiat Ducato 434-58-203 and the MAN 68-797-15: both C1, the MAN by 10 kg.
        assertEquals("C1", grade(VehicleKind.Heavy, "N2", "משא", kg = 3_995))
        assertEquals("C1", grade(VehicleKind.Heavy, "N2", "משא", kg = 11_990))
        assertEquals("C", grade(VehicleKind.Heavy, "N3", "משא", kg = 12_001))
    }

    @Test
    fun aPrivateCarWithNoWeightOnFileIsB() {
        // The private-car file holds only vehicles up to 3.5 t.
        assertEquals("B", grade(VehicleKind.Car, kg = null, seats = 5))
        assertNull(grade(VehicleKind.Heavy, "N2", kg = null))
    }

    @Test
    fun passengersMakeABus() {
        assertEquals("D", grade(VehicleKind.Car, "M2", kg = 3_400, seats = 15))
        assertEquals("D", grade(VehicleKind.Heavy, "M3", "אוטובוס", kg = 18_000))
        assertEquals("D", grade(VehicleKind.Public, "M3", "אוטובוס צבורי עירוני", kg = 18_300, seats = 33))
        assertEquals("D1", grade(VehicleKind.Public, "M2", "אוטובוס זעיר צבורי", kg = 4_800, seats = 16))
        assertEquals("D1", grade(VehicleKind.Public, "M1", "מונית", kg = 2_300, seats = 5))
    }

    @Test
    fun motorcyclesByHorsepower() {
        assertEquals("A2", grade(VehicleKind.Motorcycle, hp = 14.24))
        assertEquals("A1", grade(VehicleKind.Motorcycle, hp = 46.78))
        assertEquals("A", grade(VehicleKind.Motorcycle, hp = 47.5))
        assertNull(grade(VehicleKind.Motorcycle, hp = null))
    }

    @Test
    fun notDecidedByTheRegistry() {
        assertNull(grade(VehicleKind.Heavy, "O2", "גרור נתמך", kg = 750))
        assertNull(grade(VehicleKind.Heavy, "T", "טרקטור", kg = 900))
    }
}
