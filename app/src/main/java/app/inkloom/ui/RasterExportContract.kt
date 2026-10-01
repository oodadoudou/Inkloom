package app.inkloom.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

internal data class RasterExportDestination(val name: String, val mime: String)
internal class RasterExportContract : ActivityResultContract<RasterExportDestination, Uri?>() {
    override fun createIntent(context: Context, input: RasterExportDestination) = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE).setType(input.mime).putExtra(Intent.EXTRA_TITLE, input.name)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if(resultCode == Activity.RESULT_OK) intent?.data else null
}
