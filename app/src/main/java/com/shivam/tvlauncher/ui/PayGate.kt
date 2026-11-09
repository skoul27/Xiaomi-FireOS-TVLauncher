package com.shivam.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.PayConfig
import com.shivam.tvlauncher.PayPlan
import com.shivam.tvlauncher.PayToPlay
import com.shivam.tvlauncher.PendingPayment

/** What the gate in front of a paid HDMI input is showing. */
sealed interface GateStep {
    /** Choose how long to play. [notice] says why we're back here (declined, timed out). */
    data class Plans(val notice: String? = null) : GateStep
    /** The UPI QR code, waiting on the owner's phone. */
    data class Paying(val payment: PendingPayment) : GateStep
    data object Pin : GateStep
    data class TimeUp(val at: Long) : GateStep
}

private val Amber = Color(0xFFFFC46B)
private val Alert = Color(0xFFFF8A80)
private val Good = Color(0xFF7BD389)

/** The full-screen gate over a paid input: everything a player sees before (and after) play. */
@Composable
fun PayGate(
    consoleName: String,
    step: GateStep,
    config: PayConfig,
    now: Long,
    onPlan: (PayPlan) -> Unit,
    onAdmin: () -> Unit,
    onPin: (String) -> String?,
    onPlans: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1D24), LauncherColors.Ink)))
            .navigationSounds(),
    ) {
        when (step) {
            is GateStep.Plans -> PlansScreen(consoleName, config, step.notice, onPlan, onAdmin, onExit)
            is GateStep.Paying -> PayingScreen(config, step.payment, now, onPlans)
            GateStep.Pin -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Admin code", style = LauncherText.Display, color = LauncherColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("Unlimited play until $consoleName is closed", style = LauncherText.Body, color = LauncherColors.TextSecondary)
                Spacer(Modifier.height(24.dp))
                PinPad(onSubmit = onPin)
            }
            is GateStep.TimeUp -> TimeUpScreen(consoleName, ((step.at + TimeUpExitMillis - now) / 1000).coerceAtLeast(0), onPlans, onExit)
        }
    }
}

/** How long "Time's up" waits before going back to the launcher. */
const val TimeUpExitMillis = 60_000L

@Composable
private fun PlansScreen(
    consoleName: String,
    config: PayConfig,
    notice: String?,
    onPlan: (PayPlan) -> Unit,
    onAdmin: () -> Unit,
    onExit: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(consoleName, style = LauncherText.Display, color = LauncherColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        if (config.canTakePayment) {
            Text("Choose how long you want to play", style = LauncherText.Body, color = LauncherColors.TextSecondary)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                config.plans.forEachIndexed { index, plan ->
                    GateCard(Modifier.size(170.dp, 124.dp).then(if (index == 0) Modifier.focusRequester(first) else Modifier), onClick = { onPlan(plan) }) { focused ->
                        Text(plan.durationLabel, style = LauncherText.Title, color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("₹${plan.price}", style = LauncherText.Display, color = if (focused) LauncherColors.Ink else LocalAccent.current)
                    }
                }
            }
        } else {
            Text("Pay to play isn't set up yet. Ask the owner.", style = LauncherText.Body, color = LauncherColors.TextSecondary)
        }
        if (notice != null) {
            Spacer(Modifier.height(18.dp))
            Text(notice, style = LauncherText.Body, color = Amber)
        }
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GateButton("Admin code", if (config.canTakePayment) Modifier else Modifier.focusRequester(first), onClick = onAdmin)
            GateButton("Exit", onClick = onExit)
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun PayingScreen(config: PayConfig, payment: PendingPayment, now: Long, onPlans: () -> Unit) {
    val back = remember { FocusRequester() }
    val link = remember(payment) { PayToPlay.upiLink(config, payment) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        QrCode(link, Modifier.size(280.dp))
        Spacer(Modifier.width(40.dp))
        Column(Modifier.widthIn(max = 420.dp)) {
            Text("₹${payment.plan.price}", style = LauncherText.Hero, color = LauncherColors.TextPrimary)
            Text("for ${payment.plan.durationLabel} on ${payment.portName}", style = LauncherText.Title, color = LauncherColors.TextSecondary)
            if (payment.custom) Text("Set by the owner", style = LauncherText.Caption, color = LauncherColors.TextTertiary)
            Spacer(Modifier.height(18.dp))
            Text("Scan with any UPI app (Google Pay, PhonePe, Paytm…) and pay.", style = LauncherText.Body, color = LauncherColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(config.payee, config.upiId).filter { it.isNotBlank() }.joinToString(" · "),
                style = LauncherText.Caption,
                color = LauncherColors.TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Text("Reference ${payment.ref}", style = LauncherText.Title, color = LocalAccent.current)
            Spacer(Modifier.height(14.dp))
            val dots = ".".repeat(((now / 500) % 4).toInt())
            Text("Waiting for the owner to confirm$dots", style = LauncherText.Body, color = Amber)
            Spacer(Modifier.height(24.dp))
            GateButton("Change plan", Modifier.focusRequester(back), onClick = onPlans)
        }
    }
    LaunchedEffect(payment) { runCatching { back.requestFocus() } }
}

@Composable
private fun TimeUpScreen(consoleName: String, secondsLeft: Long, onPlans: () -> Unit, onExit: () -> Unit) {
    val first = remember { FocusRequester() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Time's up", style = LauncherText.Hero, color = LauncherColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Thanks for playing $consoleName. Add more time to keep going.", style = LauncherText.Body, color = LauncherColors.TextSecondary)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GateButton("Add time", Modifier.focusRequester(first), onClick = onPlans)
            GateButton("Exit", onClick = onExit)
        }
        Spacer(Modifier.height(18.dp))
        Text("Back to the home screen in $secondsLeft s", style = LauncherText.Caption, color = LauncherColors.TextTertiary)
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * The time left, top-right over the game. Quiet while there's plenty; amber in the last five
 * minutes, red in the last one. Admin play shows its badge for a few seconds, then gets out of the way.
 */
@Composable
fun PlayTimer(leftMillis: Long, unlimited: Boolean, showBadge: Boolean) {
    Box(Modifier.fillMaxSize().padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical), contentAlignment = Alignment.TopEnd) {
        val (text, color, big) = when {
            unlimited -> if (showBadge) Triple("Admin · no time limit", Good, false) else return@Box
            leftMillis <= 60_000 -> Triple("${clock(leftMillis)} left", Alert, true)
            leftMillis <= 5 * 60_000 -> Triple("${clock(leftMillis)} left", Amber, false)
            else -> Triple(clock(leftMillis), LauncherColors.TextPrimary, false)
        }
        Text(
            text,
            style = if (big) LauncherText.Title else LauncherText.Label,
            color = color,
            modifier = Modifier
                .clip(PillShape)
                .background(Color.Black.copy(alpha = if (big) 0.75f else 0.45f))
                .padding(horizontal = if (big) 18.dp else 12.dp, vertical = if (big) 8.dp else 4.dp),
        )
    }
}

/** 1:05:09 or 4:09. Rounded up, so it never reads 0:00 while time remains. */
private fun clock(millis: Long): String {
    val total = (millis + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Digits by D-pad (or the remote's number keys, where it has them). [onSubmit] returns why the
 * code was refused, or null when it was accepted.
 */
@Composable
fun PinPad(onSubmit: (String) -> String?) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val submit = {
        if (code.isNotEmpty()) {
            error = onSubmit(code)
            code = ""
        }
    }
    val type = { digit: Char -> if (code.length < 8) { code += digit; error = null } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val digit = NumberKeys[event.key]
            when {
                digit != null -> { type(digit); true }
                event.key == Key.Backspace || event.key == Key.Delete -> { code = code.dropLast(1); true }
                else -> false
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(24.dp)) {
            if (code.isEmpty()) {
                Text("Enter the code", style = LauncherText.Body, color = LauncherColors.TextTertiary)
            } else {
                repeat(code.length) { Box(Modifier.size(16.dp).clip(PillShape).background(LauncherColors.TextPrimary)) }
            }
        }
        Spacer(Modifier.height(18.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        for (row in keys.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (key in row) {
                    val modifier = Modifier.size(84.dp, 56.dp).then(if (key == "1") Modifier.focusRequester(first) else Modifier)
                    GateCard(modifier, onClick = {
                        when (key) {
                            "⌫" -> code = code.dropLast(1)
                            "OK" -> submit()
                            else -> type(key[0])
                        }
                    }) { focused ->
                        Text(key, style = LauncherText.Title, color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(error.orEmpty(), style = LauncherText.Body, color = Alert, textAlign = TextAlign.Center, modifier = Modifier.height(24.dp))
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private val NumberKeys = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9',
)

/** What the admin code check says, as a line under the pad (null: accepted). */
fun PayToPlay.PinResult.message(): String? = when (this) {
    PayToPlay.PinResult.Ok -> null
    is PayToPlay.PinResult.Wrong -> "Wrong code. $triesLeft ${if (triesLeft == 1) "try" else "tries"} left."
    is PayToPlay.PinResult.Locked -> "Too many tries. Wait $seconds seconds."
}

@Composable
private fun GateButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GateCard(modifier.size(180.dp, 52.dp), onClick = onClick) { focused ->
        Text(text, style = LauncherText.Button, color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary)
    }
}

/** A focusable glass card that turns white when focused, like the launcher's menu rows. */
@Composable
private fun GateCard(modifier: Modifier, onClick: () -> Unit, content: @Composable (focused: Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radii.Item)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .remoteClickable(onClick)
            .clip(shape)
            .background(if (focused) LauncherColors.TextPrimary else Glass.Fill)
            .border(1.dp, if (focused) Glass.BorderFocused else Glass.Border, shape),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content(focused) }
    }
}
