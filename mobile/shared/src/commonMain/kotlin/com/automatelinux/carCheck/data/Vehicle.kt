package com.automatelinux.carCheck.data

/** Which registry the primary record came from — it decides which facts can exist at all. */
enum class VehicleKind { Car, Motorcycle, Heavy, Public, PersonalImport }

sealed class RegistrationStatus {
    /** On the road. [validUntil] is the licence expiry (ISO date) when the registry has it. */
    data class Active(val validUntil: String?) : RegistrationStatus()
    /** Final cancellation — scrapped, exported, or written off. */
    data class OffRoad(val date: String?) : RegistrationStatus()
    /** Registered but not licensed: never renewed, or between owners. */
    data object Inactive : RegistrationStatus()
    /** The file that says is empty mid-reload; the car exists, its licensing state is not known right now. */
    data object Unknown : RegistrationStatus()
}

data class Recall(
    val id: Int,
    val type: String?,
    val category: String?,
    val description: String?,
    val fix: String?,
    val importer: String?,
    val phone: String?,
    val website: String?,
    val opened: String?,
)

data class OwnershipChange(val yearMonth: String, val ownership: String)

/** A safety or driver-assistance system the model file says this vehicle has. */
data class SafetyFeature(val name: String, val source: String?)

/**
 * Everything the registries know about one plate, already joined.
 *
 * Nulls mean "the registry does not say", and every section of the report is
 * built from whichever fields exist, so a motorcycle and a bus get honest,
 * shorter reports rather than rows of dashes.
 */
data class VehicleReport(
    val plate: Plate,
    val kind: VehicleKind,
    val status: RegistrationStatus,

    // Identity
    val make: String?,
    val makeCountry: String?,
    val model: String?,
    val commercialName: String?,
    val trim: String?,
    val year: Int?,
    val color: String?,
    val bodyType: String?,
    val vehicleType: String?,
    val euCategory: String?,

    // Licensing
    val lastTest: String?,
    val onRoad: String?,
    val firstRegistration: String?,
    val ownership: String?,
    val chassis: String?,
    val engineNumber: String?,
    val ownershipHistory: List<OwnershipChange>,

    // Engine and drivetrain
    val fuel: String?,
    val engineModel: String?,
    val displacementCc: Int?,
    val horsepower: Int?,
    val drive: String?,
    val automatic: Boolean?,
    val driveTechnology: String?,
    val grossWeightKg: Int?,
    val curbWeightKg: Int?,
    val towBrakedKg: Int?,
    val towUnbrakedKg: Int?,
    val towHitch: Boolean?,

    // Body
    val doors: Int?,
    val seats: Int?,
    val tyreFront: String?,
    val tyreRear: String?,
    val tyreLoadCode: String?,
    val tyreSpeedCode: String?,

    // Safety
    val safetyLevel: Int?,
    val safetyScore: Double?,
    val airbags: Int?,
    val safetyFeatures: List<SafetyFeature>,

    // Emissions
    val pollutionGroup: Int?,
    val greenIndex: Double?,
    val co2Wltp: Double?,
    val noxWltp: Double?,
    val pmWltp: Double?,
    val particleFilterFitted: String?,

    // History
    val kmAtLastTest: Int?,
    val structuralChange: Boolean?,
    val gasConversion: Boolean?,
    val colorChange: Boolean?,
    val tyreChange: Boolean?,
    val originality: String?,

    // Flags
    val disabledTagSince: String?,
    val importType: String?,
    val recalls: List<Recall>,

    // Money
    val listPriceNis: Int?,
    val importer: String?,

    /** Registry files that failed to answer this time; their sections say so instead of going missing. */
    val unavailable: List<String>,
    val dataAsOf: String?,
    /**
     * The main private-car file was empty (data.gov.il reloads it for hours after each
     * nightly upload) and this report was built from the files that were up: the sibling
     * half of the same row, the model specification, history, recalls. Licensing state,
     * production year, colour, ownership and chassis are missing until the reload ends.
     */
    val mainRegistryRefreshing: Boolean = false,
    /** Set when the main-file row came from our backend's copy: when that copied file was uploaded (UTC). */
    val mirrorAsOf: String? = null,
    /** Why the copy could not be used, when it was tried and the sibling files answered instead. */
    val mirrorNote: String? = null,
) {
    /** "טויוטה קורולה", or whatever the registry can say. */
    val title: String
        get() = listOfNotNull(make, commercialName ?: model).joinToString(" ").ifBlank { plate.display }

    val subtitle: String
        get() = listOfNotNull(year?.toString(), fuel, color).joinToString(" · ")
}
