package app.inkloom.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import app.inkloom.settings.InterfaceScale
import kotlin.math.roundToInt

/**
 * Applies the interface size to an activity's context, so every window it opens (the main
 * content, dialogs, menus, pop-ups, tooltips and the canvas overlays) is drawn at the same size.
 * The painting engine works in document pixels and is unaffected.
 */
object InterfaceScaling {
    private const val PREFERENCES = "inkloom_settings"

    /** The size the current activity was created with. */
    @Volatile var applied: Float = 1f
        private set

    /** The size for [setting] on this screen, measured from the unscaled application resources. */
    fun resolve(context: Context, setting: Float): Float {
        val resources = context.applicationContext.resources
        return InterfaceScale.resolve(setting, resources.displayMetrics.density, resources.displayMetrics.xdpi,
            resources.displayMetrics.ydpi, resources.configuration.smallestScreenWidthDp)
    }

    fun stored(context: Context): Float = InterfaceScale.stored(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getFloat(InterfaceScale.PREFERENCE_KEY, InterfaceScale.AUTO))

    /** For `attachBaseContext`: the base context with its density and screen size in scaled units. */
    fun wrap(base: Context): Context {
        val resources = base.resources
        val configuration = resources.configuration
        val scale = InterfaceScale.resolve(stored(base), resources.displayMetrics.density, resources.displayMetrics.xdpi,
            resources.displayMetrics.ydpi, configuration.smallestScreenWidthDp)
        applied = scale
        if (scale == 1f) return base
        val scaled = Configuration(configuration)
        scaled.densityDpi = (configuration.densityDpi * scale).roundToInt().coerceAtLeast(1)
        val actual = scaled.densityDpi / configuration.densityDpi.toFloat()
        scaled.screenWidthDp = (configuration.screenWidthDp / actual).toInt()
        scaled.screenHeightDp = (configuration.screenHeightDp / actual).toInt()
        scaled.smallestScreenWidthDp = (configuration.smallestScreenWidthDp / actual).toInt()
        return base.createConfigurationContext(scaled)
    }

    /** A changed size takes effect by recreating the activity, as a rotation does; its state is restored. */
    fun recreateIfChanged(activity: Activity) {
        if (resolve(activity, stored(activity)) != applied) activity.recreate()
    }
}
