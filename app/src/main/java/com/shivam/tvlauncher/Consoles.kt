package com.shivam.tvlauncher

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConsoleBrand { PlayStation, Xbox, Nintendo, Other }

/** How one HDMI port shows in the Consoles row. Ports the user hasn't set are [Auto]. */
enum class TileMode(val label: String, val brand: ConsoleBrand?) {
    /** Shown only while a device announces itself on the port, branded from its name. */
    Auto("Automatic", null),
    PlayStation("PlayStation", ConsoleBrand.PlayStation),
    Xbox("Xbox", ConsoleBrand.Xbox),
    Nintendo("Nintendo", ConsoleBrand.Nintendo),
    Other("Other", ConsoleBrand.Other),
    Hidden("Hidden", null),
}

/** The user's choice for one port. [name] is what an [TileMode.Other] tile is called; blank for the default. */
@Immutable
data class PortTile(val mode: TileMode, val name: String = "")

const val MaxTileName = 24

/** Keeps a tile name to one short line of plain text. */
fun cleanTileName(name: String): String = name.filterNot { it.isISOControl() }.trim().take(MaxTileName)

/**
 * An HDMI port as the TV reports it. [device] is the name the plugged-in device announces over
 * HDMI-CEC, or null when nothing does.
 */
@Immutable
data class HdmiPort(val inputId: String, val label: String, val device: String?)

/** A card in the Consoles row; opening it shows that input full screen ([ConsoleActivity]). */
@Immutable
class Console(val name: String, val brand: ConsoleBrand, val port: String, val inputId: String)

/**
 * The TV's HDMI ports, in port order. Each port is a hardware input; a device announcing itself
 * over HDMI-CEC appears as a child input of its port (a soundbar on ARC doesn't). This TV's own
 * input states can't tell an empty port from a device in standby, so CEC is the only sign used.
 */
fun hdmiPorts(context: Context): List<HdmiPort> {
    val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
    val hdmi = runCatching { manager.tvInputList }.getOrNull().orEmpty().filter { it.type == TvInputInfo.TYPE_HDMI }
    val devices = hdmi.filter { it.parentId != null }.groupBy { it.parentId }
    return hdmi
        .filter { it.parentId == null }
        .map { port ->
            HdmiPort(
                inputId = port.id,
                label = (port.loadCustomLabel(context) ?: port.loadLabel(context)).toString(),
                device = devices[port.id]?.firstOrNull()?.loadLabel(context)?.toString()?.takeIf { it.isNotBlank() },
            )
        }
        // By port number where the label has one ("HDMI 2" before "HDMI 10"), then by label.
        .sortedWith(compareBy({ it.label.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it.label }))
}

/** [hdmiPorts], read again whenever an input comes, goes or changes (a console waking, say). */
fun hdmiPortsFlow(context: Context): Flow<List<HdmiPort>> = callbackFlow {
    trySend(hdmiPorts(context))
    val manager = context.getSystemService(TvInputManager::class.java)
    val callback = object : TvInputManager.TvInputCallback() {
        override fun onInputAdded(inputId: String) { trySend(hdmiPorts(context)) }
        override fun onInputRemoved(inputId: String) { trySend(hdmiPorts(context)) }
        override fun onInputUpdated(inputId: String) { trySend(hdmiPorts(context)) }
    }
    manager?.registerCallback(callback, Handler(Looper.getMainLooper()))
    awaitClose { manager?.unregisterCallback(callback) }
}.distinctUntilChanged()

/**
 * The Consoles row: ports set to a tile always show (a PC, say, never announces itself);
 * Automatic ones only while a device announces itself there; Hidden ones never.
 */
fun consolesFor(ports: List<HdmiPort>, tiles: Map<String, PortTile>): List<Console> = ports.mapNotNull { port ->
    val tile = tiles[port.inputId] ?: PortTile(TileMode.Auto)
    val brand = when (tile.mode) {
        TileMode.Hidden -> return@mapNotNull null
        TileMode.Auto -> brandOf(port.device ?: return@mapNotNull null)
        else -> tile.mode.brand ?: ConsoleBrand.Other
    }
    Console(nameFor(brand, tile, port), brand, port.label, port.inputId)
}

private fun brandOf(device: String): ConsoleBrand = when {
    device.contains("PlayStation", ignoreCase = true) -> ConsoleBrand.PlayStation
    device.contains("Xbox", ignoreCase = true) -> ConsoleBrand.Xbox
    device.contains("Nintendo", ignoreCase = true) -> ConsoleBrand.Nintendo
    else -> ConsoleBrand.Other
}

/** The device's own name when it matches the brand ("PlayStation 5"), else the brand's usual one. */
private fun nameFor(brand: ConsoleBrand, tile: PortTile, port: HdmiPort): String {
    val announced = port.device?.takeIf { brandOf(it) == brand }
    return when (brand) {
        ConsoleBrand.PlayStation -> announced ?: "PlayStation 5"
        ConsoleBrand.Xbox -> announced ?: "Xbox"
        ConsoleBrand.Nintendo -> announced ?: "Nintendo Switch"
        ConsoleBrand.Other -> tile.name.ifBlank { port.device ?: port.label }
    }
}
