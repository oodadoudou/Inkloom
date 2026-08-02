package app.inkloom.settings

enum class UiLanguage {
    ENGLISH,
    SIMPLIFIED_CHINESE;

    companion object {
        fun fromLanguageTag(languageTag: String?): UiLanguage =
            if (languageTag.orEmpty().substringBefore('-').substringBefore('_').equals("zh", ignoreCase = true)) {
                SIMPLIFIED_CHINESE
            } else {
                ENGLISH
            }
    }
}

fun UiLanguage.text(english: String, simplifiedChinese: String): String =
    if (this == UiLanguage.SIMPLIFIED_CHINESE) simplifiedChinese else english
