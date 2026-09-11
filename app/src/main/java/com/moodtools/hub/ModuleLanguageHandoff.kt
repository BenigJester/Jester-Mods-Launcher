package com.moodtools.hub

import android.content.Context
import android.content.Intent

internal const val MODULE_LANGUAGE_EXTRA = "com.moodtools.menu.LANGUAGE"

internal fun selectedMenuLanguage(context: Context): Int =
    normalizeModuleLanguage(
        context.applicationContext.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)
            .getInt("menu_language", 0)
    )

internal fun normalizeModuleLanguage(value: Int): Int = value.takeIf { it in 0..9 } ?: 0

internal fun Intent.withSelectedMenuLanguage(context: Context): Intent =
    putExtra(MODULE_LANGUAGE_EXTRA, selectedMenuLanguage(context))
