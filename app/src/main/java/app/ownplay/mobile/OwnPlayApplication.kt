package app.ownplay.mobile

import android.app.Application
import app.ownplay.mobile.core.OwnPlayServices

class OwnPlayApplication : Application() {
    val services: OwnPlayServices by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OwnPlayServices.create(this)
    }
}
