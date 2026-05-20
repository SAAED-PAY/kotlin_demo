package sa.saaedpay.demo

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import sa.saaedpay.softpos.SaaedPay
import sa.saaedpay.softpos.api.config.SaaedPayConfig
import java.util.Locale

class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        SaaedPay.init(
            context = this,
            config = SaaedPayConfig(
                apiBaseUrl = "https://hub.saaedpay.sa/api/",
                locale = Locale.ENGLISH,
                enableLogging = BuildConfig.DEBUG,
                connectTimeoutSeconds = 30L,
                readTimeoutSeconds = 30L
            )
        )
    }
}
