package app.inkloom.settings

import android.content.Context

/** The mixing palette owns its color bar; changing the active Color Set never replaces it. */
class MixingColorBarStore(context: Context) {
    private val preferences = context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE)
    fun load(): ColorSetLibrary = synchronized(lock) {
        preferences.getString(KEY, null)?.let(ColorSetLibraryCodec::decode) ?: defaults().also(::save)
    }
    fun update(change: (ColorSetLibrary) -> ColorSetLibrary): ColorSetLibrary = synchronized(lock) {
        change(load()).validated().also(::save)
    }
    fun reset(): ColorSetLibrary = synchronized(lock) { defaults().also(::save) }
    private fun save(library: ColorSetLibrary) { preferences.edit().putString(KEY, ColorSetLibraryCodec.encode(library)).apply() }
    companion object {
        const val SET_ID = "mixing-colors"
        private const val KEY = "mixingColorBarV1"
        private val lock = Any()
        fun defaults(): ColorSetLibrary {
            val colors = listOf(0xffe53935.toInt(), 0xffff9800.toInt(), 0xffffeb3b.toInt(), 0xff43a047.toInt(), 0xff00838f.toInt(), 0xff1e88e5.toInt(), 0xff8e24aa.toInt(), 0xff111111.toInt(), 0xffffffff.toInt())
            return ColorSetLibrary(listOf(NamedColorSet(SET_ID, "Mixing colors", colors.mapIndexed { i, color -> ColorSetSwatch("mix-default-$i", color) })), SET_ID)
        }
    }
}
