package app.inkloom.settings

/** Original Inkloom utility colors, not redistributed third-party palette files. */
enum class StarterColorSet(val englishName: String, val chineseName: String, private val colors: List<UtilityColor>) {
    LINE_ART("Line art", "线稿", listOf(
        UtilityColor(0x000000, "Black", "纯黑"), UtilityColor(0x202020, "Soft black", "柔黑"),
        UtilityColor(0x404040, "Graphite", "石墨灰"), UtilityColor(0x606060, "Gray", "中灰"),
        UtilityColor(0x322522, "Dark brown", "深棕"), UtilityColor(0x51352F, "Sepia", "褐色"),
        UtilityColor(0x704B40, "Warm brown", "暖棕"), UtilityColor(0x8E6654, "Light brown", "浅棕"),
        UtilityColor(0x202D45, "Navy", "深蓝"), UtilityColor(0x3C4B68, "Blue gray", "蓝灰"),
        UtilityColor(0x38283F, "Deep violet", "深紫"), UtilityColor(0x5C405B, "Muted violet", "灰紫"),
    )),
    SKETCH("Sketch", "草稿", listOf(
        UtilityColor(0xC6E1F4, "Pale blue", "淡蓝"), UtilityColor(0x92C3E6, "Light blue", "浅蓝"),
        UtilityColor(0x5F9CCB, "Sketch blue", "草稿蓝"), UtilityColor(0x426E9F, "Dark blue", "深蓝"),
        UtilityColor(0xF4D0D0, "Pale red", "淡红"), UtilityColor(0xE8A2AA, "Light red", "浅红"),
        UtilityColor(0xCF7182, "Correction red", "修正红"), UtilityColor(0xA84860, "Dark red", "深红"),
        UtilityColor(0xDDD5EE, "Pale violet", "淡紫"), UtilityColor(0xB3A2CE, "Sketch violet", "草稿紫"),
        UtilityColor(0xC8C8C8, "Light gray", "浅灰"), UtilityColor(0x909090, "Sketch gray", "草稿灰"),
    )),
    GRAYSCALE("Grayscale", "灰阶", (0..10).map { index ->
        val channel = (255 * index + 5) / 10
        UtilityColor((channel shl 16) or (channel shl 8) or channel, "${index * 10}% brightness", "${index * 10}% 明度")
    });

    fun displayName(language: UiLanguage): String = language.text(englishName, chineseName)
    fun swatches(language: UiLanguage): List<Pair<Int, String>> = colors.map {
        (it.rgb or 0xff000000.toInt()) to language.text(it.englishName, it.chineseName)
    }

    /** Explicit add creates a normal editable copy. No automatic reseeding or replacement. */
    fun addTo(library: ColorSetLibrary, language: UiLanguage): ColorSetLibrary {
        val added = library.createSet(displayName(language))
        val id = requireNotNull(added.activeSetId)
        return swatches(language).foldIndexed(added) { index, current, (color, name) ->
            current.insertSwatch(id, color, name, beforeIndex = index)
        }.validated()
    }
}

data class UtilityColor(val rgb: Int, val englishName: String, val chineseName: String)
