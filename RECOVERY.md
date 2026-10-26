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

## Home button (this Fire OS version)

As of 2026-10-25 Fire OS refuses to disable Amazon's launcher (`pm disable-user` fails with
"Cannot disable a protected package"), and it sends Home to Amazon's launcher even with this
one set as the preferred Home app. It also handles the Home key before any app (or
accessibility service) sees it. So the remote shortcut service below watches for Amazon's home
screen opening and opens this launcher over it, after a 0.4 s wait (Fire OS Settings passes
through Amazon's home on the way to its own screen; that is left alone).

- **Press Home**: Amazon's home flashes briefly, then this launcher (reset to the start).
- **Restart / wake**: the same setting opens this launcher once per boot (when the service
  first connects) and every time the screen turns on.
- **Launcher process dies while in front** (an update, a crash, a low-memory kill): Amazon's home
  takes its place before the service is back, and Home pressed there changes nothing the
  service can see. The launcher records whether it is in front, so the service reopens it when
  it reconnects.
- **Fire OS Settings** from the gear menu: the one pass through Amazon's home on the way there
  (within 10 s) is left alone, since it can take over a second right after a restart.
- **To get Amazon's home back** from the TV: gear → Launcher settings → Home button →
  "Fire TV home". From a computer: turn the service off with the commands below.

This launcher was also set as the preferred Home app on 2026-10-25. Fire OS ignores that today;
to undo it anyway:

```sh
adb shell cmd package set-home-activity --user 0 com.amazon.tv.launcher/.ui.HomeActivity_vNext
```

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
Settings panel (gear) shows a notice with these two commands already filled in from the current
list. The launcher only reads the setting; it never changes it.

## Backing up and restoring your setup

Your pins and their order, hidden apps, All apps order, console tiles, theme, animations and
remote shortcut can be saved as one JSON file. (Backups from before console tiles, format
version 1, still import; they leave your current console tiles as they are.) Fire OS doesn't let adb into the app's folder, and the launcher has no
storage permission, so the file goes through a small bridge in the launcher that only answers adb.

Back up (on the TV: gear → Launcher settings → Export backup, then on the computer):

```sh
adb shell content read --uri content://com.shivam.tvlauncher.backup/launcher-backup.json > launcher-backup.json
```

Restore (on the computer, then on the TV: gear → Launcher settings → Import backup):

```sh
adb shell content write --uri content://com.shivam.tvlauncher.backup/launcher-backup.json < launcher-backup.json
```

Import checks the whole file first and changes nothing if anything is wrong; a valid file shows
what it contains and asks before replacing your setup. Apps in the backup that aren't installed
are kept, so they reappear if you install them later. The file is plain JSON; editing it by hand
is fine as long as it stays valid. Uninstalling the launcher deletes the copy on the TV, so keep
the one on your computer.

## What this depends on

- **ADB debugging must stay on.** It is under Device & Software → Developer Options, which this
  launcher's gear menu (Settings → Device & Software) opens directly. Don't turn it off while the
  stock launcher is disabled, or these commands can't reach the TV.
- The TV's IP is `<TV-IP>` today. If your router hands it a new one, look it up under
  the Wi-Fi button in this launcher's top bar, or in the router's client list.
- If `adb connect` asks for authorisation, accept the prompt on the TV screen.

## Checking the current state

```sh
adb shell pm list packages -d | grep com.amazon.tv.launcher   # listed = disabled
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility | grep -E "Bound services|Enabled services"
```
