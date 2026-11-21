package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ChatMessageEntity
import com.example.data.CoreSettings
import com.example.hardware.DeviceTelemetry
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ArcReactorBlue
import com.example.ui.theme.CyanMicroGrid
import com.example.ui.theme.CyanMuted
import com.example.ui.theme.EmeraldNominal
import com.example.ui.theme.HolographicCyan
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianInput
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.VoidObsidian
import com.example.viewmodel.CoreVisualState
import com.example.viewmodel.TacticalTimer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom sci-fi HUD Modifier that draws a chamfered glass container,
 * glowing 1.dp perimeter stroke, and precision corner targeting brackets.
 */
fun Modifier.chamferedHudPanel(
    chamfer: Dp = 12.dp,
    borderColor: Color = HolographicCyan.copy(alpha = 0.55f),
    fillColor: Color = ObsidianPanel,
    bracketColor: Color = HolographicCyan,
    showBrackets: Boolean = true
): Modifier = this.drawWithContent {
    val c = chamfer.toPx()
    val w = size.width
    val h = size.height

    val path = Path().apply {
        moveTo(c, 0f)
        lineTo(w, 0f)
        lineTo(w, h - c)
        lineTo(w - c, h)
        lineTo(0f, h)
        lineTo(0f, c)
        close()
    }

    drawPath(path = path, color = fillColor)
    drawPath(
        path = path,
        color = borderColor,
        style = Stroke(width = 1.dp.toPx())
    )

    if (showBrackets) {
        val bLen = 10.dp.toPx()
        val bStroke = 2.dp.toPx()
        // Top-right corner bracket
        drawLine(bracketColor, Offset(w - bLen, 0f), Offset(w, 0f), bStroke)
        drawLine(bracketColor, Offset(w, 0f), Offset(w, bLen), bStroke)
        // Bottom-left corner bracket
        drawLine(bracketColor, Offset(0f, h - bLen), Offset(0f, h), bStroke)
        drawLine(bracketColor, Offset(0f, h), Offset(bLen, h), bStroke)
    }

    drawContent()
}

/**
 * Animated obsidian tactical micro-grid backdrop with radial cyan core bloom.
 */
@Composable
fun HudBackgroundCanvas(
    isOverclock: Boolean,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "hud_bg")
    val scanProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline"
    )

    val glowColor = if (isOverclock) TacticalAmber else HolographicCyan

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = VoidObsidian)

        // Subtle radial atmosphere around upper arc-reactor zone
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.14f),
                    ArcReactorBlue.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.22f),
                radius = size.minDimension * 0.85f
            )
        )

        // Micro-grid lines
        val spacing = 28.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = CyanMicroGrid,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.6.dp.toPx()
            )
            x += spacing
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = CyanMicroGrid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.6.dp.toPx()
            )
            y += spacing
        }

        // Subtle downward holographic scanline
        val scanY = scanProgress * size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    glowColor.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                startY = scanY - 36.dp.toPx(),
                endY = scanY + 36.dp.toPx()
            ),
            topLeft = Offset(0f, (scanY - 36.dp.toPx()).coerceAtLeast(0f)),
            size = Size(size.width, 72.dp.toPx())
        )
    }
}

/**
 * Multi-layered interactive holographic Arc-Reactor AI Core.
 */
@Composable
fun ArcReactorCore(
    visualState: CoreVisualState,
    voiceAmplitude: Float,
    isOverclock: Boolean,
    partialTranscript: String,
    onTapCore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "arc_reactor")

    val slowCw by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing)
        ),
        label = "slow_cw"
    )
    val slowCcw by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing)
        ),
        label = "slow_ccw"
    )
    val fastSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing)
        ),
        label = "fast_spin"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeat"
    )
    val speakingWave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speaking_wave"
    )

    val smoothedAmp by animateFloatAsState(
        targetValue = if (visualState == CoreVisualState.LISTENING) voiceAmplitude else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "rms_amp"
    )

    val primaryAccent = when {
        visualState == CoreVisualState.THINKING -> TacticalAmber
        isOverclock -> TacticalAmber
        else -> HolographicCyan
    }
    val secondaryAccent = when (visualState) {
        CoreVisualState.THINKING -> HolographicCyan
        CoreVisualState.SPEAKING -> EmeraldNominal
        else -> ArcReactorBlue
    }

    val statusText = when (visualState) {
        CoreVisualState.IDLE -> stringResource(R.string.status_idle)
        CoreVisualState.LISTENING -> if (partialTranscript.isNotBlank()) {
            "\"$partialTranscript\""
        } else {
            stringResource(R.string.status_listening)
        }
        CoreVisualState.THINKING -> stringResource(R.string.status_thinking)
        CoreVisualState.SPEAKING -> stringResource(R.string.status_speaking)
    }

    val cdCore = stringResource(R.string.cd_arc_reactor)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(164.dp)
                .clip(CircleShape)
                .testTag("arc_reactor_core_button")
                .semantics {
                    contentDescription = cdCore
                    role = Role.Button
                }
                .clickable(onClick = onTapCore)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension * 0.48f
                val reactiveScale = 1f + smoothedAmp * 0.14f

                // Outer luminous halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.28f * pulse),
                            secondaryAccent.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxR * 1.12f
                    ),
                    radius = maxR * 1.12f,
                    center = center
                )

                // SPEAKING harmonic concentric soundwaves
                if (visualState == CoreVisualState.SPEAKING) {
                    for (i in 0..2) {
                        val phase = (speakingWave + i * 0.33f) % 1f
                        val waveR = maxR * (0.45f + phase * 0.58f)
                        drawCircle(
                            color = HolographicCyan.copy(alpha = (1f - phase) * 0.55f),
                            radius = waveR,
                            center = center,
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }
                }

                // LISTENING reactive radial audio bars
                if (visualState == CoreVisualState.LISTENING) {
                    val spokeCount = 32
                    for (i in 0 until spokeCount) {
                        val angleRad = (i * (360f / spokeCount) + slowCw) * (PI / 180.0)
                        val waveMod = 0.35f + 0.65f * ((sin(i * 1.3 + slowCw * 0.2).toFloat() + 1f) / 2f)
                        val innerR = maxR * 0.78f
                        val outerR = innerR + maxR * (0.06f + smoothedAmp * 0.22f * waveMod)
                        drawLine(
                            color = HolographicCyan.copy(alpha = 0.75f),
                            start = Offset(
                                center.x + (cos(angleRad) * innerR).toFloat(),
                                center.y + (sin(angleRad) * innerR).toFloat()
                            ),
                            end = Offset(
                                center.x + (cos(angleRad) * outerR).toFloat(),
                                center.y + (sin(angleRad) * outerR).toFloat()
                            ),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Ring 1: Outer segmented tactical ring
                val ring1Angle = if (visualState == CoreVisualState.THINKING) fastSpin else slowCw
                rotate(degrees = ring1Angle, pivot = center) {
                    val r1 = maxR * 0.90f * reactiveScale
                    for (seg in 0 until 6) {
                        drawArc(
                            color = primaryAccent.copy(alpha = 0.72f),
                            startAngle = seg * 60f + 6f,
                            sweepAngle = 42f,
                            useCenter = false,
                            topLeft = Offset(center.x - r1, center.y - r1),
                            size = Size(r1 * 2, r1 * 2),
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Butt)
                        )
                    }
                }

                // Ring 2: Counter-rotating precision tick ring
                val ring2Angle = if (visualState == CoreVisualState.THINKING) -fastSpin * 1.35f else slowCcw
                rotate(degrees = ring2Angle, pivot = center) {
                    val r2 = maxR * 0.72f * reactiveScale
                    drawArc(
                        color = secondaryAccent.copy(alpha = 0.85f),
                        startAngle = 0f,
                        sweepAngle = 115f,
                        useCenter = false,
                        topLeft = Offset(center.x - r2, center.y - r2),
                        size = Size(r2 * 2, r2 * 2),
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = primaryAccent.copy(alpha = 0.85f),
                        startAngle = 180f,
                        sweepAngle = 115f,
                        useCenter = false,
                        topLeft = Offset(center.x - r2, center.y - r2),
                        size = Size(r2 * 2, r2 * 2),
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Ring 3: Inner turbine ring
                val ring3Angle = if (visualState == CoreVisualState.THINKING) fastSpin * 2f else slowCw * 1.5f
                rotate(degrees = ring3Angle, pivot = center) {
                    val r3 = maxR * 0.50f * pulse
                    for (seg in 0 until 8) {
                        drawArc(
                            color = primaryAccent.copy(alpha = 0.92f),
                            startAngle = seg * 45f + 5f,
                            sweepAngle = 24f,
                            useCenter = false,
                            topLeft = Offset(center.x - r3, center.y - r3),
                            size = Size(r3 * 2, r3 * 2),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Butt)
                        )
                    }
                }

                // Central luminous reactor heart
                val coreR = maxR * 0.30f * (if (visualState == CoreVisualState.LISTENING) reactiveScale else pulse)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            primaryAccent,
                            secondaryAccent.copy(alpha = 0.45f)
                        ),
                        center = center,
                        radius = coreR
                    ),
                    radius = coreR,
                    center = center
                )
            }

            Icon(
                imageVector = if (visualState == CoreVisualState.LISTENING) Icons.Default.Mic else Icons.Default.Bolt,
                contentDescription = null,
                tint = VoidObsidian,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = primaryAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

/**
 * Top Telemetry HUD Bar displaying real Android BatteryManager, ConnectivityManager,
 * active Gemini model badge, and quick action buttons.
 */
@Composable
fun TopTelemetryBar(
    telemetry: DeviceTelemetry,
    settings: CoreSettings,
    pendingMissionCount: Int,
    memoryCount: Int,
    onToggleOverclock: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenMemories: () -> Unit,
    onOpenMissions: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .chamferedHudPanel(
                chamfer = 10.dp,
                borderColor = if (settings.overclockEnabled) TacticalAmber.copy(alpha = 0.7f) else HolographicCyan.copy(alpha = 0.55f),
                bracketColor = if (settings.overclockEnabled) TacticalAmber else HolographicCyan
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hud_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = HolographicCyan
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Battery telemetry
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            contentDescription = "Battery level",
                            tint = if (telemetry.batteryPercent <= 20) AlertCrimson else EmeraldNominal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${telemetry.batteryPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudTextPrimary
                        )
                    }

                    // Network telemetry
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (telemetry.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "Network status",
                            tint = if (telemetry.isOnline) HolographicCyan else AlertCrimson,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = telemetry.networkStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.isOnline) HudTextSecondary else AlertCrimson
                        )
                    }
                }
            }

            // Active Gemini model clickable badge (toggles OVERCLOCK)
            Surface(
                color = if (settings.overclockEnabled) TacticalAmber.copy(alpha = 0.2f) else CyanMuted.copy(alpha = 0.45f),
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = if (settings.overclockEnabled) TacticalAmber else HolographicCyan.copy(alpha = 0.65f),
                        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
                    )
                    .minimumInteractiveComponentSize()
                    .testTag("model_overclock_badge")
                    .clickable(onClick = onToggleOverclock)
            ) {
                Text(
                    text = if (settings.overclockEnabled) "OVERCLOCK" else "FLASH 3.8",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (settings.overclockEnabled) TacticalAmber else HolographicCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick HUD Action Strip (48dp accessible touch targets)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HudNavActionPill(
                icon = Icons.Default.Memory,
                label = "VAULT ($memoryCount)",
                contentDescription = stringResource(R.string.cd_memory_vault),
                testTag = "open_memory_vault_button",
                onClick = onOpenMemories
            )
            HudNavActionPill(
                icon = Icons.Default.TaskAlt,
                label = "MISSIONS ($pendingMissionCount)",
                contentDescription = stringResource(R.string.cd_mission_control),
                testTag = "open_mission_control_button",
                onClick = onOpenMissions
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleTts,
                    modifier = Modifier.testTag("toggle_tts_button")
                ) {
                    Icon(
                        imageVector = if (settings.ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = stringResource(R.string.cd_toggle_tts),
                        tint = if (settings.ttsEnabled) HolographicCyan else HudTextMuted
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("open_core_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.cd_core_settings),
                        tint = HolographicCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun HudNavActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
            .background(ObsidianCard)
            .border(
                width = 1.dp,
                color = HolographicCyan.copy(alpha = 0.35f),
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
            )
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HolographicCyan,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HudTextPrimary
        )
    }
}

/**
 * Animated glowing protocol execution indicator when Gemini invokes agentic tools.
 */
@Composable
fun ProtocolExecutionBanner(
    protocolName: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !protocolName.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val infinite = rememberInfiniteTransition(label = "proto_pulse")
        val alpha by infinite.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(550),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .chamferedHudPanel(
                    chamfer = 8.dp,
                    borderColor = TacticalAmber.copy(alpha = alpha),
                    fillColor = TacticalAmber.copy(alpha = 0.14f),
                    bracketColor = TacticalAmber,
                    showBrackets = false
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = TacticalAmber,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${stringResource(R.string.protocol_executing_prefix)} ${protocolName.orEmpty()}",
                style = MaterialTheme.typography.titleSmall,
                color = TacticalAmber
            )
        }
    }
}

/**
 * Live ticking tactical countdown timers strip shown on HUD whenever timers are active.
 */
@Composable
fun ActiveTimersStrip(
    timers: List<TacticalTimer>,
    onCancelTimer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (timers.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        timers.forEach { timer ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .chamferedHudPanel(
                        chamfer = 8.dp,
                        borderColor = TacticalAmber.copy(alpha = 0.8f),
                        fillColor = ObsidianCard,
                        bracketColor = TacticalAmber,
                        showBrackets = false
                    )
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = TacticalAmber,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${timer.label.uppercase()} • ${timer.formattedRemaining}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TacticalAmber
                )
                IconButton(
                    onClick = { onCancelTimer(timer.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel timer ${timer.label}",
                        tint = HudTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Holographic chat message card with [USER // INPUT] and [J.A.R.V.I.S. // CORE] tags,
 * code block formatting, tool logs, and Coil vision image preview.
 */
@Composable
fun CommsMessageCard(
    message: ChatMessageEntity,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "USER"
    val isSystem = message.role == "SYSTEM"

    val tagText = when {
        isUser -> "[USER // INPUT]"
        isSystem -> "[SYSTEM // TELEMETRY]"
        else -> "[J.A.R.V.I.S. // CORE]"
    }
    val accent = when {
        isUser -> ArcReactorBlue
        isSystem -> AlertCrimson
        else -> HolographicCyan
    }

    val timeLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.88f else 0.96f)
                .chamferedHudPanel(
                    chamfer = 10.dp,
                    borderColor = accent.copy(alpha = 0.55f),
                    fillColor = if (isUser) Color(0xCC0C2240) else ObsidianPanel,
                    bracketColor = accent,
                    showBrackets = !isUser
                )
                .padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = tagText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!message.modelBadge.isNullOrBlank()) {
                        Text(
                            text = message.modelBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (message.modelBadge.contains("OVERCLOCK")) TacticalAmber else HudTextMuted
                        )
                    }
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = HudTextMuted
                    )
                }
            }

            if (!message.toolCallLog.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalAmber.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalAmber.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "PROTOCOL LOG // ${message.toolCallLog}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalAmber
                    )
                }
            }

            if (!message.imagePath.isNullOrBlank()) {
                val imgFile = File(message.imagePath)
                if (imgFile.exists()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = imgFile,
                        contentDescription = stringResource(R.string.cd_attached_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                            .border(
                                1.dp,
                                HolographicCyan.copy(alpha = 0.5f),
                                CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            FormattedMessageBody(text = message.content)
        }
    }
}

@Composable
private fun FormattedMessageBody(text: String) {
    val segments = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEachIndexed { index, rawSegment ->
            val segment = rawSegment.trim()
            if (segment.isEmpty()) return@forEachIndexed
            if (index % 2 == 1) {
                // Code block segment
                val lines = segment.lines()
                val body = if (lines.size > 1 && lines.first().length <= 16 && !lines.first().contains(" ")) {
                    lines.drop(1).joinToString("\n")
                } else {
                    segment
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VoidObsidian.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .border(1.dp, HolographicCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = HolographicCyan
                    )
                }
            } else {
                Text(
                    text = segment,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HudTextPrimary
                )
            }
        }
    }
}

/**
 * Bottom Command Deck with Quick-Command chips, vision image attachment preview,
 * chamfered input field, Camera/Gallery buttons, and glowing Push-to-Talk microphone.
 */
@Composable
fun BottomCommandDeck(
    inputText: String,
    onInputChange: (String) -> Unit,
    attachedImage: Bitmap?,
    isListening: Boolean,
    isThinking: Boolean,
    onRemoveAttachedImage: () -> Unit,
    onQuickCommand: (String) -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickCommands = listOf(
        stringResource(R.string.cmd_status_report) to "Give me a full tactical status report using device telemetry.",
        stringResource(R.string.cmd_daily_briefing) to "Deliver my daily tactical briefing covering active missions and memories.",
        stringResource(R.string.cmd_scan_object) to "__SCAN_CAMERA__",
        stringResource(R.string.cmd_new_mission) to "Add a new high-priority mission: "
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = HolographicCyan.copy(alpha = 0.35f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .background(VoidObsidian.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 8.dp, bottom = 10.dp)
    ) {
        // Quick-command chips row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            quickCommands.forEach { (chipLabel, payload) ->
                Surface(
                    color = ObsidianCard,
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    modifier = Modifier
                        .border(
                            1.dp,
                            HolographicCyan.copy(alpha = 0.4f),
                            CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                        )
                        .testTag("quick_chip_${chipLabel.lowercase().replace(" ", "_")}")
                        .clickable {
                            when {
                                payload == "__SCAN_CAMERA__" -> onCameraClick()
                                payload.endsWith(": ") -> onInputChange(payload)
                                else -> onQuickCommand(payload)
                            }
                        }
                ) {
                    Text(
                        text = chipLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = HolographicCyan,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Attached visual scan preview strip
        if (attachedImage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .chamferedHudPanel(chamfer = 8.dp, showBrackets = false)
                    .padding(6.dp)
            ) {
                Image(
                    bitmap = attachedImage.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_attached_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VISUAL FEED READY // GEMINI VISION",
                    style = MaterialTheme.typography.labelSmall,
                    color = HolographicCyan,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onRemoveAttachedImage,
                    modifier = Modifier.testTag("remove_attached_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_remove_image),
                        tint = AlertCrimson
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main input row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            IconButton(
                onClick = onCameraClick,
                modifier = Modifier.testTag("camera_vision_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.cd_camera_scan),
                    tint = HolographicCyan
                )
            }

            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier.testTag("gallery_vision_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = stringResource(R.string.cd_gallery_scan),
                    tint = HudTextSecondary
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.input_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = false,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = HudTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HolographicCyan,
                    unfocusedBorderColor = CyanMuted,
                    focusedContainerColor = ObsidianInput,
                    unfocusedContainerColor = ObsidianInput,
                    cursorColor = HolographicCyan
                ),
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field")
            )

            // Glowing Push-to-Talk Mic Button
            val micBg = if (isListening) AlertCrimson else HolographicCyan.copy(alpha = 0.18f)
            val micBorder = if (isListening) AlertCrimson else HolographicCyan
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(micBg)
                    .border(1.5.dp, micBorder, CircleShape)
                    .testTag("push_to_talk_button")
                    .clickable(onClick = onVoiceClick)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.cd_push_to_talk),
                    tint = if (isListening) HudTextPrimary else HolographicCyan
                )
            }

            // Send Button
            val canSend = (inputText.isNotBlank() || attachedImage != null) && !isThinking
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(if (canSend) HolographicCyan else CyanMuted.copy(alpha = 0.4f))
                    .testTag("send_command_button")
                    .clickable(enabled = canSend, onClick = onSendClick)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.cd_send_command),
                    tint = if (canSend) VoidObsidian else HudTextMuted
                )
            }
        }
    }
}
