package com.automatelinux.carCheck.data

/** The driving-licence grade a vehicle needs, and the registry fact that decides it. */
data class RequiredLicence(val grade: String, val because: String)

/**
 * The licence grade this vehicle needs, from the Ministry of Transport's grades table
 * ("דרגות רישיון הנהיגה בישראל", gov.il BlobFolder RISHUY_rank_license.pdf, read 2026-10-08):
 *
 *   A2  motorcycle up to 14.6 hp (11 kW)        A1  up to 47.46 hp (35 kW)    A  above that
 *   B   up to 3,500 kg gross, up to 8 passengers besides the driver
 *   C1  commercial / work vehicle over 3,500 kg and up to 12,000 kg
 *   C   over 12,000 kg
 *   D1  taxi, patrol vehicle, public minibus up to 5,000 kg and 16 passengers besides the driver
 *   D   bus
 *
 * The registry does not state the grade; it holds what decides it — gross weight, EU category,
 * passenger seats, a motorcycle's horsepower. Null where those do not decide it: a trailer is not
 * driven, a "טרקטור" row may be a farm tractor or an ATV, a motorcycle may have no power on file.
 */
fun requiredLicence(
    kind: VehicleKind,
    euCategory: String?,
    vehicleType: String?,
    grossWeightKg: Int?,
    seats: Int?,
    horsepower: Double?,
): RequiredLicence? {
    val eu = euCategory?.trim()?.uppercase().orEmpty()
    val type = vehicleType.orEmpty()
    if (kind == VehicleKind.Motorcycle) {
        val hp = horsepower ?: return null
        val shown = formatDouble(hp) + " כ״ס"
        return when {
            hp <= 14.6 -> RequiredLicence("A2", "אופנוע עד 14.6 כ״ס · $shown")
            hp <= 47.46 -> RequiredLicence("A1", "אופנוע עד 47.46 כ״ס · $shown")
            else -> RequiredLicence("A", "אופנוע מעל 47.46 כ״ס · $shown")
        }
    }
    if (eu.startsWith("O") || type.contains("גרור") || type.contains("נגרר")) return null
    if (eu.startsWith("T") || type.contains("טרקטור")) return null
    val weight = grossWeightKg?.let { "משקל כולל ${formatInt(it)} ק״ג" }
    if (kind == VehicleKind.Public) {
        if (type.contains("מונית")) return RequiredLicence("D1", "מונית")
        val minibus = eu == "M2" && (seats ?: Int.MAX_VALUE) <= 16 && (grossWeightKg ?: Int.MAX_VALUE) <= 5_000
        if (minibus) return RequiredLicence("D1", "אוטובוס זעיר ציבורי")
        if (eu == "M2" || eu == "M3" || type.contains("אוטובוס")) return RequiredLicence("D", "אוטובוס")
        return null
    }
    // More than 8 passengers besides the driver is a bus, whatever it weighs.
    if (eu == "M2" || eu == "M3" || type.contains("אוטובוס") || (seats ?: 0) > 9) return RequiredLicence("D", "אוטובוס")
    return when {
        grossWeightKg == null ->
            // The private-car file holds only vehicles up to 3.5 t; anything heavier is in its own file.
            if (kind == VehicleKind.Car) RequiredLicence("B", "רכב פרטי/מסחרי עד 3,500 ק״ג") else null
        grossWeightKg <= 3_500 -> RequiredLicence("B", "עד 3,500 ק״ג · $weight")
        grossWeightKg <= 12_000 -> RequiredLicence("C1", "מעל 3,500 ועד 12,000 ק״ג · $weight")
        else -> RequiredLicence("C", "מעל 12,000 ק״ג · $weight")
    }
}
