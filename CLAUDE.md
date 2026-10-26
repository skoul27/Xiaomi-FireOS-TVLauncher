# TV Launcher

A custom home-screen launcher (Kotlin, Jetpack Compose for TV) for a Fire TV
(model AFTNE4E43F, Fire OS / Android 11). Package: `com.shivam.tvlauncher`.
Personal, sideloaded app; not published to any store.

See `RECOVERY.md` for everything TV-side: the Home-button workaround, the
remote-shortcut accessibility service, backup/restore over adb, and how to get
Amazon's launcher back.

## Two development PCs

This project is developed on two Windows PCs and synced through GitHub.
Pull before starting work, push when done.

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
- `local.properties` (`sdk.dir=...`) is per-PC and git-ignored

## Build and install

```powershell
.\gradlew.bat assembleDebug
adb connect <TV-IP>:5555
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

- Call the wrapper as `.\gradlew.bat` or by full path; on at least one PC, `cmd`
  will not run a bare `gradlew.bat` from the current directory.
- If `adb connect` is refused, ADB debugging on the TV has been switched off
  (Fire OS updates do this): Settings → My Fire TV → Developer Options. If it times
  out, the TV is probably asleep. A new PC must be accepted on the TV's
  "Allow USB debugging?" prompt.
- The release build is also signed with the debug key (see `app/build.gradle.kts`),
  so release and debug can install over each other.
