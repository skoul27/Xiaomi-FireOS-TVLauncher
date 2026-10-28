# Recovery: getting Amazon's launcher back

Use this if the Home button stops working, shows a blank screen, or you simply want the stock
Fire TV home back. Run from a computer on the same network as the TV, with
[adb](https://developer.android.com/tools/releases/platform-tools) installed. Replace `<TV-IP>`
with your TV's address (see "What this depends on" below).

```sh
adb connect <TV-IP>:5555
adb shell pm enable com.amazon.tv.launcher
```

Then press Home on the remote. Amazon's launcher has priority 950 for HOME, so it takes over
again as soon as it is enabled. The launcher app itself never changes system settings; the only
settings changed are the two adb steps below (remote shortcut and Now playing).

## Home button on Fire OS

Current Fire OS versions refuse to disable Amazon's launcher (`pm disable-user` fails with
"Cannot disable a protected package"), and send Home to Amazon's launcher even with this one
set as the preferred Home app. Fire OS also handles the Home key before any app (or
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
- **To get Amazon's home back** from the TV: gear → Launcher settings → Shortcuts → Home button →
  "Fire TV home". From a computer: turn the service off with the commands below.

If you set this launcher as the preferred Home app and want to undo it:

```sh
adb shell cmd package set-home-activity --user 0 com.amazon.tv.launcher/.ui.HomeActivity_vNext
```

## Remote shortcut (accessibility service)

Fire OS has no on-screen switch for third-party accessibility services, so the color-button
shortcut (and the Home redirect above) is turned on over adb. First read the current list:

```sh
adb shell settings get secure enabled_accessibility_services
```

If it prints `null` or nothing, enable just this service:

```sh
adb shell settings put secure enabled_accessibility_services com.shivam.tvlauncher/.ShortcutKeyService
adb shell settings put secure accessibility_enabled 1
```

Otherwise append to the existing list, separated by `:`, rather than overwriting it:

```sh
adb shell settings put secure enabled_accessibility_services "<current list>:com.shivam.tvlauncher/.ShortcutKeyService"
adb shell settings put secure accessibility_enabled 1
```

The launcher checks this for you: when the chosen color button's service isn't enabled, the
Settings panel (gear) shows a notice with these commands already filled in from the current
list. The launcher only reads the setting; it never changes it.

To turn it off from the TV without adb, set "Remote shortcut" to Off under gear → Launcher
settings → Shortcuts: the service stays bound but ignores every key. Uninstalling the launcher
also removes it. To turn it off over adb, put back the list you had before, or if it was empty:

```sh
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
```

If a Fire OS update switches it off, enable it again the same way.

**Don't force-stop the launcher** (`am force-stop`, or Force stop in Fire OS's app settings):
Android responds by removing its accessibility service from the enabled list, which turns the
remote shortcut and the Home redirect off until you enable it again.

## Now playing card (notification listener)

Android only lets an app see other apps' media sessions (what Spotify, YouTube etc. are playing)
while it has an allowed notification listener. Fire OS has no on-screen switch for that either,
so it is allowed over adb. The launcher's listener never reads notifications; it exists only to
unlock the media sessions.

```sh
adb shell cmd notification allow_listener com.shivam.tvlauncher/com.shivam.tvlauncher.MediaListenerService
```

To undo it (the card then simply never appears):

```sh
adb shell cmd notification disallow_listener com.shivam.tvlauncher/com.shivam.tvlauncher.MediaListenerService
```

If a Fire OS update or a reinstall drops it, run the allow command again.

## Backing up and restoring your setup

Your pins and their order, hidden apps, All apps order, console tiles, theme, animations and
remote shortcut can be saved as one JSON file. (Backups from before console tiles, format
version 1, still import; they leave your current console tiles as they are.) Fire OS doesn't let
adb into the app's folder, and the launcher has no storage permission, so the file goes through a
small bridge in the launcher that only answers adb.

Back up (on the TV: gear → Launcher settings → More settings → Export backup, then on the computer):

```sh
adb shell content read --uri content://com.shivam.tvlauncher.backup/launcher-backup.json > launcher-backup.json
```

Restore (on the computer, then on the TV: gear → Launcher settings → More settings → Import backup):

```sh
adb shell content write --uri content://com.shivam.tvlauncher.backup/launcher-backup.json < launcher-backup.json
```

Import checks the whole file first and changes nothing if anything is wrong; a valid file shows
what it contains and asks before replacing your setup. Apps in the backup that aren't installed
are kept, so they reappear if you install them later. The file is plain JSON; editing it by hand
is fine as long as it stays valid. Uninstalling the launcher deletes the copy on the TV, so keep
the one on your computer.

## What this depends on

- **ADB debugging must stay on.** It is under Settings → My Fire TV → Developer Options (the
  launcher's gear menu opens Fire OS Settings directly). Fire OS updates sometimes switch it off;
  if `adb connect` is refused, turn it back on there.
- **Your TV's IP address** (`<TV-IP>` above): look it up under the Wi-Fi button in this
  launcher's top bar, in Fire OS under Settings → My Fire TV → About → Network, or in your
  router's client list. It can change if your router hands out a new one.
- If `adb connect` asks for authorisation, accept the "Allow USB debugging?" prompt on the TV.
  If it times out, the TV is probably asleep.

## Checking the current state

```sh
adb shell pm list packages -d | grep com.amazon.tv.launcher   # listed = disabled
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility | grep -E "Bound services|Enabled services"
```
