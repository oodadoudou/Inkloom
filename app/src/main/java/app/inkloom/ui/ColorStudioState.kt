package app.inkloom.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import app.inkloom.color.MixingBoard

/** One color workspace across popup hosts and configuration changes; never retains an Activity. */
internal class ColorStudioState(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE)
    val mixing by lazy { ColorMixingSession(application) }
    var tab by mutableIntStateOf(preferences.getInt("studioTab", 0).coerceIn(0, 3))
        private set
    var mixingTool by mutableStateOf(MixingBoard.Tool.entries.getOrElse(preferences.getInt("mixTool", 0)) { MixingBoard.Tool.PAINT })
        private set
    var mixingRadius by mutableFloatStateOf(preferences.getFloat("mixRadius", 12f).coerceIn(2f, 48f))
        private set

    fun selectTab(value: Int) { tab = value.coerceIn(0, 3); preferences.edit().putInt("studioTab", tab).apply() }
    fun selectTool(value: MixingBoard.Tool) { mixingTool = value; preferences.edit().putInt("mixTool", value.ordinal).apply() }
    fun setRadius(value: Float) { mixingRadius = value.coerceIn(2f, 48f); preferences.edit().putFloat("mixRadius", mixingRadius).apply() }
}

internal val LocalColorStudioState = staticCompositionLocalOf<ColorStudioState> {
    error("Color palettes require the owning color workspace")
}
