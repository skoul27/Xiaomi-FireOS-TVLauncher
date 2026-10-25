# Recovery: getting Amazon's launcher back

Use this if the Home button stops working, shows a blank screen, or you simply want the stock
Fire TV home back. Run from a PC on the same network as the TV.

```sh
adb connect <TV-IP>:5555
adb shell pm enable com.amazon.tv.launcher
```

Then press Home on the remote. Amazon's launcher has priority 950 for HOME, so it takes over
again as soon as it is enabled. Nothing else needs undoing: this launcher never changes system
settings.

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
```
