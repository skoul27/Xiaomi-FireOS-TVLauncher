# Recovery: getting Amazon's launcher back

Use this if the Home button stops working, shows a blank screen, or you simply want the stock
Fire TV home back. Run from a PC on the same network as the TV.

```sh
adb connect <TV-IP>:5555
adb shell pm enable com.amazon.tv.launcher
```

Then press Home on the remote. Amazon's launcher has priority 950 for HOME, so it takes over
again as soon as it is enabled. The launcher app itself never changes system settings; the one
setting changed by hand is the remote shortcut below.

## Remote shortcut (accessibility service)

Fire OS has no on-screen switch for third-party accessibility services, so the color-button
shortcut was turned on over adb on 2026-10-25. Values before the change:

```
enabled_accessibility_services = null   (unset)
accessibility_enabled          = 0
```

How it was enabled (appended to the existing list, which was empty):

```sh
adb shell settings put secure enabled_accessibility_services com.shivam.tvlauncher/.ShortcutKeyService
adb shell settings put secure accessibility_enabled 1
```

To turn it off and restore exactly the values above:

```sh
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
```

To turn it off from the TV without adb, set "Remote shortcut" to Off under gear → Launcher
settings: the service stays bound but ignores every key. Uninstalling the launcher also removes it.
If a Fire OS update switches it off, read the current list first and append again, separated by
`:`, rather than overwriting it:

```sh
adb shell settings get secure enabled_accessibility_services
# empty or "null": put just our entry; otherwise put "<current list>:com.shivam.tvlauncher/.ShortcutKeyService"
adb shell settings put secure enabled_accessibility_services <list>
adb shell settings put secure accessibility_enabled 1
```

The launcher checks this for you: when the chosen color button's service isn't enabled, the
System panel (gear) shows a notice with these two commands already filled in from the current
list. The launcher only reads the setting; it never changes it.

## What this depends on

- **ADB debugging must stay on.** It is under Device & Software → Developer Options, which this
  launcher's gear menu (System → Device & Software) opens directly. Don't turn it off while the
  stock launcher is disabled, or these commands can't reach the TV.
- The TV's IP is `<TV-IP>` today. If your router hands it a new one, look it up under
  System → Network in this launcher, or in the router's client list.
- If `adb connect` asks for authorisation, accept the prompt on the TV screen.

## Checking the current state

```sh
adb shell pm list packages -d | grep com.amazon.tv.launcher   # listed = disabled
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility | grep -E "Bound services|Enabled services"
```
