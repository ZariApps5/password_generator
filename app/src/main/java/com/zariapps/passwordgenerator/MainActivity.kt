package com.zariapps.passwordgenerator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Theme colors ─────────────────────────────────────────

data class AppColors(
    val bgColor: Color,
    val surfaceColor: Color,
    val raisedColor: Color,
    val borderColor: Color,
    val accentColor: Color,
    val accentDark: Color,
    val textColor: Color,
    val mutedColor: Color,
)

val darkColors = AppColors(
    bgColor      = Color(0xFF0D0D1A),
    surfaceColor = Color(0xFF141426),
    raisedColor  = Color(0xFF1E1E32),
    borderColor  = Color(0xFF2A2A45),
    accentColor  = Color(0xFF818CF8),
    accentDark   = Color(0xFF4F46E5),
    textColor    = Color(0xFFE2E0F9),
    mutedColor   = Color(0xFF7070A0),
)

val lightColors = AppColors(
    bgColor      = Color(0xFFF5F4FF),
    surfaceColor = Color(0xFFFFFFFF),
    raisedColor  = Color(0xFFEEECFF),
    borderColor  = Color(0xFFCCCAE8),
    accentColor  = Color(0xFF4F46E5),
    accentDark   = Color(0xFF3730A3),
    textColor    = Color(0xFF1A1640),
    mutedColor   = Color(0xFF6B6A99),
)

val LocalColors = compositionLocalOf { darkColors }

// ── Strength colors (theme-independent) ──────────────────

val WeakColor   = Color(0xFFEF4444)
val FairColor   = Color(0xFFFB923C)
val GoodColor   = Color(0xFFFBBF24)
val StrongColor = Color(0xFF34D399)

// ── Strength ─────────────────────────────────────────────

enum class Strength(val label: String, val color: Color, val filled: Int) {
    WEAK("Weak", WeakColor, 1),
    FAIR("Fair", FairColor, 2),
    GOOD("Good", GoodColor, 3),
    STRONG("Strong", StrongColor, 4),
}

fun calcStrength(length: Int, types: Int): Strength = when {
    length < 8  || types <= 1 -> Strength.WEAK
    length < 12 || types == 2 -> Strength.FAIR
    length < 16 || types == 3 -> Strength.GOOD
    else                       -> Strength.STRONG
}

// ── Generator ────────────────────────────────────────────

private const val UPPER   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val LOWER   = "abcdefghijklmnopqrstuvwxyz"
private const val DIGITS  = "0123456789"
private const val SYMBOLS = "!@#\$%^&*()_+-=[]{}|;:,.<>?"

fun generate(length: Int, upper: Boolean, lower: Boolean, numbers: Boolean, symbols: Boolean): String {
    val pool = buildString {
        if (upper)   append(UPPER)
        if (lower)   append(LOWER)
        if (numbers) append(DIGITS)
        if (symbols) append(SYMBOLS)
    }
    if (pool.isEmpty()) return ""

    val seed = mutableListOf<Char>().apply {
        if (upper)   add(UPPER.random())
        if (lower)   add(LOWER.random())
        if (numbers) add(DIGITS.random())
        if (symbols) add(SYMBOLS.random())
    }
    val rest = List(length - seed.size) { pool.random() }
    return (seed + rest).shuffled().joinToString("")
}

// ── Data ─────────────────────────────────────────────────

data class HistoryEntry(val password: String, val time: String)

// ── Activity ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedDark = prefs.getBoolean("is_dark", false)
        setContent { PasswordApp(initialDark = savedDark) }
    }
}

@Composable
fun PasswordApp(initialDark: Boolean) {
    val context = LocalContext.current
    var isDark by remember { mutableStateOf(initialDark) }
    val colors = if (isDark) darkColors else lightColors

    fun toggleTheme() {
        isDark = !isDark
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("is_dark", isDark).apply()
    }

    CompositionLocalProvider(LocalColors provides colors) {
        PasswordScreen(isDark = isDark, onToggleTheme = ::toggleTheme)
    }
}

// ── Theme toggle ─────────────────────────────────────────

@Composable
fun ThemeToggle(isDark: Boolean, onToggle: () -> Unit) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(c.raisedColor)
            .border(1.dp, c.borderColor, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (isDark) "☀" else "☾", fontSize = 18.sp)
    }
}

// ── Screen ───────────────────────────────────────────────

@Composable
fun PasswordScreen(isDark: Boolean, onToggleTheme: () -> Unit) {
    val c = LocalColors.current

    var length     by remember { mutableStateOf(16) }
    var useUpper   by remember { mutableStateOf(true) }
    var useLower   by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(false) }
    var password   by remember { mutableStateOf("") }
    var showCopied by remember { mutableStateOf(false) }
    var history    by remember { mutableStateOf(listOf<HistoryEntry>()) }

    val scope   = rememberCoroutineScope()
    val haptic  = LocalHapticFeedback.current
    val context = LocalContext.current

    val activeTypes = listOf(useUpper, useLower, useNumbers, useSymbols).count { it }
    val strength    = calcStrength(length, activeTypes)

    LaunchedEffect(Unit) {
        password = generate(length, useUpper, useLower, useNumbers, useSymbols)
    }

    fun doGenerate() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val pw = generate(length, useUpper, useLower, useNumbers, useSymbols)
        password = pw
        if (pw.isNotEmpty()) {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            history = (listOf(HistoryEntry(pw, time)) + history).take(8)
        }
    }

    fun doCopy(pw: String = password) {
        if (pw.isEmpty()) return
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("password", pw))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            showCopied = true
            delay(1800)
            showCopied = false
        }
    }

    Scaffold(
        containerColor = c.bgColor,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Header ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Password",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = c.accentColor,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = "Generator",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = c.textColor,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Secure  ·  Random  ·  Instant",
                        fontSize = 12.sp,
                        color = c.mutedColor,
                        letterSpacing = 2.sp,
                    )
                }
                ThemeToggle(isDark = isDark, onToggle = onToggleTheme)
            }

            // ── Password card ────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceColor)
                    .border(1.dp, c.borderColor, RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = password.ifEmpty { "Configure options below" },
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (password.isEmpty()) c.mutedColor else c.textColor,
                            letterSpacing = 1.5.sp,
                            lineHeight = 28.sp,
                        )

                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (showCopied) StrongColor.copy(alpha = 0.15f) else c.raisedColor)
                                    .border(1.dp, if (showCopied) StrongColor.copy(alpha = 0.5f) else c.borderColor, CircleShape)
                                    .clickable { doCopy() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (showCopied) "✓" else "⎘",
                                    fontSize = 18.sp,
                                    color = if (showCopied) StrongColor else c.mutedColor,
                                )
                            }
                        }
                    }

                    StrengthBar(strength = strength)
                }
            }

            // ── Length ───────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceColor)
                    .border(1.dp, c.borderColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Length", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.mutedColor, letterSpacing = 1.sp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "$length",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = c.accentColor,
                        )
                    }
                }
                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.toInt() },
                    valueRange = 4f..64f,
                    steps = 59,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = c.accentColor,
                        activeTrackColor = c.accentColor,
                        inactiveTrackColor = c.raisedColor,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("4", fontSize = 11.sp, color = c.mutedColor)
                    Text("64", fontSize = 11.sp, color = c.mutedColor)
                }
            }

            // ── Character types ──────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceColor)
                    .border(1.dp, c.borderColor, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Include", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.mutedColor, letterSpacing = 1.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TypeToggle("A–Z", "Uppercase", useUpper, Modifier.weight(1f)) {
                        if (activeTypes > 1 || !useUpper) useUpper = !useUpper
                    }
                    TypeToggle("a–z", "Lowercase", useLower, Modifier.weight(1f)) {
                        if (activeTypes > 1 || !useLower) useLower = !useLower
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TypeToggle("0–9", "Numbers", useNumbers, Modifier.weight(1f)) {
                        if (activeTypes > 1 || !useNumbers) useNumbers = !useNumbers
                    }
                    TypeToggle("#!@", "Symbols", useSymbols, Modifier.weight(1f)) {
                        if (activeTypes > 1 || !useSymbols) useSymbols = !useSymbols
                    }
                }
            }

            // ── Generate button ──────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(listOf(c.accentDark, c.accentColor))
                    )
                    .clickable { doGenerate() }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "GENERATE",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 4.sp,
                )
            }

            // ── History ──────────────────────────────────
            AnimatedVisibility(visible = history.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RECENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.mutedColor,
                        letterSpacing = 3.sp,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                    history.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.surfaceColor)
                                .border(1.dp, c.borderColor, RoundedCornerShape(12.dp))
                                .clickable { doCopy(entry.password) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry.password,
                                modifier = Modifier.weight(1f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = c.textColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.time,
                                fontSize = 11.sp,
                                color = c.mutedColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Strength bar ─────────────────────────────────────────

@Composable
fun StrengthBar(strength: Strength) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < strength.filled) strength.color
                            else c.borderColor
                        ),
                )
            }
        }
        Text(
            text = strength.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = strength.color,
            letterSpacing = 1.sp,
        )
    }
}

// ── Type toggle ──────────────────────────────────────────

@Composable
fun TypeToggle(
    symbol: String,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) c.accentColor.copy(alpha = 0.12f) else c.raisedColor)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) c.accentColor else c.borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) c.accentColor.copy(alpha = 0.15f) else c.borderColor.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = symbol,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = if (active) c.accentColor else c.mutedColor,
                fontFamily = FontFamily.Monospace,
            )
        }
        Column {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) c.textColor else c.mutedColor,
            )
            Text(
                text = if (active) "Included" else "Excluded",
                fontSize = 11.sp,
                color = if (active) c.accentColor else c.mutedColor.copy(alpha = 0.6f),
            )
        }
    }
}
