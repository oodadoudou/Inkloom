package app.inkloom

import android.app.Application
import androidx.test.runner.AndroidJUnitRunner

/** Gives scenarios that do not reset the Library the same starting notebooks as the reset helper. */
class InkloomTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        seedTestNotebooks(app)
        super.callApplicationOnCreate(app)
    }
}
