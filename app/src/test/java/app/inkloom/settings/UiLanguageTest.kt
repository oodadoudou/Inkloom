package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class UiLanguageTest {
    @Test
    fun chineseSystemLocalesChooseSimplifiedChineseInterface() {
        assertEquals(UiLanguage.SIMPLIFIED_CHINESE, UiLanguage.fromLanguageTag("zh-CN"))
        assertEquals(UiLanguage.SIMPLIFIED_CHINESE, UiLanguage.fromLanguageTag("zh-Hans-SE"))
        assertEquals(UiLanguage.SIMPLIFIED_CHINESE, UiLanguage.fromLanguageTag("zh_TW"))
    }

    @Test
    fun otherAndMissingLocalesChooseEnglishInterface() {
        assertEquals(UiLanguage.ENGLISH, UiLanguage.fromLanguageTag("en-SE"))
        assertEquals(UiLanguage.ENGLISH, UiLanguage.fromLanguageTag("sv-SE"))
        assertEquals(UiLanguage.ENGLISH, UiLanguage.fromLanguageTag(null))
    }
}
