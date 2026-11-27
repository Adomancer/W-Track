package wtrack.app.core.i18n

import android.content.Context
import java.util.Locale

object AppLocaleHelper {
    fun activeLocale(context: Context? = null): Locale {
        if (context != null) {
            val locales = context.resources.configuration.locales
            if (!locales.isEmpty) {
                return locales[0]
            }
        }
        return Locale.getDefault()
    }

    fun isIndonesian(locale: Locale = activeLocale()): Boolean {
        val lang = locale.language.lowercase(Locale.ROOT)
        return lang == "id" || lang == "in"
    }
}
