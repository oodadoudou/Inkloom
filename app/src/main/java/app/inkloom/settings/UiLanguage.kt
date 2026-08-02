package app.inkloom.settings

enum class UiLanguage { ENGLISH, SIMPLIFIED_CHINESE }

fun UiLanguage.text(english: String, simplifiedChinese: String): String =
    if (this == UiLanguage.SIMPLIFIED_CHINESE) simplifiedChinese else english
