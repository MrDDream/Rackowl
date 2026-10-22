package com.rackowl.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val loc: Locale get() = com.rackowl.ui.I18n.locale
private val fr: Boolean get() = com.rackowl.ui.I18n.language == Language.FR

fun fmtInt(n: Long?): String = if (n == null) "—" else NumberFormat.getIntegerInstance(loc).format(n)
fun fmtInt(n: Int?): String = fmtInt(n?.toLong())

fun fmtDec(d: Double, digits: Int = 1): String =
    NumberFormat.getNumberInstance(loc).apply { maximumFractionDigits = digits; minimumFractionDigits = 0 }.format(d)

/** 24 100 → « 24,1k ». */
fun fmtCompact(n: Long?): String = when {
    n == null -> "—"
    n >= 1_000_000 -> fmtDec(n / 1_000_000.0) + "M"
    n >= 10_000 -> fmtDec(n / 1_000.0) + "k"
    else -> fmtInt(n)
}

fun fmtPercent(p: Double?): String = if (p == null) "—" else fmtDec(p, if (p < 10) 1 else 0) + if (fr) " %" else "%"

/** Débit en octets/s → valeur en Mo/s ou Ko/s. Renvoie (valeur, unité). */
fun fmtSpeed(bytesPerSec: Long?): Pair<String, String> {
    val b = bytesPerSec ?: 0
    return if (b >= 1_000_000) fmtDec(b / 1_000_000.0) to (if (fr) "Mo/s" else "MB/s") else fmtDec(b / 1_000.0, 0) to (if (fr) "Ko/s" else "kB/s")
}

/** [binary] : base 1024, pour la RAM (8 Gio alloués s'affichent « 8 Go » et non « 8,6 Go »). */
fun fmtBytes(bytes: Long?, binary: Boolean = false): String {
    val b = bytes ?: return "—"
    val units = if (fr) listOf("o", "Ko", "Mo", "Go", "To", "Po") else listOf("B", "kB", "MB", "GB", "TB", "PB")
    val step = if (binary) 1024.0 else 1000.0
    var v = b.toDouble(); var i = 0
    while (v >= step && i < units.lastIndex) { v /= step; i++ }
    return fmtDec(v) + " " + units[i]
}

fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} min"
        s < 86_400 -> "${s / 3600} h" + (if ((s % 3600) / 60 > 0) " ${(s % 3600) / 60} min" else "")
        else -> "${s / 86_400} " + if (fr) "j" else "d"
    }
}

fun fmtAgo(time: Long, now: Long = System.currentTimeMillis()): String {
    if (time <= 0) return if (fr) "jamais" else "never"
    val d = now - time
    return when {
        d < 5_000 -> if (fr) "à l’instant" else "just now"
        fr -> "il y a " + fmtDuration(d)
        else -> fmtDuration(d) + " ago"
    }
}

fun fmtClock(time: Long): String = SimpleDateFormat("HH:mm", loc).format(Date(time))
fun fmtClockSec(time: Long): String = SimpleDateFormat("HH:mm:ss", loc).format(Date(time))

/** « Aujourd’hui », « Hier » ou la date. */
fun fmtDay(time: Long, now: Long = System.currentTimeMillis()): String {
    val a = Calendar.getInstance().apply { timeInMillis = time }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    val days = ((startOfDay(b) - startOfDay(a)) / 86_400_000L).toInt()
    return when (days) {
        0 -> if (fr) "Aujourd’hui" else "Today"
        1 -> if (fr) "Hier" else "Yesterday"
        else -> SimpleDateFormat(if (fr) "EEEE d MMMM" else "EEEE, MMMM d", loc).format(Date(time)).replaceFirstChar { it.uppercase() }
    }
}

private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun fmtInterval(sec: Int): String = when {
    sec % 3600 == 0 && sec >= 3600 -> "${sec / 3600} h"
    sec % 60 == 0 && sec >= 60 -> "${sec / 60} min"
    else -> "$sec s"
}

/** URL d'icône : slug selfh.st, URL complète, ou rien. */
fun iconUrl(icon: String): String? = when {
    icon.isBlank() -> null
    icon.startsWith("http://") || icon.startsWith("https://") -> icon
    else -> {
        val slug = icon.removePrefix("sh-").removeSuffix(".png").removeSuffix(".svg").removeSuffix(".webp")
        "https://cdn.jsdelivr.net/gh/selfhst/icons/png/$slug.png"
    }
}

/** « Pi-hole » → « pi-hole », « Home Assistant » → « home-assistant ». */
fun slugify(name: String): String =
    name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
