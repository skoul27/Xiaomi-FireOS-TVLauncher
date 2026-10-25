package com.shivam.tvlauncher

import androidx.compose.runtime.Immutable

enum class ConsoleBrand { PlayStation5 }

/** A console plugged into one of the TV's HDMI ports; opening it shows that input ([ConsoleActivity]). */
@Immutable
class Console(val name: String, val brand: ConsoleBrand, val hdmiPort: Int, val inputId: String)

/**
 * This TV's MediaTek input service numbers its HDMI hardware inputs HW2–HW4 for ports 1–3
 * (`adb shell dumpsys tv_input`, mHardwareInputIdMap against hdmi_port).
 */
val Consoles: List<Console> = listOf(
    Console("PlayStation 5", ConsoleBrand.PlayStation5, hdmiPort = 3, inputId = "com.mediatek.tis/.HdmiInputService/HW4"),
)
