package dev.uint.qrserv.util

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import dev.uint.qrserv.R
import java.util.Locale

@RequiresApi(33)
fun Context.appLanguageName(): String? {
    val locales = getSystemService(LocaleManager::class.java).applicationLocales
    return if (locales.isEmpty) null else languageName(locales[0])
}

internal fun languageName(locale: Locale): String =
    locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }

@RequiresApi(33)
fun openAppLanguageSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri()))
    }.onFailure { Toast.makeText(context, R.string.info_no_settings, Toast.LENGTH_SHORT).show() }
}
