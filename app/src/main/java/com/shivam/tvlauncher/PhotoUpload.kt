package com.shivam.tvlauncher

import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The "Choose a photo" screen: where the phone should go, and how the last upload went. */
sealed interface PhotoUpload {
    data class Ready(val url: String, val status: Status = Status.Waiting) : PhotoUpload
    /** No Wi-Fi or Ethernet address to serve from, or the port couldn't be opened. */
    data object Unavailable : PhotoUpload

    enum class Status { Waiting, Receiving, Set, Failed }
}

/** The TV's IPv4 address on its current network, or null when it has none. */
fun lanAddress(context: Context): String? {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val link = cm.activeNetwork?.let { cm.getLinkProperties(it) } ?: return null
    return link.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
}

/**
 * A one-page web server that runs only while "Choose a photo" is open. The phone opens the page
 * from the QR code, picks an image, and the page posts it back here as a JPEG. The random [token]
 * in the path keeps anyone else on the network from posting without having seen the TV screen.
 */
class PhotoUploadServer(
    private val scope: CoroutineScope,
    private val cacheDir: File,
    private val onEvent: (PhotoUpload.Status) -> Unit,
    /** Saves the received file; false if it isn't a usable image. */
    private val onPhoto: suspend (File) -> Boolean,
) {
    val token: String = ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private var socket: ServerSocket? = null
    private var job: Job? = null

    /** Opens the port (a fixed one when free, so the address stays the same) and returns it. Blocking. */
    fun start(): Int? {
        val server = runCatching { ServerSocket(PreferredPort) }.recoverCatching { ServerSocket(0) }.getOrNull() ?: return null
        socket = server
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                // One coroutine each, so an idle preconnect from the browser can't hold up the real request.
                launch { client.use { runCatching { handle(it) } } }
            }
        }
        return server.localPort
    }

    fun stop() {
        runCatching { socket?.close() }
        job?.cancel()
        socket = null
        job = null
    }

    private suspend fun handle(client: Socket) {
        client.soTimeout = 30_000
        val input = client.getInputStream()
        val output = client.getOutputStream()
        val head = readHead(input) ?: return
        val (method, path) = head.lines().first().split(" ").let { it.getOrNull(0) to it.getOrNull(1) }
        if (path?.substringBefore('?') != "/$token") return output.respond(404, "text/plain", "Not found")
        when (method) {
            "GET" -> output.respond(200, "text/html; charset=utf-8", UploadPage)
            "POST" -> {
                val length = head.lines()
                    .firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toLongOrNull()
                if (length == null || length <= 0 || length > MaxBytes) return output.respond(413, "text/plain", "Too large")
                onEvent(PhotoUpload.Status.Receiving)
                val file = File.createTempFile("upload", ".tmp", cacheDir)
                val complete = runCatching {
                    file.outputStream().use { out -> copyExactly(input, out, length) }
                }.getOrDefault(false)
                val saved = complete && onPhoto(file)
                file.delete()
                onEvent(if (saved) PhotoUpload.Status.Set else PhotoUpload.Status.Failed)
                if (saved) output.respond(200, "text/plain", "OK") else output.respond(422, "text/plain", "Couldn't use that image")
            }
            else -> output.respond(405, "text/plain", "Method not allowed")
        }
    }

    /** The request line and headers, up to the blank line; null if the client sends junk. */
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

    private fun copyExactly(input: InputStream, out: OutputStream, length: Long): Boolean {
        val buffer = ByteArray(64 * 1024)
        var left = length
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) return false
            out.write(buffer, 0, n)
            left -= n
        }
        return true
    }

    private fun OutputStream.respond(code: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        val reason = when (code) { 200 -> "OK"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 413 -> "Payload Too Large"; else -> "Error" }
        write("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        write(bytes)
        flush()
    }

    private companion object {
        const val PreferredPort = 8765
        /** The page already scales to 4K before sending; this is for the raw-file fallback. */
        const val MaxBytes = 40L * 1024 * 1024
    }
}

/**
 * The phone's side. The browser decodes the image (HEIC on iPhones, EXIF rotation included), scales
 * it to cover 3840×2160 at most, and posts a JPEG; if it can't decode it, the original file goes as is.
 */
private val UploadPage = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TV background</title>
<style>
  body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
         background: #111318; color: #f2f3f5; font: 17px/1.45 system-ui, -apple-system, sans-serif; }
  main { width: 100%; max-width: 420px; padding: 24px; box-sizing: border-box; text-align: center; }
  h1 { font-size: 24px; margin: 0 0 6px; }
  p { color: #a9adb6; margin: 0 0 24px; }
  label { display: block; padding: 16px; border-radius: 14px; background: #f2f3f5; color: #111318;
          font-weight: 600; cursor: pointer; }
  input { display: none; }
  img { display: none; width: 100%; border-radius: 12px; margin-top: 20px; }
  #status { margin-top: 18px; min-height: 1.5em; color: #f2f3f5; }
</style>
</head>
<body>
<main>
  <h1>TV background</h1>
  <p>Pick a photo and it becomes the launcher's background.</p>
  <label>Choose a photo<input id="file" type="file" accept="image/*"></label>
  <img id="preview" alt="">
  <div id="status"></div>
</main>
<script>
  var input = document.getElementById('file');
  var status = document.getElementById('status');
  var preview = document.getElementById('preview');
  function send(body) {
    status.textContent = 'Sending to the TV…';
    fetch(location.pathname, { method: 'POST', body: body })
      .then(function (r) { status.textContent = r.ok ? 'Done. It’s on the TV.' : 'The TV couldn’t use that image. Try another.'; })
      .catch(function () { status.textContent = 'Couldn’t reach the TV. Is “Choose a photo” still open on it?'; });
  }
  input.addEventListener('change', function () {
    var file = input.files[0];
    if (!file) return;
    status.textContent = 'Preparing…';
    var url = URL.createObjectURL(file);
    var img = new Image();
    img.onload = function () {
      var scale = Math.min(1, Math.max(3840 / img.naturalWidth, 2160 / img.naturalHeight));
      var canvas = document.createElement('canvas');
      canvas.width = Math.round(img.naturalWidth * scale);
      canvas.height = Math.round(img.naturalHeight * scale);
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
      preview.src = url;
      preview.style.display = 'block';
      canvas.toBlob(function (blob) { send(blob || file); }, 'image/jpeg', 0.95);
    };
    img.onerror = function () { send(file); };
    img.src = url;
    input.value = '';
  });
</script>
</body>
</html>
""".trimIndent()
