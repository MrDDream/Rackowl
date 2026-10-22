package com.rackowl.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rackowl.BuildConfig
import com.rackowl.data.Service
import com.rackowl.integrations.Integrations
import com.rackowl.monitor.Monitor
import com.rackowl.net.Exchange
import com.rackowl.net.Http
import com.rackowl.ui.LocalApp
import com.rackowl.ui.components.HCard
import com.rackowl.ui.components.HIcons
import com.rackowl.ui.components.IconBtn
import com.rackowl.ui.components.SecondaryButton
import com.rackowl.ui.t
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.mono
import com.rackowl.ui.toast

/**
 * Diagnostic d'un service : rejoue la vérification et le widget en enregistrant chaque requête
 * (secrets masqués), pour comprendre pourquoi un widget ne remonte rien.
 */
@Composable
fun DiagnosticDialog(s: Service, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val c = Home.colors
    val exchanges = remember { mutableStateListOf<Exchange>() }
    var outcome by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<Int?>(null) }

    // La clé ou le mot de passe peuvent aussi apparaître dans le chemin d'une URL : on les masque partout.
    fun scrub(text: String): String = listOf(s.apiKey, s.password).filter { it.length >= 4 }.fold(text) { acc, secret -> acc.replace(secret, "•••") }

    LaunchedEffect(s.id) {
        val http = Http(app.store.config.value.settings.allowInsecureTls).apply { recorder = { e -> exchanges += e } }
        val lines = mutableListOf<String>()
        if (s.url.startsWith("tcp://")) {
            lines += runCatching { Monitor.tcpCheck(s.url) }.fold({ t("TCP port open in $it ms", "Port TCP ouvert en $it ms") }, { "TCP : " + Monitor.describe(it) })
        } else {
            lines += runCatching { http.get(s.url) }.fold({ "Ping : HTTP ${it.code} · ${it.latencyMs} ms" }, { "Ping : " + Monitor.describe(it) })
        }
        Integrations.byId(s.widget)?.let { i ->
            lines += runCatching { i.fetch(http, s) }.fold(
                { d -> "Widget ${i.name} : " + d.stats.joinToString(" · ") { "${it.value} ${it.label}" }.ifBlank { t("no stats", "aucune statistique") } },
                { "Widget ${i.name} : " + Monitor.describe(it) },
            )
        }
        outcome = lines.joinToString("\n")
    }

    fun report(): String = buildString {
        appendLine("Rackowl ${BuildConfig.VERSION_NAME} · ${s.name} · widget=${s.widget ?: "-"}")
        outcome?.let { appendLine(it) }
        exchanges.forEachIndexed { i, e ->
            appendLine()
            appendLine("#${i + 1} ${e.method} ${e.url}")
            appendLine("→ ${e.code ?: e.error} · ${e.ms} ms")
            if (e.requestHeaders.isNotEmpty()) appendLine("Request headers: ${e.requestHeaders}")
            if (e.responseHeaders.isNotEmpty()) appendLine("Response headers: ${e.responseHeaders}")
            if (e.body.isNotBlank()) appendLine(e.body)
        }
    }.let(::scrub)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(HIcons.Close, t("Close", "Fermer"), onDismiss)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(t("Diagnostic", "Diagnostic"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.name, fontSize = 13.sp, color = c.muted)
                }
            }
            Text(
                outcome?.let(::scrub) ?: t("Running checks…", "Vérifications en cours…"),
                style = mono(12, c.text), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                t("Keys, tokens and passwords are masked. Tap a request to see its response.", "Clés, jetons et mots de passe sont masqués. Touche une requête pour voir sa réponse."),
                fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(exchanges) { i, e ->
                    val ok = e.code != null && e.code in 200..299
                    HCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { expanded = if (expanded == i) null else i }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.method, style = mono(12, c.textSecondary))
                                Text(
                                    "  " + (e.code?.toString() ?: "ERR") + " · ${e.ms} ms",
                                    style = mono(12, if (ok) c.up else c.down), modifier = Modifier.weight(1f),
                                )
                            }
                            Text(scrub(e.url), style = mono(11, c.text), maxLines = if (expanded == i) 6 else 2, overflow = TextOverflow.Ellipsis)
                            e.error?.let { Text(scrub(it), style = mono(11, c.down)) }
                            if (expanded == i) {
                                if (e.requestHeaders.isNotEmpty()) Text(scrub("→ " + e.requestHeaders.entries.joinToString("\n→ ") { "${it.key}: ${it.value}" }), style = mono(10, c.muted))
                                Text(scrub(e.body.ifBlank { t("(empty response)", "(réponse vide)") }), style = mono(10, c.textSecondary))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(t("Copy", "Copier"), {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Rackowl diagnostic", report()))
                    ctx.toast(t("Copied", "Copié"))
                }, Modifier.weight(1f), height = 44.dp, radius = 12.dp, fontSize = 14, enabled = outcome != null)
                SecondaryButton(t("Share", "Partager"), {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report())
                    ctx.startActivity(Intent.createChooser(send, null))
                }, Modifier.weight(1f), height = 44.dp, radius = 12.dp, fontSize = 14, enabled = outcome != null)
            }
        }
    }
}
