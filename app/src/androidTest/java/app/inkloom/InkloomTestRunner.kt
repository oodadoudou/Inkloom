package app.inkloom

import android.app.Application
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import app.inkloom.update.AppUpdater

/** Gives scenarios that do not reset the Library the same starting notebooks as the reset helper. */
class InkloomTestRunner : AndroidJUnitRunner() {
    /**
     * The tests run as the real app (same package) and delete its notebooks and settings, so they
     * refuse to start on a physical tablet unless asked to (`-e allowRealDevice true`).
     */
    override fun onCreate(arguments: Bundle) {
        check(isAndroidEmulator || arguments.getString("allowRealDevice") == "true") {
            "Inkloom instrumented tests erase the app's notebooks; run them on an emulator"
        }
        super.onCreate(arguments)
    }

    override fun callApplicationOnCreate(app: Application) {
        seedTestNotebooks(app)
        AppUpdater.checksEnabled = false
        super.callApplicationOnCreate(app)
    }
}
