package com.automatelinux.carCheck.util

/** Which view the user is on, for feedback-lib to tag a report with. */
object ScreenTracker {
    @Volatile var currentScreen: String = "חיפוש"
}
