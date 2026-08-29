package com.moodtools.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private val GateInk = Color(0xFF090B10)
private val GateSurface = Color(0xFF12161D)
private val GateRaised = Color(0xFF191F28)
private val GateAccent = Color(0xFF80E4C6)
private val GateMuted = Color(0xFFAAB3BF)

internal enum class GateScene {
    Booting,
    Checking,
    Locked,
    ConnectionRequired,
    Blocked,
    Ready
}

internal sealed interface LauncherGatePresentation {
    val scene: GateScene

    data object Booting : LauncherGatePresentation {
        override val scene = GateScene.Booting
    }

    data class Checking(val root: Boolean) : LauncherGatePresentation {
        override val scene = GateScene.Checking
    }

    data class Locked(val message: String?) : LauncherGatePresentation {
        override val scene = GateScene.Locked
    }

    data class ConnectionRequired(val message: String) : LauncherGatePresentation {
        override val scene = GateScene.ConnectionRequired
    }

    data class RootDenied(val message: String) : LauncherGatePresentation {
        override val scene = GateScene.Blocked
    }

    data class SecurityBlocked(val message: String) : LauncherGatePresentation {
        override val scene = GateScene.Blocked
    }

    data class Ready(val expiresAt: Long) : LauncherGatePresentation {
        override val scene = GateScene.Ready
    }
}

internal fun launcherGatePresentation(
    state: LauncherStartupState,
    bootSettled: Boolean
): LauncherGatePresentation {
    val canShowBootFrame = state !is LauncherStartupState.RootDenied &&
        state !is LauncherStartupState.SecurityBlocked
    if (!bootSettled && canShowBootFrame) return LauncherGatePresentation.Booting
    return when (state) {
        LauncherStartupState.CheckingRoot -> LauncherGatePresentation.Checking(root = true)
        LauncherStartupState.CheckingAccess -> LauncherGatePresentation.Checking(root = false)
        is LauncherStartupState.RootDenied -> LauncherGatePresentation.RootDenied(state.message)
        is LauncherStartupState.SecurityBlocked -> LauncherGatePresentation.SecurityBlocked(state.message)
        is LauncherStartupState.ConnectionRequired ->
            LauncherGatePresentation.ConnectionRequired(state.message)
        is LauncherStartupState.Locked -> LauncherGatePresentation.Locked(state.message)
        is LauncherStartupState.Ready -> LauncherGatePresentation.Ready(state.expiresAt)
    }
}

private const val GATE_BOOT_SETTLE_MS = 460L

@Composable
fun LauncherGateScreen(
    state: LauncherStartupState,
    onUnlock: () -> Unit,
    onRetry: () -> Unit,
    onEnter: () -> Unit,
    onCopySupportCode: () -> Unit,
    onExit: () -> Unit
) {
    val bootSettled by produceState(initialValue = false) {
        delay(GATE_BOOT_SETTLE_MS)
        value = true
    }
    val presentation = launcherGatePresentation(state, bootSettled)
    val haloTransition = rememberInfiniteTransition(label = "gate-halo")
    val haloScale by haloTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gate-halo-scale"
    )
    val haloAlpha by haloTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gate-halo-alpha"
    )
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = GateInk) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF111B1B), GateInk, Color(0xFF0B0E14)))
                ).windowInsetsPadding(WindowInsets.safeDrawing).padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 68.dp)
                        .size(360.dp)
                        .scale(haloScale)
                        .alpha(haloAlpha)
                        .background(
                            Brush.radialGradient(
                                listOf(GateAccent, Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                AnimatedContent(
                    targetState = presentation,
                    transitionSpec = {
                        val direction = if (targetState.scene == GateScene.Ready) 1 else -1
                        (
                            fadeIn(tween(260)) +
                                scaleIn(tween(360), initialScale = 0.97f) +
                                slideInVertically(tween(360)) { fullHeight -> fullHeight / 18 * direction }
                        ) togetherWith (
                            fadeOut(tween(180)) +
                                scaleOut(tween(220), targetScale = 0.985f) +
                                slideOutVertically(tween(220)) { fullHeight -> -fullHeight / 24 * direction }
                        ) using SizeTransform(clip = false)
                    },
                    label = "launcher-gate-scene"
                ) { targetPresentation ->
                    when (targetPresentation) {
                        LauncherGatePresentation.Booting -> BootGateContent()
                        is LauncherGatePresentation.Ready -> {
                            val expiresAtMillis = targetPresentation.expiresAt
                                .takeIf { it != Long.MAX_VALUE }
                                ?.let { seconds -> seconds * 1_000L }
                            ReadyAccessContent(
                                expiresAtMillis = expiresAtMillis,
                                onEnter = onEnter
                            )
                        }
                        else -> {
                            DefaultGateContent(
                                presentation = targetPresentation,
                                onUnlock = onUnlock,
                                onRetry = onRetry,
                                onCopySupportCode = onCopySupportCode,
                                onExit = onExit
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BootGateContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GateBrandHeader()
        Spacer(Modifier.weight(0.9f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GateSurface.copy(alpha = 0.97f), RoundedCornerShape(30.dp))
                .border(
                    BorderStroke(1.dp, GateAccent.copy(alpha = 0.10f)),
                    RoundedCornerShape(30.dp)
                )
                .padding(24.dp)
        ) {
            Text(
                "Preparing secure access",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Restoring your launcher session and checking the device handoff.",
                color = GateMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                color = GateAccent,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ReadyAccessContent(
    expiresAtMillis: Long?,
    onEnter: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val now by produceState(initialValue = System.currentTimeMillis(), expiresAtMillis) {
        while (expiresAtMillis != null) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val expiryText = remember(expiresAtMillis) {
        expiresAtMillis?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
        }
    }
    val remainingMillis = expiresAtMillis?.minus(now)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.97f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GateBrandHeader()

            Spacer(Modifier.weight(0.85f))

            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(GateAccent.copy(alpha = 0.10f), CircleShape)
                    .border(1.dp, GateAccent.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .background(GateAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "\u2713",
                        color = Color(0xFF07100D),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Access is active",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your launcher is ready to use",
                color = GateMuted,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(30.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GateSurface.copy(alpha = 0.96f), RoundedCornerShape(30.dp))
                    .border(
                        BorderStroke(1.dp, GateAccent.copy(alpha = 0.13f)),
                        RoundedCornerShape(30.dp)
                    )
                    .padding(22.dp)
            ) {
                Text(
                    "TIME REMAINING",
                    color = GateAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    remainingMillis?.let(::formatRemainingAccessPrimary) ?: "Active",
                    color = Color.White,
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Available until",
                        color = GateMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        expiryText ?: "This test launch",
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onEnter,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GateAccent,
                        contentColor = Color(0xFF07100D)
                    )
                ) {
                    Text("Enter launcher  \u2192", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun DefaultGateContent(
    presentation: LauncherGatePresentation,
    onUnlock: () -> Unit,
    onRetry: () -> Unit,
    onCopySupportCode: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GateBrandHeader()
        Spacer(Modifier.weight(0.9f))
        Column(
            modifier = Modifier.fillMaxWidth().background(
                GateSurface.copy(alpha = 0.97f), RoundedCornerShape(30.dp)
            ).border(
                BorderStroke(1.dp, GateAccent.copy(alpha = 0.10f)),
                RoundedCornerShape(30.dp)
            ).padding(24.dp)
        ) {
            Text(
                when (presentation) {
                    is LauncherGatePresentation.Checking ->
                        if (presentation.root) "Checking root access" else "Checking your digital key"
                    is LauncherGatePresentation.RootDenied -> "Root access is required"
                    is LauncherGatePresentation.SecurityBlocked -> "Security check failed"
                    is LauncherGatePresentation.ConnectionRequired -> "Connect to restore access"
                    is LauncherGatePresentation.Locked -> "Unlock for 1 day"
                    else -> error("Unsupported default gate presentation: $presentation")
                },
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when (presentation) {
                    is LauncherGatePresentation.Checking -> "This should only take a moment."
                    is LauncherGatePresentation.RootDenied -> presentation.message
                    is LauncherGatePresentation.SecurityBlocked -> presentation.message
                    is LauncherGatePresentation.ConnectionRequired -> presentation.message
                    is LauncherGatePresentation.Locked -> presentation.message
                        ?: "Complete the free Linkvertise route once to activate this launcher for one day. When the website confirms your route, tap Open launcher to come back here."
                    else -> error("Unsupported default gate presentation: $presentation")
                },
                color = GateMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            if (presentation is LauncherGatePresentation.Locked) {
                Spacer(Modifier.height(18.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().background(
                        GateRaised, RoundedCornerShape(18.dp)
                    ).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "1. Continue to the Jester Mods website\n2. Complete Linkvertise and the browser check\n3. Tap Open launcher on the website",
                        color = GateMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            when (presentation) {
                is LauncherGatePresentation.Checking -> CircularProgressIndicator(
                    color = GateAccent,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                is LauncherGatePresentation.RootDenied,
                is LauncherGatePresentation.SecurityBlocked -> Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB4AB),
                        contentColor = Color(0xFF250000)
                    )
                ) { Text("Exit", fontWeight = FontWeight.Bold) }
                is LauncherGatePresentation.ConnectionRequired -> Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GateAccent,
                        contentColor = Color(0xFF09100E)
                    )
                ) { Text("Try again", fontWeight = FontWeight.Bold) }
                is LauncherGatePresentation.Locked -> Button(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GateAccent,
                        contentColor = Color(0xFF09100E)
                    )
                ) { Text("Continue to Linkvertise", fontWeight = FontWeight.Bold) }
                else -> error("Unsupported default gate presentation: $presentation")
            }
            if (presentation is LauncherGatePresentation.Locked) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCopySupportCode,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    border = BorderStroke(1.dp, GateAccent.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GateAccent)
                ) {
                    Text("Copy support code", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun GateBrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(GateAccent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .border(1.dp, GateAccent.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.jester_moods_icon),
                contentDescription = "Jester Mods",
                modifier = Modifier.size(36.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 11.dp)) {
            Text(
                "JESTER MODS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                "LAUNCHER",
                color = GateMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.7.sp
            )
        }
    }
}

private fun formatRemainingAccessPrimary(milliseconds: Long): String {
    if (milliseconds <= 0L) return "Expired"
    var minutes = (milliseconds + 59_999L) / 60_000L
    val days = minutes / 1_440L
    minutes %= 1_440L
    val hours = minutes / 60L
    minutes %= 60L
    return buildString {
        if (days > 0L) append("${days}d ")
        if (days > 0L || hours > 0L) append("${hours}h ")
        if (days == 0L) append("${minutes}m")
    }
}
