package com.shivam.tvlauncher

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The owner's app for pay to play, a web page served on the home network: confirm or decline a
 * payment the TV is waiting on, charge a custom amount, give or end time, see the day's takings,
 * unlock the TV's settings, and set prices, UPI ID, admin code and which ports are paid. It runs
 * while any port is paid, or while the TV's Pay to play panel is open (so it can be set up the
 * first time). Only requests carrying the secret token in the path are answered.
 */
object PayServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context
    private var socket: ServerSocket? = null
    private var job: Job? = null
    private var holds = 0

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** Keeps the page up while the TV's settings panel shows its address. */
    @Synchronized
    fun hold() {
        holds++
        sync()
    }

    @Synchronized
    fun release() {
        holds = (holds - 1).coerceAtLeast(0)
        sync()
    }

    /** Starts or stops the server to match the setup. */
    @Synchronized
    fun sync() {
        if (!::app.isInitialized) return
        val wanted = holds > 0 || PayToPlay.config.value.ports.isNotEmpty()
        if (wanted && socket == null) start() else if (!wanted && socket != null) stop()
    }

    /** The page's address on this network, or null when the TV has none or the port is taken. */
    fun ownerUrl(): String? {
        val port = socket?.localPort ?: return null
        val address = lanAddress(app) ?: return null
        return "http://$address:$port/${PayToPlay.config.value.token}"
    }

    private fun start() {
        // A fixed port, so the address saved on the owner's phone keeps working.
        val server = runCatching { ServerSocket(Port) }.getOrNull() ?: return
        socket = server
        job = scope.launch {
            while (isActive) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                launch { client.use { runCatching { handle(it) } } }
            }
        }
    }

    private fun stop() {
        runCatching { socket?.close() }
        job?.cancel()
        socket = null
        job = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = client.getInputStream()
        val output = client.getOutputStream()
        val head = readHead(input) ?: return
        val (method, target) = head.lines().first().split(" ").let { it.getOrNull(0) to it.getOrNull(1) }
        val path = target?.substringBefore('?').orEmpty()
        val base = "/${PayToPlay.config.value.token}"
        when {
            method == "GET" && (path == base || path == "$base/") -> output.respond(200, "text/html; charset=utf-8", OwnerPage)
            method == "GET" && path == "$base/manifest.json" -> output.respond(200, "application/manifest+json", manifest(base))
            method == "GET" && path == "$base/icon.svg" -> output.respond(200, "image/svg+xml", Icon)
            method == "GET" && path == "$base/state" -> output.respond(200, "application/json", state().toString())
            method == "POST" && path == "$base/do" -> {
                val length = head.lines()
                    .firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull()
                if (length == null || length < 0 || length > MaxBody) return output.respond(413, "text/plain", "Too large")
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n < 0) return
                    read += n
                }
                val error = act(parseForm(String(body, Charsets.UTF_8)))
                if (error == null) {
                    output.respond(200, "application/json", state().toString())
                } else {
                    output.respond(400, "application/json", JSONObject().put("error", error).toString())
                }
            }
            else -> output.respond(404, "text/plain", "Not found")
        }
    }

    private fun portNames(): Map<String, String> = hdmiPorts(app).associate { it.inputId to portName(it) }

    private fun portName(port: HdmiPort) = port.device?.let { "${port.label} · $it" } ?: port.label

    /** Carries out one action from the page; returns why it couldn't, or null when done. */
    private fun act(form: Map<String, List<String>>): String? {
        fun one(name: String) = form[name]?.firstOrNull().orEmpty().trim()
        fun minutes() = one("m").toIntOrNull()?.takeIf { it in 1..PayToPlay.MaxMinutes }
        fun price() = one("p").toIntOrNull()?.takeIf { it in 1..PayToPlay.MaxPrice }
        val names = portNames()
        return when (one("a")) {
            "confirm" -> if (PayToPlay.confirm(one("ref"))) null else "That payment is no longer waiting."
            "decline" -> if (PayToPlay.decline(one("ref"))) null else "That payment is no longer waiting."
            "grant" -> {
                val m = minutes() ?: return "Enter a time from 1 minute to 24 hours."
                val id = one("id").takeIf { it in names } ?: return "Pick a port."
                val cash = one("p").toIntOrNull()?.coerceIn(0, PayToPlay.MaxPrice) ?: 0
                PayToPlay.grant(id, m, portName = names[id], cash = cash)
                null
            }
            "charge" -> {
                val m = minutes() ?: return "Enter a time from 1 minute to 24 hours."
                val p = price() ?: return "Enter an amount from ₹1."
                val id = one("id").takeIf { it in names } ?: return "Pick a port."
                if (PayToPlay.config.value.upiId.isBlank()) return "Add your UPI ID in Setup first."
                if (id !in PayToPlay.config.value.ports) return "That port isn't paid. Turn it on in Setup."
                PayToPlay.request(id, hdmiPorts(app).first { it.inputId == id }.label, PayPlan(m, p), custom = true)
                null
            }
            "cancel" -> {
                PayToPlay.cancel()
                null
            }
            "end" -> {
                PayToPlay.end(one("id"), names[one("id")])
                null
            }
            "unlock" -> {
                PayToPlay.unlockAdmin()
                null
            }
            "lock" -> {
                PayToPlay.lockAdmin()
                null
            }
            "settings" -> {
                val plans = PayToPlay.parsePlans(one("plans"))
                if (plans.isEmpty()) return "Add at least one price."
                val pin = one("pin")
                if (pin.isNotEmpty() && (pin.length !in 4..8 || !pin.all(Char::isDigit))) return "The admin code is 4 to 8 digits."
                val upi = one("upi")
                if (upi.isNotEmpty() && !UpiId.matches(upi)) return "That doesn't look like a UPI ID (name@bank)."
                PayToPlay.updateConfig { config ->
                    config.copy(
                        ports = form["ports"].orEmpty().filter { it in names }.toSet(),
                        upiId = upi,
                        payee = one("payee").filterNot { it.isISOControl() }.take(40),
                        plans = plans,
                        // Blank keeps the current code, so the page never has to show it.
                        pin = pin.ifEmpty { config.pin },
                        guard = one("guard") == "1",
                    )
                }
                null
            }
            else -> "Unknown action."
        }
    }

    private fun state(): JSONObject {
        val config = PayToPlay.config.value
        val now = PayToPlay.now()
        val ports = hdmiPorts(app)
        val names = ports.associate { it.inputId to portName(it) }
        val unlimited = PayToPlay.unlimited.value
        val playing = (PayToPlay.paidUntil.value.filterValues { it > now }.keys + unlimited).distinct()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val log = PayToPlay.log.value
        return JSONObject()
            .put("ports", JSONArray(ports.map {
                JSONObject().put("id", it.inputId).put("name", names[it.inputId]).put("label", it.label)
                    .put("device", it.device ?: "").put("paid", it.inputId in config.ports)
            }))
            .put("upi", config.upiId)
            .put("payee", config.payee)
            .put("plans", JSONArray(config.plans.map { JSONObject().put("m", it.minutes).put("p", it.price).put("label", it.durationLabel) }))
            .put("maxPlans", PayToPlay.MaxPlans)
            .put("pinSet", config.pin.isNotEmpty())
            .put("guard", config.guard)
            .put("guardOn", runCatching { PayToPlay.guardServiceOn() }.getOrDefault(false))
            .put("locked", PayToPlay.locked)
            .put("adminLeft", PayToPlay.adminLeft() / 1000)
            .put("pending", PayToPlay.pending()?.let {
                JSONObject().put("ref", it.ref).put("id", it.inputId).put("port", names[it.inputId] ?: it.portName).put("label", it.plan.durationLabel)
                    .put("price", it.plan.price).put("custom", it.custom)
                    .put("expires", (PayToPlay.PaymentTimeoutMillis - (now - it.createdAt)).coerceAtLeast(0) / 1000)
            } ?: JSONObject.NULL)
            .put("sessions", JSONArray(playing.map { id ->
                JSONObject().put("id", id).put("name", names[id] ?: id).put("unlimited", id in unlimited)
                    .put("left", PayToPlay.paidLeft(id, now) / 1000)
            }))
            .put("today", log.filter { it.at >= midnight }.sumOf { it.amount })
            .put("todayCount", log.count { it.at >= midnight && it.amount > 0 })
            .put("log", JSONArray(log.take(40).map { JSONObject().put("at", it.at).put("t", it.text).put("a", it.amount) }))
    }

    private fun manifest(base: String) = JSONObject()
        .put("name", "Pay to play")
        .put("short_name", "Pay to play")
        .put("start_url", base)
        .put("scope", "$base/")
        .put("display", "standalone")
        .put("background_color", "#0E1015")
        .put("theme_color", "#0E1015")
        .put("icons", JSONArray().put(JSONObject().put("src", "$base/icon.svg").put("sizes", "any").put("type", "image/svg+xml")))
        .toString()

    private fun parseForm(body: String): Map<String, List<String>> = body.split('&').filter { it.isNotEmpty() }.map { pair ->
        val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
        val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
        key to value
    }.groupBy({ it.first }, { it.second })

    private fun readHead(input: InputStream): String? {
        val bytes = StringBuilder()
        while (bytes.length < 16_384) {
            val b = input.read()
            if (b < 0) return null
            bytes.append(b.toChar())
            if (bytes.endsWith("\r\n\r\n")) return bytes.toString()
        }
        return null
    }

    private fun OutputStream.respond(code: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        val reason = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 413 -> "Payload Too Large"; else -> "Error" }
        write("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        write(bytes)
        flush()
    }

    private const val Port = 8766
    private const val MaxBody = 16_384
    private val UpiId = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}$")
}

private val Icon = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96">
<rect width="96" height="96" rx="22" fill="#0E1015"/>
<rect x="18" y="34" width="60" height="34" rx="17" fill="#7BD389"/>
<circle cx="36" cy="51" r="4" fill="#0E1015"/><rect x="33" y="44" width="6" height="14" rx="2" fill="#0E1015" transform="rotate(90 36 51)"/>
<circle cx="60" cy="47" r="3.5" fill="#0E1015"/><circle cx="66" cy="54" r="3.5" fill="#0E1015"/>
</svg>
""".trimIndent()

/**
 * The phone's side, an app-like page with three tabs. Live: a waiting payment first (big, with
 * Confirm and Decline), then each paid port with its countdown and quick actions, and a sheet for
 * custom charges and time. History: the day's takings and recent events. Setup: ports, UPI ID,
 * prices, admin code, the lock. Polls the TV every 1.5 s and counts down locally in between.
 * No `$` anywhere below: this is a Kotlin raw string.
 */
private val OwnerPage = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#0E1015">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<meta name="apple-mobile-web-app-title" content="Pay to play">
<link rel="manifest" href="manifest.json">
<link rel="icon" href="icon.svg">
<link rel="apple-touch-icon" href="icon.svg">
<title>Pay to play</title>
<style>
  :root {
    color-scheme: dark;
    --bg: #0E1015; --card: #171A21; --card2: #1E222B; --line: #2A2F3A;
    --text: #F2F3F5; --muted: #9BA1AD; --faint: #6B7280;
    --green: #7BD389; --green-ink: #0B2210; --red: #FF8A80; --amber: #FFC46B; --blue: #A9C9FF;
    --r: 18px;
  }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  html, body { margin: 0; background: var(--bg); color: var(--text);
    font: 16px/1.4 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  body { padding-bottom: calc(76px + env(safe-area-inset-bottom)); }
  .wrap { max-width: 520px; margin: 0 auto; padding: 0 16px; }
  header { position: sticky; top: 0; z-index: 5; background: rgba(14,16,21,.92); backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px); padding: calc(10px + env(safe-area-inset-top)) 0 10px; border-bottom: 1px solid var(--line); }
  header .wrap { display: flex; align-items: center; gap: 12px; }
  header h1 { font-size: 20px; margin: 0; letter-spacing: -.01em; }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--faint); display: inline-block; margin-right: 6px; }
  .dot.on { background: var(--green); box-shadow: 0 0 0 4px rgba(123,211,137,.15); }
  .dot.off { background: var(--red); }
  .conn { font-size: 13px; color: var(--muted); }
  .grow { flex: 1; }
  .today { text-align: right; }
  .today b { display: block; font-size: 18px; }
  .today span { font-size: 12px; color: var(--muted); }
  section { display: none; padding-top: 16px; }
  section.show { display: block; }
  h2 { font-size: 13px; text-transform: uppercase; letter-spacing: .08em; color: var(--muted); margin: 22px 4px 10px; font-weight: 600; }
  .card { background: var(--card); border: 1px solid var(--line); border-radius: var(--r); padding: 16px; margin-bottom: 12px; }
  .muted { color: var(--muted); }
  .small { font-size: 13px; }
  .notice { border-radius: 14px; padding: 12px 14px; margin-bottom: 10px; font-size: 14px; background: rgba(255,196,107,.1);
    border: 1px solid rgba(255,196,107,.35); color: var(--amber); }
  .pending { border: 2px solid var(--green); background: linear-gradient(180deg, rgba(123,211,137,.14), rgba(123,211,137,.03));
    animation: pulse 1.8s ease-in-out infinite; }
  @keyframes pulse { 50% { box-shadow: 0 0 0 6px rgba(123,211,137,.12); } }
  .amount { font-size: 44px; font-weight: 800; letter-spacing: -.02em; line-height: 1.05; }
  .meta { display: flex; flex-wrap: wrap; gap: 6px 14px; margin-top: 6px; color: var(--muted); font-size: 14px; }
  .meta b { color: var(--text); font-variant-numeric: tabular-nums; }
  .ref { font-variant-numeric: tabular-nums; letter-spacing: .12em; font-weight: 800; color: var(--text); }
  .row { display: flex; gap: 10px; margin-top: 14px; }
  .row.wrap { flex-wrap: wrap; }
  button { appearance: none; border: 0; border-radius: 14px; padding: 13px 14px; font: inherit; font-weight: 650;
    background: var(--card2); color: var(--text); cursor: pointer; flex: 1; min-height: 48px; transition: transform .08s, opacity .2s; }
  button:active { transform: scale(.97); }
  button:disabled { opacity: .45; }
  button.primary { background: var(--green); color: var(--green-ink); }
  button.danger { background: transparent; color: var(--red); border: 1.5px solid rgba(255,138,128,.5); }
  button.ghost { background: transparent; border: 1.5px solid var(--line); }
  button.big { min-height: 56px; font-size: 17px; }
  .port { display: flex; align-items: center; gap: 12px; }
  .port .name { font-weight: 700; }
  .badge { font-size: 12px; font-weight: 700; padding: 3px 9px; border-radius: 99px; background: var(--card2); color: var(--muted); }
  .badge.play { background: rgba(123,211,137,.15); color: var(--green); }
  .badge.wait { background: rgba(255,196,107,.15); color: var(--amber); }
  .badge.admin { background: rgba(169,201,255,.15); color: var(--blue); }
  .clock { font-size: 40px; font-weight: 800; font-variant-numeric: tabular-nums; letter-spacing: -.02em; margin-top: 8px; }
  .clock.amber { color: var(--amber); } .clock.red { color: var(--red); }
  .bar { height: 6px; border-radius: 6px; background: var(--card2); margin-top: 10px; overflow: hidden; }
  .bar i { display: block; height: 100%; background: var(--green); border-radius: 6px; transition: width 1s linear; }
  .chips { display: flex; flex-wrap: wrap; gap: 8px; }
  .chip { flex: 0 0 auto; min-height: 40px; padding: 8px 14px; border-radius: 99px; background: var(--card2); border: 1.5px solid transparent; font-weight: 600; }
  .chip.on { border-color: var(--green); color: var(--green); background: rgba(123,211,137,.08); }
  label.f { display: block; font-size: 13px; color: var(--muted); margin: 14px 2px 6px; font-weight: 600; }
  input[type=text], input[type=number], input[type=password], input[type=tel] {
    width: 100%; padding: 13px 14px; border-radius: 12px; border: 1.5px solid var(--line); background: var(--bg);
    color: var(--text); font: inherit; outline: none; }
  input:focus { border-color: var(--blue); }
  .pair { display: flex; gap: 10px; }
  .pair > div { flex: 1; }
  .prefix { position: relative; }
  .prefix span { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); color: var(--muted); }
  .prefix input { padding-left: 30px; }
  .suffix { position: relative; }
  .suffix span { position: absolute; right: 14px; top: 50%; transform: translateY(-50%); color: var(--muted); font-size: 14px; }
  .switch { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-top: 1px solid var(--line); }
  .switch:first-child { border-top: 0; }
  .switch .grow div:first-child { font-weight: 600; }
  .tog { position: relative; width: 50px; height: 30px; flex: 0 0 auto; }
  .tog input { opacity: 0; width: 0; height: 0; position: absolute; }
  .tog i { position: absolute; inset: 0; border-radius: 99px; background: var(--card2); border: 1.5px solid var(--line); transition: .2s; }
  .tog i::after { content: ""; position: absolute; width: 22px; height: 22px; border-radius: 50%; top: 2.5px; left: 3px; background: var(--muted); transition: .2s; }
  .tog input:checked + i { background: var(--green); border-color: var(--green); }
  .tog input:checked + i::after { left: 23px; background: var(--green-ink); }
  .plan { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
  .plan > div { flex: 1; }
  .plan button { flex: 0 0 48px; padding: 0; background: transparent; border: 1.5px solid var(--line); color: var(--muted); }
  .log { list-style: none; margin: 0; padding: 0; }
  .log li { display: flex; gap: 12px; padding: 12px 2px; border-top: 1px solid var(--line); font-size: 15px; }
  .log li:first-child { border-top: 0; }
  .log time { color: var(--faint); font-variant-numeric: tabular-nums; flex: 0 0 52px; }
  .log b { color: var(--green); margin-left: auto; }
  .stat { display: flex; gap: 12px; }
  .stat > div { flex: 1; }
  .stat b { display: block; font-size: 30px; font-weight: 800; }
  nav { position: fixed; left: 0; right: 0; bottom: 0; z-index: 6; background: rgba(14,16,21,.95); backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px); border-top: 1px solid var(--line); padding-bottom: env(safe-area-inset-bottom); }
  nav .wrap { display: flex; }
  nav button { background: none; border-radius: 0; color: var(--faint); font-size: 12px; font-weight: 600; padding: 8px 0 10px; min-height: 60px; }
  nav button svg { display: block; margin: 0 auto 3px; width: 24px; height: 24px; fill: currentColor; }
  nav button.on { color: var(--text); }
  nav .pip { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: var(--green); margin-left: 4px; vertical-align: 2px; }
  .sheet-bg { position: fixed; inset: 0; background: rgba(0,0,0,.55); z-index: 9; opacity: 0; pointer-events: none; transition: opacity .2s; }
  .sheet-bg.show { opacity: 1; pointer-events: auto; }
  .sheet { position: fixed; left: 0; right: 0; bottom: 0; z-index: 10; background: var(--card); border-radius: 22px 22px 0 0;
    border-top: 1px solid var(--line); transform: translateY(105%); transition: transform .25s cubic-bezier(.2,.8,.2,1);
    padding: 8px 0 calc(16px + env(safe-area-inset-bottom)); max-height: 88vh; overflow-y: auto; }
  .sheet.show { transform: none; }
  .grab { width: 40px; height: 5px; border-radius: 5px; background: var(--line); margin: 4px auto 10px; }
  .sheet h3 { margin: 0 0 4px; font-size: 20px; }
  .toast { position: fixed; left: 50%; bottom: calc(90px + env(safe-area-inset-bottom)); transform: translate(-50%, 20px); z-index: 20;
    background: var(--text); color: var(--bg); padding: 12px 18px; border-radius: 14px; font-weight: 600; font-size: 15px;
    opacity: 0; transition: .25s; pointer-events: none; max-width: 90vw; text-align: center; }
  .toast.show { opacity: 1; transform: translate(-50%, 0); }
  .toast.err { background: var(--red); }
  .empty { text-align: center; padding: 28px 16px; color: var(--muted); }
</style>
</head>
<body>
<header><div class="wrap">
  <div class="grow">
    <h1>Pay to play</h1>
    <div class="conn"><span class="dot" id="dot"></span><span id="conn">Connecting…</span></div>
  </div>
  <div class="today"><b id="today">₹0</b><span>today</span></div>
</div></header>

<div class="wrap">
  <section id="tab-live" class="show">
    <div id="notices"></div>
    <div id="pending"></div>
    <div id="ports"></div>
    <h2>TV settings</h2>
    <div class="card">
      <div class="port"><div class="grow"><div class="name" id="lockTitle">Settings</div><div class="muted small" id="lockText"></div></div></div>
      <div class="row" id="lockRow"></div>
    </div>
    <div class="row"><button class="ghost" id="soundBtn">🔔 Turn on payment alerts</button></div>
  </section>

  <section id="tab-history">
    <div class="card stat">
      <div><span class="muted small">Taken today</span><b id="hToday">₹0</b></div>
      <div><span class="muted small">Payments today</span><b id="hCount">0</b></div>
    </div>
    <div class="card"><ul class="log" id="log"></ul></div>
  </section>

  <section id="tab-setup">
    <h2>Paid ports</h2>
    <div class="card" id="setPorts"></div>
    <h2>Payment</h2>
    <div class="card">
      <label class="f" for="upi" style="margin-top:0">Your UPI ID</label>
      <input type="text" id="upi" placeholder="name@okaxis" autocomplete="off" autocapitalize="off" spellcheck="false">
      <label class="f" for="payee">Name shown in the payment app</label>
      <input type="text" id="payee" placeholder="Optional" maxlength="40">
    </div>
    <h2>Prices on the TV</h2>
    <div class="card">
      <div id="plans"></div>
      <button class="ghost" id="addPlan" style="width:100%">+ Add a price</button>
    </div>
    <h2>Security</h2>
    <div class="card">
      <label class="f" for="pin" style="margin-top:0">Admin code (4–8 digits)</label>
      <input type="password" id="pin" inputmode="numeric" pattern="[0-9]*" maxlength="8" autocomplete="new-password">
      <div class="muted small" id="pinNote" style="margin-top:6px"></div>
      <div class="switch" style="margin-top:12px">
        <div class="grow"><div>Lock Fire TV's inputs and settings</div>
          <div class="muted small">Stops players reaching a paid port through Alexa, the Inputs menu or settings. The admin code unlocks for 10 minutes.</div></div>
        <label class="tog"><input type="checkbox" id="guard"><i></i></label>
      </div>
    </div>
    <div class="row" style="margin-bottom:24px"><button class="primary big" id="save">Save</button></div>
  </section>
</div>

<nav><div class="wrap">
  <button class="on" data-tab="live"><svg viewBox="0 0 24 24"><path d="M21 6H3c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-10 7H8v3H6v-3H3v-2h3V8h2v3h3v2zm4.5 2c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm4-3c-.83 0-1.5-.67-1.5-1.5S18.67 9 19.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"/></svg>Live<span class="pip" id="pip" hidden></span></button>
  <button data-tab="history"><svg viewBox="0 0 24 24"><path d="M13 3a9 9 0 0 0-9 9H1l3.9 3.9.1.1L9 12H6a7 7 0 1 1 2.05 4.95l-1.42 1.42A9 9 0 1 0 13 3zm-1 5v5l4.25 2.52.77-1.28-3.52-2.09V8H12z"/></svg>History</button>
  <button data-tab="setup"><svg viewBox="0 0 24 24"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.48.48 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.47.47 0 0 0-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z"/></svg>Setup</button>
</div></nav>

<div class="sheet-bg" id="sheetBg"></div>
<div class="sheet" id="sheet"><div class="wrap">
  <div class="grab"></div>
  <h3 id="sheetTitle">Charge or give time</h3>
  <div class="muted small" id="sheetPort"></div>
  <label class="f">Quick picks</label>
  <div class="chips" id="sheetChips"></div>
  <div class="pair">
    <div><label class="f" for="cm">Time</label><div class="suffix"><input type="number" id="cm" inputmode="numeric" min="1" max="1440" placeholder="45"><span>min</span></div></div>
    <div><label class="f" for="cp">Amount</label><div class="prefix"><span>₹</span><input type="number" id="cp" inputmode="numeric" min="0" placeholder="70"></div></div>
  </div>
  <div class="row"><button class="primary big" id="sheetCharge">Show QR on TV</button></div>
  <div class="row"><button class="ghost" id="sheetCash">Paid in cash — start now</button></div>
  <div class="row"><button class="ghost" id="sheetFree">Give time free</button></div>
  <div class="muted small" style="margin-top:12px">“Show QR on TV” puts a UPI code for this amount on the TV; you confirm it here once paid.</div>
</div></div>

<div class="toast" id="toast"></div>

<script>
  var base = location.pathname.replace(/\/+${'$'}/, '');
  var S = null, fetchedAt = 0, setupLoaded = false, setupDirty = false, lastRef = null, audio = null, sheetPort = null;
  function el(id) { return document.getElementById(id); }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return '&#' + c.charCodeAt(0) + ';'; }); }
  function money(n) { return '₹' + Number(n).toLocaleString('en-IN'); }
  function clock(s) { s = Math.max(0, Math.round(s)); var h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (x < 10 ? '0' : '') + x; }
  function hhmm(t) { var d = new Date(t); return (d.getHours() < 10 ? '0' : '') + d.getHours() + ':' + (d.getMinutes() < 10 ? '0' : '') + d.getMinutes(); }
  function since() { return (Date.now() - fetchedAt) / 1000; }
  var toastTimer;
  function toast(text, bad) { var t = el('toast'); t.textContent = text; t.className = 'toast show' + (bad ? ' err' : '');
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { t.className = 'toast'; }, 2600); }

  function post(params, ok) {
    var p = new URLSearchParams(params);
    return fetch(base + '/do', { method: 'POST', body: p.toString(), headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
      .then(function (r) { return r.json().then(function (j) { if (!r.ok) throw new Error(j.error || 'Failed'); return j; }); })
      .then(function (j) { take(j); if (ok) toast(ok); return j; })
      .catch(function (e) { toast(e.message || 'Couldn’t reach the TV', true); return null; });
  }
  function act(a, extra, ok) { var p = new URLSearchParams(extra || {}); p.set('a', a); return post(p, ok); }

  /* Tabs */
  var tabs = document.querySelectorAll('nav button');
  tabs.forEach(function (b) { b.onclick = function () { show(b.getAttribute('data-tab')); }; });
  function show(name) {
    tabs.forEach(function (b) { b.classList.toggle('on', b.getAttribute('data-tab') === name); });
    document.querySelectorAll('section').forEach(function (s) { s.classList.toggle('show', s.id === 'tab-' + name); });
    window.scrollTo(0, 0);
  }

  /* Alerts: phones only allow sound after a tap, and notifications need https, so this asks once. */
  el('soundBtn').onclick = function () {
    try { audio = audio || new (window.AudioContext || window.webkitAudioContext)(); audio.resume(); beep(); } catch (e) {}
    el('soundBtn').textContent = '🔔 Alerts on: sound and vibration';
    el('soundBtn').disabled = true;
  };
  function beep() {
    if (!audio) return;
    [0, .18].forEach(function (d) {
      var o = audio.createOscillator(), g = audio.createGain();
      o.frequency.value = 880; o.connect(g); g.connect(audio.destination);
      g.gain.setValueAtTime(.0001, audio.currentTime + d); g.gain.exponentialRampToValueAtTime(.3, audio.currentTime + d + .02);
      g.gain.exponentialRampToValueAtTime(.0001, audio.currentTime + d + .15);
      o.start(audio.currentTime + d); o.stop(audio.currentTime + d + .16);
    });
  }

  function take(j) { S = j; fetchedAt = Date.now(); render(); }

  function render() {
    if (!S) return;
    var p = S.pending;
    el('today').textContent = money(S.today);
    el('hToday').textContent = money(S.today);
    el('hCount').textContent = S.todayCount;
    el('pip').hidden = !p;

    /* Things to fix, at the top of Live. */
    var n = [];
    if (!S.upi) n.push('Add your UPI ID in Setup so the TV can show a payment QR.');
    if (!S.ports.some(function (x) { return x.paid; })) n.push('No port is paid yet. Turn one on in Setup.');
    if (!S.pinSet) n.push('Set an admin code in Setup: until then anyone can get into the TV’s settings.');
    else if (S.guard && !S.guardOn) n.push('The lock is off on the TV: turn on “Remote shortcut” under Fire TV Settings › Accessibility.');
    S.ports.forEach(function (x) { if (x.device && !x.paid && S.ports.some(function (y) { return y.paid; }))
      n.push(esc(x.label) + ' has a device on it but isn’t paid: a console moved there would play free.'); });
    el('notices').innerHTML = n.map(function (t) { return '<div class="notice">' + t + '</div>'; }).join('');

    if (p) {
      var left = Math.max(0, p.expires - since());
      el('pending').innerHTML = '<h2>Waiting for you</h2><div class="card pending">' +
        '<div class="amount">' + money(p.price) + '</div>' +
        '<div class="meta"><span><b>' + esc(p.label) + '</b></span><span>' + esc(p.port) + '</span></div>' +
        '<div class="meta"><span>Ref <span class="ref">' + esc(p.ref) + '</span></span><span>Expires in <b>' + clock(left) + '</b></span>' +
        (p.custom ? '<span>Custom charge</span>' : '') + '</div>' +
        '<div class="muted small" style="margin-top:8px">Check your UPI app for ' + money(p.price) + ' with note “ref ' + esc(p.ref) + '”.</div>' +
        '<div class="row"><button class="primary big" data-a="confirm">Confirm payment</button></div>' +
        '<div class="row"><button class="danger" data-a="decline">Decline</button></div></div>';
      el('pending').querySelector('[data-a=confirm]').onclick = function () { act('confirm', { ref: p.ref }, 'Confirmed. Play has started.'); };
      el('pending').querySelector('[data-a=decline]').onclick = function () { act('decline', { ref: p.ref }, 'Declined'); };
      if (p.ref !== lastRef) { document.title = '● ' + money(p.price) + ' waiting'; if (navigator.vibrate) navigator.vibrate([250, 120, 250]); beep(); }
    } else {
      el('pending').innerHTML = '';
      document.title = 'Pay to play';
    }
    lastRef = p ? p.ref : null;

    /* One card per paid port, plus any port playing that isn't paid any more. */
    var sessions = {}; S.sessions.forEach(function (s) { sessions[s.id] = s; });
    var shown = S.ports.filter(function (x) { return x.paid || sessions[x.id]; });
    el('ports').innerHTML = '<h2>Consoles</h2>' + (shown.length ? shown.map(function (x, i) {
      var s = sessions[x.id], waiting = p && p.id === x.id, body;
      if (s && s.unlimited) {
        body = '<div class="clock">No limit</div><div class="muted small">Admin code · ends when the console is closed on the TV</div>';
      } else if (s) {
        var l = Math.max(0, s.left - since()), cls = l <= 60 ? ' red' : l <= 300 ? ' amber' : '';
        body = '<div class="clock' + cls + '" data-left="' + s.left + '">' + clock(l) + '</div>' +
          '<div class="bar"><i style="width:' + Math.min(100, l / 36) + '%"></i></div>';
      } else {
        body = '<div class="muted" style="margin-top:6px">' + (waiting ? 'Showing a payment QR on the TV' : 'Idle') + '</div>';
      }
      var badge = s ? (s.unlimited ? '<span class="badge admin">Admin</span>' : '<span class="badge play">Playing</span>')
        : waiting ? '<span class="badge wait">Waiting</span>' : '<span class="badge">Free</span>';
      var actions = s
        ? '<button data-i="' + i + '" data-a="add15">+15 min</button><button data-i="' + i + '" data-a="sheet">More…</button><button class="danger" data-i="' + i + '" data-a="end">End</button>'
        : '<button class="primary" data-i="' + i + '" data-a="sheet">Charge or give time</button>';
      return '<div class="card"><div class="port"><div class="grow"><div class="name">' + esc(x.label) + '</div>' +
        '<div class="muted small">' + esc(x.device || 'No device announced') + (x.paid ? '' : ' · not paid') + '</div></div>' + badge + '</div>' +
        body + '<div class="row">' + actions + '</div></div>';
    }).join('') : '<div class="card empty">No paid ports. Turn one on in Setup.</div>');
    el('ports').querySelectorAll('button').forEach(function (b) {
      var x = shown[+b.getAttribute('data-i')], a = b.getAttribute('data-a');
      b.onclick = function () {
        if (a === 'add15') act('grant', { id: x.id, m: 15 }, 'Added 15 minutes');
        else if (a === 'end') { if (confirm('End play on ' + x.label + ' now?')) act('end', { id: x.id }, 'Ended'); }
        else openSheet(x);
      };
    });

    /* The TV's settings lock. */
    var lr = el('lockRow');
    if (!S.pinSet || !S.guard) {
      el('lockTitle').textContent = 'Not locked';
      el('lockText').textContent = !S.pinSet ? 'Set an admin code in Setup to lock the TV’s settings.' : 'The lock is off in Setup.';
      lr.innerHTML = '';
    } else if (S.adminLeft > 0) {
      el('lockTitle').textContent = 'Unlocked';
      el('lockText').textContent = 'Locks again in about ' + Math.ceil(S.adminLeft / 60) + ' min, or when you lock it.';
      lr.innerHTML = '<button data-a="lock">Lock now</button>';
    } else {
      el('lockTitle').textContent = 'Locked';
      el('lockText').textContent = 'Fire TV’s settings and own input switching are blocked.';
      lr.innerHTML = '<button data-a="unlock">Unlock for 10 minutes</button>';
    }
    lr.querySelectorAll('button').forEach(function (b) {
      var a = b.getAttribute('data-a');
      b.onclick = function () { act(a, {}, a === 'lock' ? 'Locked' : 'Unlocked for 10 minutes'); };
    });

    /* History */
    el('log').innerHTML = S.log.length ? S.log.map(function (e) {
      return '<li><time>' + hhmm(e.at) + '</time><span>' + esc(e.t) + '</span></li>';
    }).join('') : '<li class="empty" style="display:block">Nothing yet today.</li>';

    if (!setupLoaded || (!setupDirty && !el('tab-setup').contains(document.activeElement))) loadSetup();
  }

  /* Live countdowns between polls. */
  setInterval(function () {
    if (!S) return;
    document.querySelectorAll('.clock[data-left]').forEach(function (c) {
      var l = Math.max(0, +c.getAttribute('data-left') - since());
      c.textContent = clock(l);
      c.className = 'clock' + (l <= 60 ? ' red' : l <= 300 ? ' amber' : '');
      var bar = c.nextElementSibling && c.nextElementSibling.firstElementChild;
      if (bar) bar.style.width = Math.min(100, l / 36) + '%';
    });
  }, 1000);

  /* The sheet: presets fill the fields; any time and amount can be typed. */
  function openSheet(port) {
    sheetPort = port;
    el('sheetPort').textContent = port.name;
    el('sheetChips').innerHTML = S.plans.map(function (x, i) {
      return '<button class="chip" data-i="' + i + '">' + esc(x.label) + ' · ' + money(x.p) + '</button>';
    }).join('');
    el('sheetChips').querySelectorAll('.chip').forEach(function (c) {
      c.onclick = function () {
        var x = S.plans[+c.getAttribute('data-i')];
        el('cm').value = x.m; el('cp').value = x.p;
        el('sheetChips').querySelectorAll('.chip').forEach(function (o) { o.classList.toggle('on', o === c); });
      };
    });
    el('cm').value = ''; el('cp').value = '';
    el('sheetCharge').disabled = !port.paid;
    el('sheet').classList.add('show'); el('sheetBg').classList.add('show');
  }
  function closeSheet() { el('sheet').classList.remove('show'); el('sheetBg').classList.remove('show'); }
  el('sheetBg').onclick = closeSheet;
  ['cm', 'cp'].forEach(function (id) { el(id).oninput = function () {
    el('sheetChips').querySelectorAll('.chip').forEach(function (o) { o.classList.remove('on'); }); }; });
  function sheetValues(needPrice) {
    var m = parseInt(el('cm').value, 10), p = parseInt(el('cp').value, 10);
    if (!(m >= 1 && m <= 1440)) { toast('Enter a time from 1 to 1440 minutes', true); return null; }
    if (needPrice && !(p >= 1)) { toast('Enter an amount', true); return null; }
    return { m: m, p: p || 0 };
  }
  el('sheetCharge').onclick = function () { var v = sheetValues(true); if (!v) return;
    act('charge', { id: sheetPort.id, m: v.m, p: v.p }, 'QR for ' + money(v.p) + ' is on the TV').then(function (j) { if (j) closeSheet(); }); };
  el('sheetCash').onclick = function () { var v = sheetValues(true); if (!v) return;
    act('grant', { id: sheetPort.id, m: v.m, p: v.p }, 'Started ' + v.m + ' min (cash ' + money(v.p) + ')').then(function (j) { if (j) closeSheet(); }); };
  el('sheetFree').onclick = function () { var v = sheetValues(false); if (!v) return;
    act('grant', { id: sheetPort.id, m: v.m }, 'Gave ' + v.m + ' min').then(function (j) { if (j) closeSheet(); }); };

  /* Setup */
  function planRow(m, p) {
    var d = document.createElement('div'); d.className = 'plan';
    d.innerHTML = '<div class="suffix"><input type="number" inputmode="numeric" min="1" max="1440" class="pm" placeholder="30"><span>min</span></div>' +
      '<div class="prefix"><span>₹</span><input type="number" inputmode="numeric" min="1" class="pp" placeholder="50"></div><button type="button" aria-label="Remove">✕</button>';
    d.querySelector('.pm').value = m || ''; d.querySelector('.pp').value = p || '';
    d.querySelector('button').onclick = function () { d.remove(); dirty(); planButtons(); };
    d.querySelectorAll('input').forEach(function (i) { i.oninput = dirty; });
    el('plans').appendChild(d);
  }
  function planButtons() { el('addPlan').disabled = el('plans').children.length >= (S ? S.maxPlans : 4); }
  el('addPlan').onclick = function () { planRow(); dirty(); planButtons(); };
  function dirty() { setupDirty = true; }
  ['upi', 'payee', 'pin', 'guard'].forEach(function (id) { el(id).addEventListener('input', dirty); el(id).addEventListener('change', dirty); });
  function loadSetup() {
    el('setPorts').innerHTML = S.ports.length ? S.ports.map(function (x) {
      return '<div class="switch"><div class="grow"><div>' + esc(x.label) + '</div><div class="muted small">' + esc(x.device || 'No device announced') + '</div></div>' +
        '<label class="tog"><input type="checkbox" class="sp" value="' + esc(x.id) + '"' + (x.paid ? ' checked' : '') + '><i></i></label></div>';
    }).join('') : '<div class="empty">The TV reports no HDMI ports.</div>';
    el('setPorts').querySelectorAll('input').forEach(function (i) { i.onchange = dirty; });
    el('upi').value = S.upi; el('payee').value = S.payee; el('guard').checked = S.guard;
    el('plans').innerHTML = ''; S.plans.forEach(function (x) { planRow(x.m, x.p); }); planButtons();
    el('pinNote').textContent = S.pinSet ? 'A code is set. Leave blank to keep it.' : 'No code yet. Set one so only you can skip payment and open settings.';
    setupLoaded = true; setupDirty = false;
  }
  el('save').onclick = function () {
    var p = new URLSearchParams(); p.set('a', 'settings');
    el('setPorts').querySelectorAll('input:checked').forEach(function (i) { p.append('ports', i.value); });
    p.set('upi', el('upi').value); p.set('payee', el('payee').value); p.set('pin', el('pin').value);
    p.set('guard', el('guard').checked ? '1' : '0');
    var plans = [];
    el('plans').querySelectorAll('.plan').forEach(function (d) {
      var m = d.querySelector('.pm').value, pr = d.querySelector('.pp').value; if (m && pr) plans.push(m + ':' + pr); });
    p.set('plans', plans.join(','));
    post(p, 'Saved').then(function (j) { if (j) { el('pin').value = ''; setupDirty = false; loadSetup(); } });
  };

  function poll() {
    fetch(base + '/state', { cache: 'no-store' }).then(function (r) { return r.json(); })
      .then(function (j) { el('dot').className = 'dot on'; el('conn').textContent = 'TV connected'; take(j); })
      .catch(function () { el('dot').className = 'dot off'; el('conn').textContent = 'Can’t reach the TV. Same Wi-Fi?'; });
  }
  poll(); setInterval(poll, 1500);
  document.addEventListener('visibilitychange', function () { if (!document.hidden) poll(); });
</script>
</body>
</html>
""".trimIndent()
