package app.inkloom.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import app.inkloom.BuildConfig
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.Ink
import app.inkloom.ui.theme.MutedInk

/** Third-party code and data shipped in the app, with the licence each is used under. */
internal data class OpenSourceNotice(val name: String, val licence: String, val url: String)

internal val OpenSourceNotices = listOf(
    OpenSourceNotice("AndroidX and Jetpack Compose", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    OpenSourceNotice("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0", "https://kotlinlang.org"),
    OpenSourceNotice("ML Kit Text Recognition v2 (Latin, Chinese)", "ML Kit Terms of Service", "https://developers.google.com/ml-kit/terms"),
    OpenSourceNotice("ONNX Runtime", "MIT License", "https://github.com/microsoft/onnxruntime"),
    OpenSourceNotice("Real-ESRGAN realesr-animevideov3 model", "BSD 3-Clause License", "https://github.com/xinntao/Real-ESRGAN"),
    OpenSourceNotice("Little CMS 2.19", "MIT License", "https://github.com/mm2/Little-CMS"),
)

/** Settings > About: version, what stays on the device, and open-source notices. */
internal fun LazyListScope.aboutItems(language: UiLanguage) {
    item { Text("Inkloom ${BuildConfig.VERSION_NAME}", color = Ink, fontSize = 15.sp) }
    item { Text(language.text("PRIVACY", "隐私"), color = MutedInk, fontSize = 12.sp) }
    item {
        Text(language.text(
            "Notebooks, brushes and settings stay on this device; text recognition and super resolution run on the device. " +
                "Inkloom goes online only to check GitHub for a new release. Nothing leaves the device unless you export, share or back up a file.",
            "笔记、笔刷与设置都保存在本机；文字识别与超分辨率也在本机运行。Inkloom 只在检查 GitHub 新版本时联网。除非你导出、分享或备份文件，任何内容都不会离开设备。"),
            fontSize = 13.sp)
    }
    item { Text(language.text("OPEN-SOURCE NOTICES", "开源许可"), color = MutedInk, fontSize = 12.sp) }
    OpenSourceNotices.forEach { notice ->
        item {
            Column {
                Text(notice.name, color = Ink, fontSize = 13.sp)
                Text("${notice.licence} · ${notice.url}", color = MutedInk, fontSize = 11.sp)
            }
        }
    }
}
