package com.shivam.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One price: [minutes] of play for [price] rupees. */
data class PayPlan(val minutes: Int, val price: Int) {
    val durationLabel: String get() = durationLabel(minutes)
}

fun durationLabel(minutes: Int): String = when {
    minutes % 60 == 0 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}

/**
 * Pay to play, set up from the owner's phone. [ports] are the TV input ids that ask for payment
 * before showing; [pin] is the admin code; [token] is the secret in the owner page's address,
 * made once per install. [guard] also keeps players out of Fire OS's own input switching and
 * settings while a port is paid (see [ShortcutKeyService]).
 */
data class PayConfig(
    val ports: Set<String> = emptySet(),
    val upiId: String = "",
    val payee: String = "",
    val plans: List<PayPlan> = DefaultPlans,
    val pin: String = "",
    val guard: Boolean = true,
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
    /** [SystemClock.elapsedRealtime] when it was made. */
    val createdAt: Long,
    /** Made by the owner from the phone (a custom amount or time), not picked on the TV. */
    val custom: Boolean = false,
)

/** A line in the owner page's history. [at] is wall-clock time, for showing only. */
data class PayEvent(val at: Long, val text: String, val amount: Int = 0)

/**
 * Who may watch a gated input right now. All timing is on [SystemClock.elapsedRealtime], so
 * changing the TV's date or time can't add play. Paid time is saved with how much was left, so
 * leaving the input (Back, Home) keeps the clock running, coming back within it needs no new
 * payment, and a reboot pauses it rather than losing it. The admin code's unlimited play is memory
 * only: it ends when the input is closed.
 */
object PayToPlay {
    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context
    private val random = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _config = MutableStateFlow(PayConfig())
    val config: StateFlow<PayConfig> = _config

    private val _paidUntil = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Elapsed-realtime end of paid time, per input id. */
    val paidUntil: StateFlow<Map<String, Long>> = _paidUntil

    private val _unlimited = MutableStateFlow<Set<String>>(emptySet())
    val unlimited: StateFlow<Set<String>> = _unlimited

    private val _pending = MutableStateFlow<PendingPayment?>(null)

    private val _log = MutableStateFlow<List<PayEvent>>(emptyList())
    val log: StateFlow<List<PayEvent>> = _log

    @Volatile
    private var adminUntil = 0L
    private var wrongPins = 0
    private var lockedUntil = 0L

    fun now(): Long = SystemClock.elapsedRealtime()

    /** Loads the saved setup once per process and starts the owner page if a port is gated. */
    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("pay_to_play", Context.MODE_PRIVATE)
        val token = prefs.getString(KeyToken, null) ?: newToken().also { prefs.edit().putString(KeyToken, it).apply() }
        _config.value = PayConfig(
            ports = prefs.getStringSet(KeyPorts, null).orEmpty().toSet(),
            upiId = prefs.getString(KeyUpi, null).orEmpty(),
            payee = prefs.getString(KeyPayee, null).orEmpty(),
            plans = prefs.getString(KeyPlans, null)?.let(::parsePlans) ?: PayConfig.DefaultPlans,
            pin = prefs.getString(KeyPin, null).orEmpty(),
            guard = prefs.getBoolean(KeyGuard, true),
            token = token,
        )
        loadSessions()
        _log.value = runCatching {
            val array = JSONArray(prefs.getString(KeyLog, "[]"))
            (0 until array.length()).map { i ->
                array.getJSONObject(i).let { PayEvent(it.getLong("at"), it.getString("t"), it.optInt("a")) }
            }
        }.getOrDefault(emptyList())
        // Saves how much is left now and then, so a reboot (or a pulled plug) loses at most this much.
        scope.launch {
            while (true) {
                delay(CheckpointMillis)
                if (_paidUntil.value.isNotEmpty()) saveSessions()
            }
        }
        PayServer.init(app)
        PayServer.sync()
    }

    fun isGated(inputId: String): Boolean = inputId in _config.value.ports

    /** Milliseconds of paid time left on [inputId]; 0 when none. */
    fun paidLeft(inputId: String, now: Long = now()): Long =
        ((_paidUntil.value[inputId] ?: 0L) - now).coerceAtLeast(0L)

    fun mayPlay(inputId: String): Boolean = inputId in _unlimited.value || paidLeft(inputId) > 0

    /** The payment waiting on the owner, if it hasn't gone stale. */
    fun pending(): PendingPayment? {
        val p = _pending.value ?: return null
        if (now() - p.createdAt <= PaymentTimeoutMillis) return p
        _pending.compareAndSet(p, null)
        return null
    }

    fun request(inputId: String, portName: String, plan: PayPlan, custom: Boolean = false): PendingPayment {
        val payment = PendingPayment(inputId, portName, plan, "%04d".format(random.nextInt(10_000)), now(), custom)
        _pending.value = payment
        return payment
    }

    /** Drops the waiting payment, if it's still [payment] (or any, when null). */
    fun cancel(payment: PendingPayment? = null) {
        _pending.update { if (payment == null || it == payment) null else it }
    }

    /** The owner's confirm: the waiting payment's time starts now. False if it's gone or changed. */
    fun confirm(ref: String): Boolean {
        val payment = pending()?.takeIf { it.ref == ref } ?: return false
        // Time first, then clear: the gate never sees "not waiting" without "paid".
        grant(payment.inputId, payment.plan.minutes, log = false)
        _pending.value = null
        record("Paid ₹${payment.plan.price} · ${payment.plan.durationLabel} · ${payment.portName}", payment.plan.price)
        return true
    }

    fun decline(ref: String): Boolean {
        val payment = pending()?.takeIf { it.ref == ref } ?: return false
        _pending.value = null
        record("Declined ₹${payment.plan.price} · ${payment.portName}")
        return true
    }

    /** Adds [minutes] to [inputId]'s paid time, from now if it had none left. */
    fun grant(inputId: String, minutes: Int, log: Boolean = true, portName: String? = null, cash: Int = 0) {
        val now = now()
        _paidUntil.update { it + (inputId to maxOf(now, it[inputId] ?: 0L) + minutes * 60_000L) }
        saveSessions()
        val where = portName?.let { " · $it" }.orEmpty()
        when {
            !log -> {}
            cash > 0 -> record("Cash ₹$cash · ${durationLabel(minutes)}$where", cash)
            else -> record("Gave ${durationLabel(minutes)}$where")
        }
    }

    /** Ends play on [inputId] now: paid time and admin unlimited both. */
    fun end(inputId: String, portName: String? = null) {
        val had = inputId in _paidUntil.value || inputId in _unlimited.value
        _paidUntil.update { it - inputId }
        _unlimited.update { it - inputId }
        saveSessions()
        if (had) record("Ended play${portName?.let { " · $it" }.orEmpty()}")
    }

    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val triesLeft: Int) : PinResult
        data class Locked(val seconds: Int) : PinResult
    }

    /** The admin code, with a lockout after [MaxWrongPins] wrong tries in a row. */
    @Synchronized
    fun checkPin(pin: String): PinResult {
        val now = now()
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
            record("Admin code: $MaxWrongPins wrong tries on the TV")
            return PinResult.Locked((LockoutMillis / 1000).toInt())
        }
        return PinResult.Wrong(MaxWrongPins - wrongPins)
    }

    fun unlockUnlimited(inputId: String, portName: String) {
        _unlimited.update { it + inputId }
        record("Admin play · $portName")
    }

    fun endUnlimited(inputId: String) = _unlimited.update { it - inputId }

    /**
     * True while players are kept out of the launcher's settings and Fire OS's settings and input
     * switching: a port is paid, the guard is on, and there's an admin code to get past it with.
     */
    val locked: Boolean
        get() = _config.value.let { it.guard && it.ports.isNotEmpty() && it.pin.isNotEmpty() } && now() >= adminUntil

    /** Lets the owner into settings for a while (admin code on the TV, or from the phone). */
    fun unlockAdmin(millis: Long = AdminWindowMillis) {
        adminUntil = now() + millis
    }

    fun lockAdmin() {
        adminUntil = 0L
    }

    fun adminLeft(): Long = (adminUntil - now()).coerceAtLeast(0L)

    fun updateConfig(transform: (PayConfig) -> PayConfig) {
        val next = transform(_config.value).copy(token = _config.value.token)
        _config.value = next
        prefs.edit()
            .putStringSet(KeyPorts, next.ports)
            .putString(KeyUpi, next.upiId)
            .putString(KeyPayee, next.payee)
            .putString(KeyPlans, formatPlans(next.plans))
            .putString(KeyPin, next.pin)
            .putBoolean(KeyGuard, next.guard)
            .apply()
        // A port no longer gated has nothing to wait for.
        _pending.update { p -> p?.takeIf { it.inputId in next.ports } }
        PayServer.sync()
    }

    /** Whether the accessibility service the guard (and the remote shortcut) runs in is on. */
    fun guardServiceOn(): Boolean {
        val ours = ComponentName(app, ShortcutKeyService::class.java)
        val enabled = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
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
        val minutes = m.toIntOrNull()?.takeIf { it in 1..MaxMinutes } ?: return@mapNotNull null
        val price = p.toIntOrNull()?.takeIf { it in 1..MaxPrice } ?: return@mapNotNull null
        PayPlan(minutes, price)
    }.distinctBy { it.minutes }.sortedBy { it.minutes }.take(MaxPlans)

    fun formatPlans(plans: List<PayPlan>): String = plans.joinToString(", ") { "${it.minutes}:${it.price}" }

    private fun record(text: String, amount: Int = 0) {
        _log.update { (listOf(PayEvent(System.currentTimeMillis(), text, amount)) + it).take(MaxLog) }
        val array = JSONArray(_log.value.map { JSONObject().put("at", it.at).put("t", it.text).put("a", it.amount) })
        prefs.edit().putString(KeyLog, array.toString()).apply()
    }

    private fun bootCount(): Int = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** One "input id, ms left, elapsed at save, boot" line per port with time left. */
    @Synchronized
    private fun saveSessions() {
        val now = now()
        val boot = bootCount()
        prefs.edit().putString(
            KeySessions,
            _paidUntil.value.mapNotNull { (id, until) ->
                (until - now).takeIf { it > 0 }?.let { "$id\t$it\t$now\t$boot" }
            }.joinToString("\n"),
        ).apply()
    }

    /** Same boot: the end time carries on. After a reboot: what was left at the last save, from now. */
    private fun loadSessions() {
        val now = now()
        val boot = bootCount()
        _paidUntil.value = prefs.getString(KeySessions, null).orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t').takeIf { it.size == 4 } ?: return@mapNotNull null
            val left = parts[1].toLongOrNull() ?: return@mapNotNull null
            val savedAt = parts[2].toLongOrNull() ?: return@mapNotNull null
            val sameBoot = boot >= 0 && parts[3].toIntOrNull() == boot
            val end = if (sameBoot) savedAt + left else now + left
            end.takeIf { it > now }?.let { parts[0] to it }
        }.toMap()
    }

    private fun newToken(): String = ByteArray(12).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    const val MaxPlans = 4
    const val MaxMinutes = 24 * 60
    const val MaxPrice = 100_000
    /** A QR code left unpaid this long is dropped, so a stale one can't be confirmed later. */
    const val PaymentTimeoutMillis = 10 * 60_000L
    const val AdminWindowMillis = 10 * 60_000L
    private const val CheckpointMillis = 30_000L
    private const val MaxWrongPins = 5
    private const val LockoutMillis = 60_000L
    private const val MaxLog = 60
    private const val KeyToken = "token"
    private const val KeyPorts = "ports"
    private const val KeyUpi = "upi"
    private const val KeyPayee = "payee"
    private const val KeyPlans = "plans"
    private const val KeyPin = "pin"
    private const val KeyGuard = "guard"
    private const val KeySessions = "sessions_v2"
    private const val KeyLog = "log"
}
