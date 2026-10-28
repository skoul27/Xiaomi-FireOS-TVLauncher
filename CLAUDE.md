# TV Launcher

A custom home-screen launcher (Kotlin, Jetpack Compose for TV) for a Fire TV
(model AFTNE4E43F, Fire OS / Android 11). Package: `com.shivam.tvlauncher`.
Sideloaded app, not published to any store; open source (MIT) on GitHub.

See `RECOVERY.md` for everything TV-side: the Home-button workaround, the
remote-shortcut accessibility service, backup/restore over adb, and how to get
Amazon's launcher back.

## Two development PCs

This project is developed on two Windows PCs and synced through GitHub
(`skoul27/Xiaomi-FireOS-TVLauncher`). Each PC has its own clone at
`C:\code\tv-launcher`; GitHub is the source of truth, not either PC's folder.

- **Start of every session:** `git pull` before touching anything. If the working
  tree has uncommitted changes, stop and ask the owner before pulling.
- **End of every session (or when the owner says "commit and push"):** commit with
  the repo-local identity (skoul27, noreply address) and `git push`, so the other PC
  can pick up from here.
- If a pull hits a conflict, resolve it keeping both PCs' intent and say what was
  merged; never discard the other side's work.

**Both PCs must sign with the same debug keystore.** The app on the TV is signed
with the key whose certificate SHA-256 is
`c732dfc70afeb91b14d9130ab56d41db831e23b6baddb61bffbdefa11c80ef9b`.
A build signed with any other key fails to install over it with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the only way past that is uninstalling,
which wipes the launcher's data and the accessibility-service binding.

- The keystore lives at `%USERPROFILE%\.android\debug.keystore` on each PC. A copy is
  kept on the owner's Google Drive. It is never committed to this repo.
- Android Gradle Plugin silently creates a new keystore on the first build if none
  exists. On a fresh PC, put the shared keystore in place *before* building.
- To check a keystore:
  `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -storepass android`
- To check a built APK:
  `%ANDROID_HOME%\build-tools\35.0.0\apksigner.bat verify --print-certs app\build\outputs\apk\debug\app-debug.apk`

## Toolchain

- JDK 17 (Eclipse Temurin), `JAVA_HOME` set
- Android SDK: platform 35, build-tools 35.0.0, platform-tools; `ANDROID_HOME` set
- Gradle 8.11.1 through the wrapper, AGP 8.7.3, Kotlin 2.1.0 (versions in `gradle/libs.versions.toml`)
- `local.properties` is per-PC and git-ignored. It holds `sdk.dir=...` and the TV's address
  as `tv.ip=...`, which is kept out of this public repo. If `tv.ip` is missing on a PC, ask
  the owner for it (or read it on the TV: Settings → My Fire TV → About → Network, or the
  launcher's Wi-Fi button) and add the line.

## Build and install

```powershell
.\gradlew.bat assembleDebug
$tv = (Select-String -Path local.properties -Pattern '^tv\.ip=(.+)$').Matches[0].Groups[1].Value
adb connect "${tv}:5555"
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

- Call the wrapper as `.\gradlew.bat` or by full path; on at least one PC, `cmd`
  will not run a bare `gradlew.bat` from the current directory.
- If `adb connect` is refused, ADB debugging on the TV has been switched off
  (Fire OS updates do this): Settings → My Fire TV → Developer Options. If it times
  out, the TV is probably asleep. A new PC must be accepted on the TV's
  "Allow USB debugging?" prompt.
- **Never `am force-stop com.shivam.tvlauncher`.** Android responds by removing the app's
  accessibility service from `enabled_accessibility_services`, which turns the remote shortcut
  and the Home redirect off (re-enable with the commands in `RECOVERY.md`). `adb install -r`
  is fine. To restart the UI, open it with `am start`, or kill only the process if you must.
- Shell-injected keys (`adb shell input keyevent KEYCODE_PROG_BLUE`) don't reach the
  accessibility key filter, so the remote shortcut can only be tested with the real remote.
  The Home redirect can be tested over adb (`input keyevent KEYCODE_HOME` from another app).
- Release builds use the key in `keystore.properties` (git-ignored) when that file exists,
  and the debug key otherwise (see `app/build.gradle.kts`). On both PCs today there is no
  `keystore.properties`, so release and debug install over each other.

## Public repo

This repo is public (decided 2026-10-28: one repo, not a separate public copy). Anything
committed here is visible to everyone, so keep secrets, keystores and personal details out.

- **History:** rewritten on 2026-10-28 before going public (the Gmail author address and
  the TV's IP removed from every commit). A clone made before that date has the old history:
  never merge or push it. If `git pull` reports divergent histories, check for unpushed work,
  save it as a patch, then `git fetch` and `git reset --hard origin/master` and reapply it.
  Keep committing with the repo-local identity (skoul27, noreply address).
- **License:** MIT, copyright skoul27 (`LICENSE`). Plus Jakarta Sans is OFL
  (`third_party/plus-jakarta-sans/OFL.txt`); credits are listed in `README.md`.
- **Package ID** stays `com.shivam.tvlauncher`, so the owner's TV keeps updating in place.
- **Release key:** if public APKs on GitHub Releases are ever signed with a new release key,
  they can't install over the owner's debug-signed copy (same package, different key), and
  vice versa. Either sign releases with the shared debug keystore (via `keystore.properties`)
  or accept a one-time uninstall on the TV (export a backup first).
- **Docs:** `README.md` is for users (features, sideloading, the Fire OS Home-button caveat,
  privacy, credits). `RECOVERY.md` is written for any user with `<TV-IP>`; TV-specific notes
  belong in this file, not there.
- The About panel (gear → About: version, developer Shivam Koul, koul@duck.com) stays.
- **Console marks:** PlayStation (traced from Wikimedia), Xbox and Nintendo Switch (Simple
  Icons 11.0.0, CC0 paths) stay, with a trademark disclaimer in `README.md`. They label the
  HDMI input only; don't use them anywhere else (app icon, banner, store art).
