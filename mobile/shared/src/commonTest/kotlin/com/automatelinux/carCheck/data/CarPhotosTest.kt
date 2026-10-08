package com.automatelinux.carCheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CarPhotosTest {
    private fun report(
        kind: VehicleKind,
        make: String?,
        model: String?,
        commercialName: String? = null,
        year: Int? = null,
        vehicleType: String? = null,
        grossWeightKg: Int? = null,
    ) = VehicleReport(
        plate = Plate.parse("12345678")!!, kind = kind, status = RegistrationStatus.Inactive,
        make = make, makeCountry = null, model = model, commercialName = commercialName, trim = null, year = year,
        color = null, bodyType = null, vehicleType = vehicleType, euCategory = null,
        lastTest = null, onRoad = null, firstRegistration = null, ownership = null, chassis = null, engineNumber = null,
        ownershipHistory = emptyList(),
        fuel = null, engineModel = null, displacementCc = null, horsepower = null, drive = null, automatic = null,
        driveTechnology = null, grossWeightKg = grossWeightKg, curbWeightKg = null, towBrakedKg = null,
        towUnbrakedKg = null, towHitch = null,
        doors = null, seats = null, tyreFront = null, tyreRear = null, tyreLoadCode = null, tyreSpeedCode = null,
        safetyLevel = null, safetyScore = null, airbags = null, safetyFeatures = emptyList(),
        pollutionGroup = null, greenIndex = null, co2Wltp = null, noxWltp = null, pmWltp = null, particleFilterFitted = null,
        kmAtLastTest = null, structuralChange = null, gasConversion = null, colorChange = null, tyreChange = null, originality = null,
        disabledTagSince = null, importType = null, recalls = emptyList(),
        listPriceNis = null, importer = null, unavailable = emptyList(), dataAsOf = null,
    )

    @Test
    fun aPrivateCarNeedsNoKindWord() {
        assertNull(CarPhotos.kindWord(report(VehicleKind.Car, "רנו", "KW0", commercialName = "KANGOO", year = 2017)))
    }

    @Test
    fun aHeavyVehicleSaysWhatItIs() {
        assertEquals("רכב מסחרי", CarPhotos.kindWord(report(VehicleKind.Heavy, "פיאט", "250", vehicleType = "משא", grossWeightKg = 3995)))
        assertEquals("משאית", CarPhotos.kindWord(report(VehicleKind.Heavy, "מאן", "12163LL", vehicleType = "משא", grossWeightKg = 11990)))
        assertEquals("אוטובוס", CarPhotos.kindWord(report(VehicleKind.Heavy, "וולבו", "B11R", vehicleType = "אוטובוס")))
        assertEquals("אופנוע", CarPhotos.kindWord(report(VehicleKind.Motorcycle, "ימאהה", "MT07")))
    }
}
