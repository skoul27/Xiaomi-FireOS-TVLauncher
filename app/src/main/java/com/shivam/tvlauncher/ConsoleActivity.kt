package com.shivam.tvlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.tv.TvContract
import android.media.tv.TvView
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shivam.tvlauncher.ui.GateStep
import com.shivam.tvlauncher.ui.LauncherTheme
import com.shivam.tvlauncher.ui.PayGate
import com.shivam.tvlauncher.ui.PlayTimer
import com.shivam.tvlauncher.ui.TimeUpExitMillis
import com.shivam.tvlauncher.ui.message
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows one HDMI input full screen. Fire OS keeps its own input switching behind Amazon-only
 * permissions, but any app may tune a passthrough input in a TvView. Back returns to the launcher.
 *
 * A port set to pay to play ([PayToPlay]) shows a gate first and is tuned only while paid time
 * (or the admin code's unlimited play) lasts; when time runs out the picture goes and the gate
 * comes back.
 */
class ConsoleActivity : ComponentActivity() {
    private lateinit var tvView: TvView
    private lateinit var message: TextView
    private lateinit var gateView: ComposeView
    private lateinit var inputId: String
    private lateinit var name: String
    private lateinit var port: String
    private var tuned = false
    private var gated = false

    /** Null while playing (or on a free port); otherwise the gate's current screen. */
    private var step by mutableStateOf<GateStep?>(null)
    private var now by mutableLongStateOf(System.currentTimeMillis())
    private var unlimited by mutableStateOf(false)
    private var playingSince by mutableLongStateOf(0L)
    private var wasPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PayToPlay.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        inputId = intent.getStringExtra(EXTRA_INPUT_ID) ?: return finish()
        name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        port = intent.getStringExtra(EXTRA_PORT).orEmpty()
        gated = PayToPlay.isGated(inputId)
        tvView = TvView(this)
        message = TextView(this).apply {
            text = "No signal from $name on $port.\nTurn it on, or press Back."
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.jakarta_medium) }.getOrNull()
            // Shown only once the input reports no picture, so a good tune never flashes it.
            visibility = View.GONE
        }
        gateView = ComposeView(this).apply {
            setContent {
                LauncherTheme {
                    val current = step
                    if (current != null) {
                        PayGate(
                            consoleName = name,
                            step = current,
                            config = PayToPlay.config.value,
                            now = now,
                            onPlan = { plan -> step = GateStep.Paying(PayToPlay.request(inputId, name, plan)) },
                            onAdmin = { step = GateStep.Pin },
                            onPin = { code ->
                                val result = PayToPlay.checkPin(code)
                                if (result == PayToPlay.PinResult.Ok) {
                                    PayToPlay.unlockUnlimited(inputId)
                                    refresh()
                                }
                                result.message()
                            },
                            onPlans = { backToPlans() },
                            onExit = { finish() },
                        )
                    } else if (gated) {
                        PlayTimer(
                            leftMillis = PayToPlay.paidLeft(inputId, now),
                            unlimited = unlimited,
                            showBadge = now - playingSince < 6_000,
                        )
                    }
                }
            }
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(tvView)
                addView(message, FrameLayout.LayoutParams(-1, -1))
                addView(gateView, FrameLayout.LayoutParams(-1, -1))
            },
        )
        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String) {
                message.visibility = View.GONE
            }

            override fun onVideoUnavailable(inputId: String, reason: Int) {
                if (tuned) message.visibility = View.VISIBLE
            }

            override fun onConnectionFailed(inputId: String) = fail(name)
            override fun onDisconnected(inputId: String) = fail(name)
        })
        // Once a second: the clock, and whether the owner has confirmed, declined or ended play.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refresh()
                    delay(if (step is GateStep.Paying) 250 else 500)
                }
            }
        }
    }

    /** Brings the screen in line with the paid state: tune while play is allowed, gate otherwise. */
    private fun refresh() {
        now = System.currentTimeMillis()
        if (!gated) return tune()
        unlimited = inputId in PayToPlay.unlimited.value
        if (PayToPlay.mayPlay(inputId)) {
            if (!wasPlaying) playingSince = now
            wasPlaying = true
            step = null
            tune()
            return
        }
        untune()
        if (wasPlaying) {
            // The time ran out, or the owner ended it from the phone.
            wasPlaying = false
            step = GateStep.TimeUp(now)
        }
        when (val current = step) {
            null -> step = GateStep.Plans()
            is GateStep.Paying -> when {
                PayToPlay.pending.value != current.payment -> step = GateStep.Plans("The payment wasn't confirmed. Ask the owner, or try again.")
                now - current.payment.createdAt > PaymentTimeoutMillis -> {
                    PayToPlay.cancel(current.payment)
                    step = GateStep.Plans("That took too long. Choose again to get a new code.")
                }
            }
            is GateStep.TimeUp -> if (now - current.at > TimeUpExitMillis) finish()
            else -> {}
        }
    }

    private fun backToPlans() {
        (step as? GateStep.Paying)?.let { PayToPlay.cancel(it.payment) }
        step = GateStep.Plans()
    }

    private fun tune() {
        if (tuned) return
        tuned = true
        tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
        // Keys go to the TvView (and on over HDMI-CEC) while playing; the gate takes none.
        gateView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        gateView.isFocusable = false
        gateView.clearFocus()
        tvView.requestFocus()
    }

    private fun untune() {
        if (tuned) {
            tuned = false
            tvView.reset()
            message.visibility = View.GONE
        }
        // The gate's buttons need the gateView to hold the window's focus.
        gateView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        gateView.isFocusable = true
        if (!gateView.hasFocus()) gateView.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        ShortcutKeyService.recordFront(this, true)
    }

    override fun onPause() {
        ShortcutKeyService.recordFront(this, false)
        super.onPause()
    }

    // Releases the input as soon as we're covered, so it never keeps playing behind another app.
    // Paid time keeps running; admin play and a payment still waiting end here.
    override fun onStop() {
        if (::tvView.isInitialized) tvView.reset()
        tuned = false
        if (::inputId.isInitialized) {
            PayToPlay.endUnlimited(inputId)
            (step as? GateStep.Paying)?.let { PayToPlay.cancel(it.payment) }
        }
        super.onStop()
        finish()
    }

    /**
     * Back is ours before the TvView sees it: the HDMI device must never be able to swallow it.
     * On the gate's inner screens it goes back to the plans first.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                when (step) {
                    is GateStep.Paying, GateStep.Pin -> backToPlans()
                    else -> finish()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun fail(name: String) {
        Toast.makeText(this, "Can't show $name right now", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val EXTRA_INPUT_ID = "inputId"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_PORT = "port"

        /** A QR code left unpaid this long is dropped, so a stale one can't be confirmed later. */
        private const val PaymentTimeoutMillis = 10 * 60_000L

        fun intent(context: Context, console: Console): Intent =
            Intent(context, ConsoleActivity::class.java)
                .putExtra(EXTRA_INPUT_ID, console.inputId)
                .putExtra(EXTRA_NAME, console.name)
                .putExtra(EXTRA_PORT, console.port)
    }
}
