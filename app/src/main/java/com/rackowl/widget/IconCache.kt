package com.rackowl.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.rackowl.RackowlApp
import com.rackowl.data.iconUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Logos des services pour les widgets d'écran d'accueil : Glance ne charge pas d'image réseau,
 * on télécharge donc chaque icône une fois, réduite à 96 px, dans le cache de l'app.
 */
object IconCache {
    private const val SIZE = 96

    private fun file(context: Context, icon: String) =
        File(File(context.cacheDir, "widget-icons").apply { mkdirs() }, icon.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".png")

    suspend fun bitmap(context: Context, icon: String): Bitmap? = withContext(Dispatchers.IO) {
        if (icon.isBlank()) return@withContext null
        val f = file(context, icon)
        if (f.exists()) return@withContext BitmapFactory.decodeFile(f.path)
        val url = iconUrl(icon) ?: return@withContext null
        runCatching {
            val app = context.applicationContext as RackowlApp
            val req = okhttp3.Request.Builder().url(url).build()
            val bytes = app.http().client.newCall(req).execute().use { if (it.isSuccessful) it.body?.bytes() else null } ?: return@runCatching null
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            val scaled = Bitmap.createScaledBitmap(raw, SIZE, SIZE, true)
            f.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            scaled
        }.getOrNull()
    }

    suspend fun bitmaps(context: Context, icons: Collection<String>): Map<String, Bitmap> =
        icons.distinct().mapNotNull { i -> bitmap(context, i)?.let { i to it } }.toMap()
}
