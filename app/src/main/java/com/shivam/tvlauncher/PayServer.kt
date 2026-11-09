package com.shivam.tvlauncher

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The owner's page for pay to play, served on the home network: confirm or decline a payment the
 * TV is waiting on, give or end time, and set prices, UPI ID, admin code and which ports are paid.
 * It runs while any port is paid, or while the TV's Pay to play panel is open (so it can be set
 * up the first time). Only requests carrying the secret token in the path are answered.
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
            method == "GET" && path == base -> output.respond(200, "text/html; charset=utf-8", OwnerPage)
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
                val form = parseForm(String(body, Charsets.UTF_8))
                val error = act(form)
                if (error == null) {
                    output.respond(200, "application/json", state().toString())
                } else {
                    output.respond(400, "application/json", JSONObject().put("error", error).toString())
                }
            }
            else -> output.respond(404, "text/plain", "Not found")
        }
    }

    /** Carries out one action from the page; returns why it couldn't, or null when done. */
    private fun act(form: Map<String, List<String>>): String? {
        fun one(name: String) = form[name]?.firstOrNull().orEmpty()
        return when (one("a")) {
            "confirm" -> if (PayToPlay.confirm(one("ref"))) null else "That payment is no longer waiting."
            "decline" -> if (PayToPlay.decline(one("ref"))) null else "That payment is no longer waiting."
            "grant" -> {
                val minutes = one("m").toIntOrNull()?.takeIf { it in 1..24 * 60 } ?: return "Pick a time."
                val id = one("id").takeIf { it.isNotEmpty() } ?: return "Pick a port."
                PayToPlay.grant(id, minutes)
                null
            }
            "end" -> {
                PayToPlay.end(one("id"))
                null
            }
            "settings" -> {
                val plans = PayToPlay.parsePlans(one("plans"))
                if (plans.isEmpty()) return "Add at least one price, like 30:50 (30 minutes for ₹50)."
                val pin = one("pin").trim()
                if (pin.isNotEmpty() && (pin.length !in 4..8 || !pin.all(Char::isDigit))) return "The admin code is 4 to 8 digits."
                val upi = one("upi").trim()
                if (upi.isNotEmpty() && !UpiId.matches(upi)) return "That doesn't look like a UPI ID (name@bank)."
                PayToPlay.updateConfig { config ->
                    config.copy(
                        ports = form["ports"].orEmpty().filter { it.isNotBlank() }.toSet(),
                        upiId = upi,
                        payee = one("payee").filterNot { it.isISOControl() }.trim().take(40),
                        plans = plans,
                        // Blank keeps the current code, so the field never has to show it.
                        pin = pin.ifEmpty { config.pin },
                    )
                }
                null
            }
            else -> "Unknown action."
        }
    }

    private fun state(): JSONObject {
        val config = PayToPlay.config.value
        val now = System.currentTimeMillis()
        val ports = hdmiPorts(app)
        val names = ports.associate { it.inputId to (it.device?.let { d -> "${it.label} · $d" } ?: it.label) }
        val unlimited = PayToPlay.unlimited.value
        val playing = (PayToPlay.paidUntil.value.filterValues { it > now }.keys + unlimited).distinct()
        return JSONObject()
            .put("ports", JSONArray(ports.map { JSONObject().put("id", it.inputId).put("name", names[it.inputId]).put("paid", it.inputId in config.ports) }))
            .put("upi", config.upiId)
            .put("payee", config.payee)
            .put("plans", PayToPlay.formatPlans(config.plans))
            .put("planList", JSONArray(config.plans.map { JSONObject().put("m", it.minutes).put("p", it.price).put("label", it.durationLabel) }))
            .put("pinSet", config.pin.isNotEmpty())
            .put("pending", PayToPlay.pending.value?.let {
                JSONObject().put("ref", it.ref).put("port", it.portName).put("label", it.plan.durationLabel)
                    .put("price", it.plan.price).put("age", (now - it.createdAt) / 1000)
            } ?: JSONObject.NULL)
            .put("sessions", JSONArray(playing.map { id ->
                JSONObject().put("id", id).put("name", names[id] ?: id).put("unlimited", id in unlimited)
                    .put("left", PayToPlay.paidLeft(id, now) / 1000)
            }))
    }

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

/**
 * The phone's side: polls the TV every two seconds. A waiting payment comes first, big, with
 * Confirm and Decline; then what's playing; then the setup form.
 */
private val OwnerPage = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Pay to play</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; background: #111318; color: #f2f3f5; font: 16px/1.45 system-ui, -apple-system, sans-serif; }
  main { max-width: 480px; margin: 0 auto; padding: 20px 16px 48px; }
  h1 { font-size: 22px; margin: 0 0 4px; }
  h2 { font-size: 15px; text-transform: uppercase; letter-spacing: .06em; color: #a9adb6; margin: 28px 0 10px; }
  .muted { color: #a9adb6; }
  .card { background: #1c1f26; border-radius: 16px; padding: 16px; margin-bottom: 12px; }
  .pay { background: #24301f; border: 2px solid #7bd389; }
  .big { font-size: 28px; font-weight: 700; }
  .row { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
  button { flex: 1; min-width: 90px; padding: 14px 12px; border: 0; border-radius: 12px; font: inherit; font-weight: 600;
           background: #2c313b; color: #f2f3f5; cursor: pointer; }
  button.yes { background: #7bd389; color: #0c0d10; }
  button.no { background: #ff8a80; color: #0c0d10; }
  label { display: block; margin: 14px 0 6px; font-weight: 600; }
  input[type=text], input[type=password] { width: 100%; box-sizing: border-box; padding: 12px; border-radius: 10px;
           border: 1px solid #3a3f4a; background: #15171c; color: #f2f3f5; font: inherit; }
  .check { display: flex; align-items: center; gap: 10px; font-weight: 400; margin: 8px 0; }
  .check input { width: 22px; height: 22px; }
  #msg { min-height: 1.4em; margin-top: 12px; }
  .err { color: #ff8a80; }
</style>
</head>
<body>
<main>
  <h1>Pay to play</h1>
  <div class="muted" id="conn">Connecting to the TV…</div>

  <div id="pending"></div>

  <h2>Playing now</h2>
  <div id="sessions"></div>

  <h2>Give time</h2>
  <div class="card">
    <div class="muted">For cash, or a payment you've already checked.</div>
    <label for="gport">Port</label>
    <select id="gport" style="width:100%;padding:12px;border-radius:10px;background:#15171c;color:#f2f3f5;border:1px solid #3a3f4a;font:inherit"></select>
    <div class="row" id="grant"></div>
  </div>

  <h2>Setup</h2>
  <form class="card" id="setup">
    <div class="muted">Paid ports ask for payment when opened from the launcher.</div>
    <div id="ports"></div>
    <label for="upi">Your UPI ID</label>
    <input type="text" id="upi" name="upi" placeholder="name@okaxis" autocomplete="off">
    <label for="payee">Name shown in the payment app</label>
    <input type="text" id="payee" name="payee" placeholder="Optional">
    <label for="plans">Prices (minutes:rupees, up to 4)</label>
    <input type="text" id="plans" name="plans" placeholder="30:50, 60:90, 120:160">
    <label for="pin">Admin code (4–8 digits)</label>
    <input type="password" id="pin" name="pin" inputmode="numeric" autocomplete="new-password">
    <div class="muted" id="pinnote"></div>
    <div class="row"><button type="submit" class="yes">Save</button></div>
  </form>
  <div id="msg"></div>
</main>
<script>
  var base = location.pathname.replace(/\/$/, '');
  var loaded = false, lastRef = null;
  function el(id) { return document.getElementById(id); }
  function esc(s) { return String(s).replace(/[&<>"']/g, function (c) { return '&#' + c.charCodeAt(0) + ';'; }); }
  function clock(s) { var m = Math.floor(s / 60), h = Math.floor(m / 60); m = m % 60; s = s % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (s < 10 ? '0' : '') + s; }
  function say(text, bad) { el('msg').textContent = text; el('msg').className = bad ? 'err' : 'muted'; }
  function post(params) {
    var body = new URLSearchParams(params);
    return fetch(base + '/do', { method: 'POST', body: body.toString(),
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
      .then(function (r) { return r.json().then(function (j) { if (!r.ok) throw new Error(j.error || 'Failed'); return j; }); })
      .then(function (j) { render(j); return j; })
      .catch(function (e) { say(e.message || 'Couldn’t reach the TV.', true); });
  }
  function act(a, extra) { var p = new URLSearchParams(extra || {}); p.set('a', a); return post(p); }
  function render(s) {
    el('conn').textContent = 'Connected to the TV.';
    var p = s.pending;
    if (p) {
      el('pending').innerHTML = '<h2>Waiting for you</h2><div class="card pay"><div class="big">₹' + p.price + ' · ' + esc(p.label) + '</div>' +
        '<div>' + esc(p.port) + ' · ref <b>' + esc(p.ref) + '</b> · ' + clock(p.age) + ' ago</div>' +
        '<div class="muted">Check your UPI app for ₹' + p.price + ' with note “ref ' + esc(p.ref) + '”.</div>' +
        '<div class="row"><button class="yes" onclick="act(\'confirm\',{ref:\'' + esc(p.ref) + '\'})">Confirm payment</button>' +
        '<button class="no" onclick="act(\'decline\',{ref:\'' + esc(p.ref) + '\'})">Decline</button></div></div>';
      if (p.ref !== lastRef) { document.title = '● Payment waiting'; if (navigator.vibrate) navigator.vibrate([200, 100, 200]); }
    } else {
      el('pending').innerHTML = '';
      document.title = 'Pay to play';
    }
    lastRef = p ? p.ref : null;
    el('sessions').innerHTML = s.sessions.length ? s.sessions.map(function (x) {
      return '<div class="card"><b>' + esc(x.name) + '</b><div class="big">' + (x.unlimited ? 'Admin · no limit' : clock(x.left) + ' left') + '</div>' +
        '<div class="row"><button onclick="act(\'grant\',{id:' + JSON.stringify(x.id).replace(/"/g, '&quot;') + ',m:15})">+15 min</button>' +
        '<button class="no" onclick="act(\'end\',{id:' + JSON.stringify(x.id).replace(/"/g, '&quot;') + '})">End now</button></div></div>';
    }).join('') : '<div class="card muted">Nothing playing.</div>';
    var gp = el('gport'), keep = gp.value;
    gp.innerHTML = s.ports.map(function (x) { return '<option value="' + esc(x.id) + '">' + esc(x.name) + '</option>'; }).join('');
    var firstPaid = s.ports.filter(function (x) { return x.paid; })[0];
    gp.value = keep || (firstPaid ? firstPaid.id : (s.ports[0] || {}).id || '');
    el('grant').innerHTML = s.planList.map(function (x) {
      return '<button onclick="act(\'grant\',{id:el(\'gport\').value,m:' + x.m + '})">' + esc(x.label) + '</button>';
    }).join('');
    if (!loaded) {
      el('ports').innerHTML = s.ports.length ? s.ports.map(function (x) {
        return '<label class="check"><input type="checkbox" name="ports" value="' + esc(x.id) + '"' + (x.paid ? ' checked' : '') + '>' + esc(x.name) + '</label>';
      }).join('') : '<div class="muted">The TV reports no HDMI ports.</div>';
      el('upi').value = s.upi; el('payee').value = s.payee; el('plans').value = s.plans;
      loaded = true;
    }
    el('pinnote').textContent = s.pinSet ? 'An admin code is set. Leave blank to keep it.' : 'No admin code yet: set one so only you can skip payment.';
  }
  function poll() {
    fetch(base + '/state', { cache: 'no-store' }).then(function (r) { return r.json(); }).then(render)
      .catch(function () { el('conn').textContent = 'Can’t reach the TV. Is it on, and is this phone on the same Wi-Fi?'; });
  }
  el('setup').addEventListener('submit', function (e) {
    e.preventDefault();
    var p = new URLSearchParams(new FormData(el('setup'))); p.set('a', 'settings');
    post(p).then(function (j) { if (j) { el('pin').value = ''; say('Saved.'); } });
  });
  poll(); setInterval(poll, 2000);
</script>
</body>
</html>
""".trimIndent()
