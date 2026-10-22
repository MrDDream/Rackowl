package com.rackowl.data

import java.util.Calendar

/** « HH:mm » → minutes depuis minuit, ou null si invalide. */
fun parseHm(s: String): Int? {
    val m = Regex("^\\s*(\\d{1,2}):(\\d{2})\\s*$").find(s) ?: return null
    val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) h * 60 + min else null
}

/** Vrai si [time] tombe dans la plage quotidienne [start]–[end], y compris à cheval sur minuit. */
fun inDailyWindow(time: Long, start: String, end: String): Boolean {
    val a = parseHm(start) ?: return false
    val b = parseHm(end) ?: return false
    val cal = Calendar.getInstance().apply { timeInMillis = time }
    val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return if (a <= b) now in a until b else now >= a || now < b
}

/** « 02:00-04:00 » → paire (début, fin). */
fun splitWindow(w: String): Pair<String, String>? =
    w.split('-').takeIf { it.size == 2 && parseHm(it[0]) != null && parseHm(it[1]) != null }?.let { it[0].trim() to it[1].trim() }

fun Service.inMaintenance(now: Long = System.currentTimeMillis()): Boolean =
    maintenanceUntil > now || splitWindow(maintenanceWindow)?.let { (a, b) -> inDailyWindow(now, a, b) } == true

fun Settings.inQuietHours(now: Long = System.currentTimeMillis()): Boolean = quietHours && inDailyWindow(now, quietStart, quietEnd)
