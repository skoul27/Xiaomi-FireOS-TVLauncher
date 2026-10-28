# TV Launcher

A custom home screen for Amazon Fire TV, written in Kotlin with Jetpack Compose for TV. It
replaces Fire OS's ad-filled home with a calm, glass-styled grid of your own apps.

Built for and tested on one Fire TV (Fire OS on Android 11, model AFTNE4E43F). Other Fire TV
and Android TV devices running Android 9 or later should work, but haven't been tested.

![Home screen](docs/screenshots/home.jpg)

| Now playing | All apps | Settings |
|---|---|---|
| ![Now playing card](docs/screenshots/now-playing.jpg) | ![All apps](docs/screenshots/all-apps.jpg) | ![Settings](docs/screenshots/settings.jpg) |

## Features

- **My Apps**: pin, reorder and hide apps; All apps and Hidden apps as pop-ups. Long-press OK on
  a tile for Remove / Move / Hide, the Menu button for App info / Uninstall / Close app.
- **Consoles**: a tile per HDMI input, found at runtime, shown as PlayStation, Xbox, Nintendo or
  Other (or hidden), opening straight to that input.
- **Top bar**: clock and date with the local weather, a Now playing card for whatever Spotify,
  YouTube etc. are playing (with playback controls), Wi-Fi status, Cast and Settings.
- **Backgrounds**: a dynamic background tinted from the focused app, four built-in wallpapers, or
  your own photo, chosen from your phone by scanning a QR code (the launcher serves a one-time
  upload page on your local network while that panel is open).
- **Remote shortcut**: open the launcher from any app with a color button on the remote.
- **Themes**: accent colors and animation settings.
- **Backup and restore** of your whole setup as one JSON file, over adb.

## Install

Download the ready-made APK (quickest), or build it yourself from this source code. Either way
you need a computer with [adb](https://developer.android.com/tools/releases/platform-tools)
once, for the one-time setup further down.

### Prepare the TV

1. Turn on **Settings → My Fire TV → Developer Options → ADB debugging**. If Developer Options
   is hidden, open Settings → My Fire TV → About and press OK on the device name seven times.
2. Find the TV's IP address under Settings → My Fire TV → About → Network.

### Option A: download the APK

Each [release](../../releases/latest) has `tv-launcher.apk`, built from the tagged source.

**From a computer** on the same network as the TV:

```sh
adb connect <TV-IP>:5555
adb install -r tv-launcher.apk
```

Accept the "Allow USB debugging?" prompt on the TV the first time.

**Or directly on the TV:** install the free **Downloader** app from the Amazon Appstore, allow it
under Settings → My Fire TV → Developer Options → Install unknown apps, then enter this address
in Downloader and choose Install:

```
https://github.com/skoul27/Xiaomi-FireOS-TVLauncher/releases/latest/download/tv-launcher.apk
```

The release notes list the APK's SHA-256 checksum and its signing certificate, if you want to
check what you downloaded. To update, install a newer release the same way; your settings are
kept.

### Option B: build it yourself

About 15 minutes the first time.

**1. Get the tools.** Easiest: install [Android Studio](https://developer.android.com/studio)
(Windows, macOS or Linux), which includes the JDK, the Android SDK and adb; open it once and
finish the setup wizard with the standard options. Or, command line only: install JDK 17 or later
(for example [Eclipse Temurin](https://adoptium.net)) and the
[Android command-line tools](https://developer.android.com/studio#command-line-tools-only), then:

```sh
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --licenses
```

and set `ANDROID_HOME` to the SDK folder, with its `platform-tools` folder (where `adb` is) on
your `PATH`.

**2. Download the source:**

```sh
git clone https://github.com/skoul27/Xiaomi-FireOS-TVLauncher.git
cd Xiaomi-FireOS-TVLauncher
```

Or use **Code → Download ZIP** on this page and unzip it.

**3. Build.** In Android Studio: File → Open, choose the project folder, wait for the Gradle
sync to finish (accept any prompt to install SDK packages), then Build → Build App Bundle(s) /
APK(s) → Build APK(s). The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

From a terminal in the project folder:

```sh
./gradlew assembleRelease          # Windows: .\gradlew.bat assembleRelease
```

The APK is at `app/build/outputs/apk/release/app-release.apk`. (To use Android Studio's JDK from
a terminal, point `JAVA_HOME` at its `jbr` folder.)

**4. Install** it with the adb commands from Option A, using the path of your APK.

Your build is signed with your computer's Android debug key (`~/.android/debug.keystore`, or
`%USERPROFILE%\.android\debug.keystore` on Windows), which the build creates the first time.
**Keep that file:** Android only installs an update signed with the same key as the installed
app. For the same reason, your own build and the downloaded release can't be installed over each
other: switching between them means uninstalling first, which deletes the launcher's settings
(export a backup first, see [RECOVERY.md](RECOVERY.md)).

After installing, open **TV Launcher** from Your Apps & Channels and do the one-time setup below.

## One-time setup: Home button and Now playing

Fire OS always sends the Home button to Amazon's own launcher and won't let you disable it or
pick another Home app. TV Launcher works around this with an accessibility service: when
Amazon's home screen opens, it opens TV Launcher over it (you'll see Amazon's home flash for a
moment). The same service runs the remote color-button shortcut. It only looks at which button
was pressed and which app opened; it can't read the screen and keeps no record.

Fire OS has no on-screen switch for third-party accessibility services, so you turn it on once
over adb:

```sh
adb shell settings get secure enabled_accessibility_services
# If that printed null or nothing:
adb shell settings put secure enabled_accessibility_services com.shivam.tvlauncher/.ShortcutKeyService
adb shell settings put secure accessibility_enabled 1
```

If it printed a list, append `:com.shivam.tvlauncher/.ShortcutKeyService` to it instead of
replacing it. The launcher's Settings panel shows the exact commands for your TV when the
service is off.

The Now playing card needs one more adb command:

```sh
adb shell cmd notification allow_listener com.shivam.tvlauncher/com.shivam.tvlauncher.MediaListenerService
```

Fire OS updates sometimes switch these off, or switch ADB debugging off; run them again if so.
Don't force-stop the launcher from Fire OS's app settings: Android turns the accessibility
service off when you do.

**[RECOVERY.md](RECOVERY.md)** covers everything else: getting Amazon's home back, turning the
service off, backup and restore, and checking the current state.

## Privacy

The launcher has no analytics and no accounts. Its only network requests are for the weather:
your approximate location from your IP address via [ipwho.is](https://ipwho.is), then the
forecast from [Open-Meteo](https://open-meteo.com). The photo upload page is only served on your
local network, behind a random link, while the Choose a photo panel is open.

## Credits and trademarks

- [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans), SIL Open Font License 1.1
  ([third_party/plus-jakarta-sans/OFL.txt](third_party/plus-jakarta-sans/OFL.txt)).
- [ZXing](https://github.com/zxing/zxing) for QR codes, Apache License 2.0.
- Xbox and Nintendo Switch icon paths from [Simple Icons](https://simpleicons.org) (CC0).
  PlayStation symbol traced from the PS5 logo on Wikimedia Commons.
- Weather data by [Open-Meteo](https://open-meteo.com) (CC BY 4.0).

PlayStation is a trademark of Sony Interactive Entertainment. Xbox is a trademark of Microsoft.
Nintendo Switch is a trademark of Nintendo. Fire TV and Fire OS are trademarks of Amazon. This
project is not affiliated with or endorsed by any of them; the console marks are used only to
label the HDMI input a console is connected to.

## License

[MIT](LICENSE) © skoul27
