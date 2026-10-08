package com.automatelinux.carCheck

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.automatelinux.carCheck.ui.CarCheckModel
import com.automatelinux.carCheck.ui.HostActions
import com.automatelinux.carCheck.ui.ReportScreen
import com.automatelinux.carCheck.ui.SearchScreen
import com.automatelinux.carCheck.ui.theme.AppTheme
import com.automatelinux.carCheck.ui.theme.LocalPalette

/** Shared entry: the search screen, and the report over it once a plate has answered. */
@Composable
fun App(model: CarCheckModel, host: HostActions) {
    // Hebrew-first: the whole UI is RTL whatever the device locale is.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AppTheme {
            val state by model.state.collectAsState()
            val palette = LocalPalette.current
            Surface(modifier = Modifier.fillMaxSize().background(palette.page), color = palette.page) {
                AnimatedContent(
                    targetState = state.report,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 8 } + fadeOut())
                        } else {
                            (slideInHorizontally { it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
                        }
                    },
                    label = "screen",
                ) { report ->
                    if (report == null) {
                        SearchScreen(
                            state = state,
                            onInput = model::setInput,
                            onSearch = model::search,
                            onOpenRecent = model::search,
                            onRemoveRecent = model::removeRecent,
                            onClearRecents = model::clearRecents,
                            onScan = host::scanPlate,
                            onPickPlate = model::search,
                        )
                    } else {
                        ReportScreen(report = report, photos = model.photos, host = host, onBack = model::back)
                    }
                }
            }
        }
    }
}
