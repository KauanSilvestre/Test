package app.sentinela.core

import android.content.Context
import android.os.Build
import app.sentinela.core.model.DeviceInfo
import java.util.TimeZone

object DeviceInfoProvider {
    fun collect(context: Context): DeviceInfo {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidSdk = Build.VERSION.SDK_INT,
            appVersion = version,
            timezone = TimeZone.getDefault().id,
        )
    }
}
