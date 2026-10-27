package com.aboni.n2kRouter.ui

import androidx.compose.ui.graphics.Color
import com.aboni.marine.ui.theme.Themed

/** The wind app's domain colours. Night values are red only, told apart by brightness. */
object WindColors {
    /** The raw vane reading. */
    val needle = Themed(Color(0xFF0000FF), Color(0xFF90CAF9), Color(0xFFC41A10))
    /** The smoothed reading. */
    val smoothNeedle = Themed(Color(0xFF0000AA), Color(0xFF5C6BC0), Color(0xFF7A1208))
    /** What goes on the NMEA 2000 bus (smoothed plus offset). */
    val outputNeedle = Themed(Color(0xFF2288FF), Color(0xFFFFD54F), Color(0xFFFF3020))
    val starboard = Themed(Color(0xFF1E8C3C), Color(0xFF4CAF50), Color(0xFF3A0A06))
    val port = Themed(Color(0xFFD32F2F), Color(0xFFEF5350), Color(0xFF7A1208))
    /** Angles covered by the calibration so far. */
    val calibrating = Themed(Color(0xFFE65100), Color(0xFFFF6600), Color(0xFF7A1208))
    /** Covered angles once the calibration score is reached. */
    val calibrated = Themed(Color(0xFF007700), Color(0xFF4CAF50), Color(0xFFFF3020))
}
