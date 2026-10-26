package com.shivam.tvlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.tv.TvContract
import android.media.tv.TvView
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat

/**
 * Shows one HDMI input full screen. Fire OS keeps its own input switching behind Amazon-only
 * permissions, but any app may tune a passthrough input in a TvView. Back returns to the launcher.
 */
class ConsoleActivity : Activity() {
    private lateinit var tvView: TvView
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        tvView = TvView(this)
        message = TextView(this).apply {
            text = "No signal from $name on ${intent.getStringExtra(EXTRA_PORT).orEmpty()}.\nTurn it on, or press Back."
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.jakarta_medium) }.getOrNull()
            // Shown only once the input reports no picture, so a good tune never flashes it.
            visibility = View.GONE
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(tvView)
                addView(message, FrameLayout.LayoutParams(-1, -1))
            },
        )
        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String) {
                message.visibility = View.GONE
            }

            override fun onVideoUnavailable(inputId: String, reason: Int) {
                message.visibility = View.VISIBLE
            }

            override fun onConnectionFailed(inputId: String) = fail(name)
            override fun onDisconnected(inputId: String) = fail(name)
        })
    }

    override fun onStart() {
        super.onStart()
        val inputId = intent.getStringExtra(EXTRA_INPUT_ID) ?: return finish()
        tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
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
    override fun onStop() {
        tvView.reset()
        super.onStop()
        finish()
    }

    /** Back is ours before the TvView sees it: the HDMI device must never be able to swallow it. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) finish()
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

        fun intent(context: Context, console: Console): Intent =
            Intent(context, ConsoleActivity::class.java)
                .putExtra(EXTRA_INPUT_ID, console.inputId)
                .putExtra(EXTRA_NAME, console.name)
                .putExtra(EXTRA_PORT, console.port)
    }
}
