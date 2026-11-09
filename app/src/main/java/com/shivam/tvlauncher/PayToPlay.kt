package com.shivam.tvlauncher

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One price on the gate: [minutes] of play for [price] rupees. */
data class PayPlan(val minutes: Int, val price: Int) {
    val durationLabel: String
        get() = when {
            minutes % 60 == 0 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
            minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
            else -> "$minutes min"
        }
}

/**
 * Pay to play, set up from the owner's phone. [ports] are the TV input ids that ask for payment
 * before showing; [pin] is the admin code that skips it; [token] is the secret in the owner page's
 * address, made once per install.
 */
data class PayConfig(
    val ports: Set<String> = emptySet(),
    val upiId: String = "",
    val payee: String = "",
    val plans: List<PayPlan> = DefaultPlans,
    val pin: String = "",
    val token: String = "",
) {
    /** Enough to take a payment: somewhere to send it and something to buy. */
    val canTakePayment: Boolean get() = upiId.isNotBlank() && plans.isNotEmpty()

    companion object {
        val DefaultPlans = listOf(PayPlan(30, 50), PayPlan(60, 90), PayPlan(120, 160))
    }
}

/** A payment the TV is showing a QR code for, waiting on the owner to confirm it. */
data class PendingPayment(
    val inputId: String,
    val portName: String,
    val plan: PayPlan,
    /** Four digits shown on the TV and put in the UPI note, so the owner can match the payment. */
    val ref: String,
    val createdAt: Long,
)

/**
 * Who may watch a gated input right now. Paid time is saved with its end time, so leaving the
 * input (Back, Home) keeps the clock running and coming back within it needs no new payment.
 * The admin code's unlimited play is memory only: it ends when the input is closed.
 */
object PayToPlay {
    private lateinit var prefs: SharedPreferences
    private val random = SecureRandom()

    private val _config = MutableStateFlow(PayConfig())
    val config: StateFlow<PayConfig> = _config

    private val _paidUntil = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Wall-clock end of paid time, per input id. */
    val paidUntil: StateFlow<Map<String, Long>> = _paidUntil

    private val _unlimited = MutableStateFlow<Set<String>>(emptySet())
    val unlimited: StateFlow<Set<String>> = _unlimited

    private val _pending = MutableStateFlow<PendingPayment?>(null)
    val pending: StateFlow<PendingPayment?> = _pending

    private var wrongPins = 0
    private var lockedUntil = 0L

    /** Loads the saved setup once per process and starts the owner page if a port is gated. */
    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences("pay_to_play", Context.MODE_PRIVATE)
        val token = prefs.getString(KeyToken, null) ?: newToken().also { prefs.edit().putString(KeyToken, it).apply() }
        _config.value = PayConfig(
            ports = prefs.getStringSet(KeyPorts, null).orEmpty().toSet(),
            upiId = prefs.getString(KeyUpi, null).orEmpty(),
            payee = prefs.getString(KeyPayee, null).orEmpty(),
            plans = prefs.getString(KeyPlans, null)?.let(::parsePlans) ?: PayConfig.DefaultPlans,
            pin = prefs.getString(KeyPin, null).orEmpty(),
            token = token,
        )
        val now = System.currentTimeMillis()
        _paidUntil.value = prefs.getString(KeySessions, null).orEmpty().lines().mapNotNull { line ->
            val (id, until) = line.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
            until.toLongOrNull()?.takeIf { it > now }?.let { id to it }
        }.toMap()
        PayServer.init(app)
        PayServer.sync()
    }

    fun isGated(inputId: String): Boolean = inputId in _config.value.ports

    /** Milliseconds of paid time left on [inputId]; 0 when none. */
    fun paidLeft(inputId: String, now: Long = System.currentTimeMillis()): Long =
        ((_paidUntil.value[inputId] ?: 0L) - now).coerceAtLeast(0L)

    fun mayPlay(inputId: String): Boolean = inputId in _unlimited.value || paidLeft(inputId) > 0

    fun request(inputId: String, portName: String, plan: PayPlan): PendingPayment {
        val payment = PendingPayment(inputId, portName, plan, "%04d".format(random.nextInt(10_000)), System.currentTimeMillis())
        _pending.value = payment
        return payment
    }

    /** Drops the waiting payment, if it's still [payment] (or any, when null). */
    fun cancel(payment: PendingPayment? = null) {
        _pending.update { if (payment == null || it == payment) null else it }
    }

    /** The owner's confirm: the waiting payment's time starts now. False if it's gone or changed. */
    fun confirm(ref: String): Boolean {
        val payment = _pending.value?.takeIf { it.ref == ref } ?: return false
        // Time first, then clear: the gate never sees "not waiting" without "paid".
        grant(payment.inputId, payment.plan.minutes)
        _pending.value = null
        return true
    }

    fun decline(ref: String): Boolean {
        if (_pending.value?.ref != ref) return false
        _pending.value = null
        return true
    }

    /** Adds [minutes] to [inputId]'s paid time, from now if it had none left. */
    fun grant(inputId: String, minutes: Int) {
        val now = System.currentTimeMillis()
        _paidUntil.update { it + (inputId to maxOf(now, it[inputId] ?: 0L) + minutes * 60_000L) }
        saveSessions()
    }

    /** Ends play on [inputId] now: paid time and admin unlimited both. */
    fun end(inputId: String) {
        _paidUntil.update { it - inputId }
        _unlimited.update { it - inputId }
        saveSessions()
    }

    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val triesLeft: Int) : PinResult
        data class Locked(val seconds: Int) : PinResult
    }

    /** The admin code: right, and [inputId] plays without limit until it's closed. */
    @Synchronized
    fun checkPin(pin: String): PinResult {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return PinResult.Locked(((lockedUntil - now) / 1000 + 1).toInt())
        val stored = _config.value.pin
        if (stored.isNotEmpty() && pin == stored) {
            wrongPins = 0
            return PinResult.Ok
        }
        wrongPins++
        if (wrongPins >= MaxWrongPins) {
            wrongPins = 0
            lockedUntil = now + LockoutMillis
            return PinResult.Locked((LockoutMillis / 1000).toInt())
        }
        return PinResult.Wrong(MaxWrongPins - wrongPins)
    }

    fun unlockUnlimited(inputId: String) = _unlimited.update { it + inputId }

    fun endUnlimited(inputId: String) = _unlimited.update { it - inputId }

    fun updateConfig(transform: (PayConfig) -> PayConfig) {
        val next = transform(_config.value).let { it.copy(token = _config.value.token) }
        _config.value = next
        prefs.edit()
            .putStringSet(KeyPorts, next.ports)
            .putString(KeyUpi, next.upiId)
            .putString(KeyPayee, next.payee)
            .putString(KeyPlans, formatPlans(next.plans))
            .putString(KeyPin, next.pin)
            .apply()
        // A port no longer gated has nothing to wait for.
        _pending.update { p -> p?.takeIf { it.inputId in next.ports } }
        PayServer.sync()
    }

    /** A UPI payment link (NPCI's deep-link format); any UPI app opens it from the QR code. */
    fun upiLink(config: PayConfig, payment: PendingPayment): String = Uri.Builder()
        .scheme("upi").authority("pay")
        .appendQueryParameter("pa", config.upiId.trim())
        .apply { if (config.payee.isNotBlank()) appendQueryParameter("pn", config.payee.trim()) }
        .appendQueryParameter("am", "${payment.plan.price}.00")
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", "${payment.portName} ${payment.plan.durationLabel} ref ${payment.ref}")
        .build().toString()

    /** "30:50, 60:90": minutes:rupees pairs. Bad entries are skipped. */
    fun parsePlans(text: String): List<PayPlan> = text.split(',', '\n', ';').mapNotNull { part ->
        val (m, p) = part.split(':', '=').map { it.trim() }.takeIf { it.size == 2 } ?: return@mapNotNull null
        val minutes = m.toIntOrNull()?.takeIf { it in 1..24 * 60 } ?: return@mapNotNull null
        val price = p.toIntOrNull()?.takeIf { it in 1..100_000 } ?: return@mapNotNull null
        PayPlan(minutes, price)
    }.distinctBy { it.minutes }.sortedBy { it.minutes }.take(MaxPlans)

    fun formatPlans(plans: List<PayPlan>): String = plans.joinToString(", ") { "${it.minutes}:${it.price}" }

    private fun saveSessions() {
        val now = System.currentTimeMillis()
        prefs.edit().putString(
            KeySessions,
            _paidUntil.value.filterValues { it > now }.entries.joinToString("\n") { (id, until) -> "$id\t$until" },
        ).apply()
    }

    private fun newToken(): String = ByteArray(12).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    const val MaxPlans = 4
    private const val MaxWrongPins = 5
    private const val LockoutMillis = 60_000L
    private const val KeyToken = "token"
    private const val KeyPorts = "ports"
    private const val KeyUpi = "upi"
    private const val KeyPayee = "payee"
    private const val KeyPlans = "plans"
    private const val KeyPin = "pin"
    private const val KeySessions = "sessions"
}
