package com.automatelinux.carCheck

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.carCheck.data.CarPhotos
import com.automatelinux.carCheck.data.MarketPrices
import com.automatelinux.carCheck.data.RecentStore
import com.automatelinux.carCheck.data.RegistryMirror
import com.automatelinux.carCheck.data.VehicleLookup
import com.automatelinux.carCheck.ui.CarCheckModel
import com.russhwolf.settings.SharedPreferencesSettings

/** Keeps the shared [CarCheckModel] alive across rotation; nothing else lives here. */
class CarCheckViewModel(app: Application) : AndroidViewModel(app) {
    val model = CarCheckModel(
        scope = viewModelScope,
        recents = RecentStore(SharedPreferencesSettings(app.getSharedPreferences("carCheck", Context.MODE_PRIVATE))),
        // The backend this build was made for keeps a copy of the main registry file, for the
        // hours each night when data.gov.il's own table is empty.
        lookup = VehicleLookup(mirror = RegistryMirror(BuildConfig.API_BASE_URL)),
        // and searches Google Images once per model, keeping the photos for every later plate.
        photos = CarPhotos(BuildConfig.API_BASE_URL),
        // and reads Yad2's asking prices for the model and year, kept for a week.
        prices = MarketPrices(BuildConfig.API_BASE_URL),
    )
}
