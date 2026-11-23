package app.inkloom.importer

import app.inkloom.brushpresetv5.BrushOutput
import app.inkloom.brushpresetv5.BrushPresetV5Codec
import app.inkloom.model.BrushToolBinding
import app.inkloom.model.BrushToolRole
import app.inkloom.model.SystemBrushCatalog

/** Import/storage-boundary migration. The runtime never examines old flags or display labels. */
object BrushToolBindingMigration {
    fun fromLegacy(explicitEraser: Boolean, presetJson: String?, factoryId: String?): BrushToolBinding {
        val preset = presetJson?.let(BrushPresetV5Codec::decodeOrNull)
        if (explicitEraser || preset?.output == BrushOutput.ERASE) {
            return BrushToolBinding(BrushToolRole.ERASE)
        }
        val factory = factoryId?.let(SystemBrushCatalog::byId)
            ?: factoryId?.let { preset?.identity?.id?.let(SystemBrushCatalog::byId) }
        return factory?.toolBinding ?: BrushToolBinding()
    }
}
