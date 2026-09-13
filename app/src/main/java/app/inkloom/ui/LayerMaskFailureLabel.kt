package app.inkloom.ui

import app.inkloom.settings.UiLanguage

internal fun layerMaskFailureLabel(reason: String, language: UiLanguage): String {
    val zh = language == UiLanguage.SIMPLIFIED_CHINESE
    return when (reason) {
        "BUSY" -> if (zh) "请先抬笔并完成当前操作" else "Lift the pen and finish the current operation"
        "LOCKED" -> if (zh) "请先解锁图层或所属组" else "Unlock the layer or its parent folder"
        "MISSING_SELECTION" -> if (zh) "请先建立选区" else "Create a selection first"
        "EXISTING_MASK" -> if (zh) "此图层已有蒙版" else "This layer already has a mask"
        "MISSING_MASK", "MASK_TARGET_UNAVAILABLE" -> if (zh) "请确认蒙版存在，并显示、解锁所属图层" else "Make sure the mask exists and its layer is visible and unlocked"
        "GEOMETRY" -> if (zh) "选区与蒙版尺寸不匹配" else "Selection and mask dimensions do not match"
        "WRONG_DISPATCHER" -> if (zh) "此蒙版操作尚不支持" else "This mask operation is not supported yet"
        else -> if (zh) "蒙版文件或编辑目标不可用，请重试" else "The mask file or editing target is unavailable; try again"
    }
}
