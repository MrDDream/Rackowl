package com.rackowl.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rackowl.data.Language
import java.util.Locale

/**
 * Langue de l'interface (anglais par défaut, français au choix dans Réglages).
 * C'est un état Compose : changer de langue recompose aussitôt les écrans qui l'ont lue.
 */
object I18n {
    var language by mutableStateOf(Language.EN)

    val locale: Locale get() = if (language == Language.FR) Locale.FRANCE else Locale.UK
}

/** Texte selon la langue : [en] (par défaut) ou [fr]. */
fun t(en: String, fr: String): String = if (I18n.language == Language.FR) fr else en
