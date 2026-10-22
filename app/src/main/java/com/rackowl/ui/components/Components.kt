package com.rackowl.ui.components

import androidx.compose.animation.core.animateDpAsState
import com.rackowl.ui.t
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.rackowl.data.Level
import com.rackowl.data.iconUrl
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.Plex

@Composable
fun HCard(
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    bg: Color = Home.colors.surface,
    border: Color = Home.colors.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(radius)),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(horizontal = 4.dp),
        style = TextStyle(fontFamily = Plex, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp),
        color = Home.colors.muted,
    )
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Home.colors.border))

@Composable
fun IconBtn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Home.colors.textSecondary,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}

/** Point d'état avec halo, comme sur les tuiles du design. */
@Composable
fun StatusDot(up: Boolean?, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    val c = Home.colors
    val (dot, halo, label) = when (up) {
        true -> Triple(c.up, c.upBg, t("Online", "En ligne"))
        false -> Triple(c.down, c.downBg, t("Offline", "Hors ligne"))
        null -> Triple(c.muted, c.selected, t("Not monitored", "Non surveillé"))
    }
    Box(
        modifier
            .size(size + 6.dp)
            .clip(CircleShape)
            .background(halo)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(dot))
    }
}

/** Icône selfh.st chargée depuis le CDN, avec repli sur l'initiale. */
@Composable
fun ServiceIcon(icon: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val url = iconUrl(icon)
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (!loaded) {
            Box(
                Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Home.colors.borderStrong),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold, color = Home.colors.textSecondary,
                )
            }
        }
        if (url != null) AsyncImage(
            model = url, contentDescription = null, modifier = Modifier.size(size), contentScale = ContentScale.Fit,
            onState = { loaded = it is AsyncImagePainter.State.Success },
        )
    }
}

@Composable
fun levelColor(level: Level): Color = when (level) {
    Level.NORMAL -> Home.colors.text
    Level.WARN -> Home.colors.warn
    Level.DOWN -> Home.colors.down
}

/** Contrôle segmenté (fréquences, colonnes, thème…). */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Home.colors.field,
    height: Dp = 36.dp,
    textStyle: TextStyle = TextStyle(fontFamily = Plex, fontSize = 13.sp),
) {
    val c = Home.colors
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, c.borderStrong, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) c.segmentOn else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = textStyle, color = if (on) c.segmentOnText else c.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun HSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    val c = Home.colors
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "switch")
    Box(
        Modifier
            .size(44.dp, 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) c.accent else c.trackOff)
            .clickable(role = Role.Switch, onClickLabel = label) { onChange(!checked) }
            .semantics { contentDescription = label }
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = offset).size(20.dp).clip(CircleShape).background(Color(0xFFF4F5F7)))
    }
}

/** Ligne réglage : libellé + aide à gauche, contrôle à droite. */
@Composable
fun SettingRow(
    title: String,
    hint: String? = null,
    hintColor: Color = Home.colors.muted,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, color = Home.colors.text)
            if (!hint.isNullOrBlank()) Text(hint, fontSize = 12.sp, color = hintColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Champ de saisie au style du design (fond sombre, bord fin, 44 dp). */
@Composable
fun HField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    bg: Color = Home.colors.surface,
    borderColor: Color = Home.colors.border,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    height: Dp = 44.dp,
) {
    val c = Home.colors
    val style = if (mono) com.rackowl.ui.theme.mono(13, c.text, FontWeight.Medium)
    else TextStyle(fontFamily = Plex, fontSize = 15.sp, color = c.text)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = height)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(start = 12.dp, end = if (trailing != null) 6.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.placeholder), maxLines = 1)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun FieldLabel(text: String) = Text(text, fontSize = 12.sp, color = Home.colors.muted)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Home.colors
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.accent)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = c.onAccent); Box(Modifier.width(8.dp)) }
        Text(text, color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
    radius: Dp = 14.dp,
    bg: Color = Home.colors.surface,
    border: Color = Home.colors.border,
    fontSize: Int = 15,
    enabled: Boolean = true,
) {
    val c = Home.colors
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(radius))
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = c.textSecondary); Box(Modifier.width(6.dp)) }
        Text(text, color = c.text, fontSize = fontSize.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Puce de filtre / catégorie (bord accent quand sélectionnée). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, trailing: String? = null, dashed: Boolean = false) {
    val c = Home.colors
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.accentBg else if (dashed) Color.Transparent else c.surface)
            .border(BorderStroke(1.dp, if (selected) c.accent else if (dashed) c.trackOff else c.border), RoundedCornerShape(50))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, fontSize = 13.sp, color = if (selected) c.text else if (dashed) c.muted else c.textSecondary, maxLines = 1)
        if (trailing != null) Text(trailing, style = com.rackowl.ui.theme.mono(11, c.muted))
    }
}

@Composable
fun Pill(text: String, fg: Color, bg: Color, dot: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        Text(text, fontSize = 12.sp, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(Home.colors.border)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
