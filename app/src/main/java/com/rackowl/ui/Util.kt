package com.rackowl.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.rackowl.RackowlApp
import com.rackowl.data.Store
import kotlinx.coroutines.launch

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        toast(t("Can’t open $url", "Impossible d’ouvrir $url"))
    } catch (e: Exception) {
        toast(t("Invalid URL: $url", "URL invalide : $url"))
    }
}

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** Lance une modification du stockage sans bloquer l'interface. */
fun RackowlApp.edit(block: suspend Store.() -> Unit) {
    scope.launch { store.block() }
}

/** Échange deux éléments d'une liste (flèches de réorganisation). */
fun <T> List<T>.swap(i: Int, j: Int): List<T> {
    if (i !in indices || j !in indices) return this
    return toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
}
