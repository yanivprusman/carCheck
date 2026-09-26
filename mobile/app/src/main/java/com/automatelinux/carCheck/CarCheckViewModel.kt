package com.automatelinux.carCheck

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.carCheck.data.RecentStore
import com.automatelinux.carCheck.ui.CarCheckModel
import com.russhwolf.settings.SharedPreferencesSettings

/** Keeps the shared [CarCheckModel] alive across rotation; nothing else lives here. */
class CarCheckViewModel(app: Application) : AndroidViewModel(app) {
    val model = CarCheckModel(
        scope = viewModelScope,
        recents = RecentStore(SharedPreferencesSettings(app.getSharedPreferences("carCheck", Context.MODE_PRIVATE))),
    )
}
