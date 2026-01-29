package com.mgomanager.app.data.model

import androidx.compose.ui.graphics.Color
import com.mgomanager.app.ui.theme.StatusGreen
import com.mgomanager.app.ui.theme.StatusLightOrange
import com.mgomanager.app.ui.theme.StatusOrange
import com.mgomanager.app.ui.theme.StatusRed

/**
 * Represents the suspicious account level
 * 0 = Clean, 3 = Warning Level 3, 7 = Warning Level 7, 99 = Permanent Ban
 */
enum class SusLevel(val value: Int, val displayName: String) {
    NONE(0, "Keine"),
    LEVEL_3(3, "Level 3"),
    LEVEL_7(7, "Level 7"),
    PERMANENT(99, "Permanent");

    companion object {
        fun fromValue(value: Int): SusLevel {
            return values().find { it.value == value } ?: NONE
        }

        fun getColor(susLevel: SusLevel): Color {
            return when (susLevel) {
                NONE -> StatusGreen
                LEVEL_3 -> StatusOrange
                LEVEL_7 -> StatusLightOrange
                PERMANENT -> StatusRed
            }
        }
    }

    fun getColor(): Color = getColor(this)
}
