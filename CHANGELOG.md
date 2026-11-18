# Changelog

What changed in each release. Download any version from
[Releases](https://github.com/skoul27/Xiaomi-FireOS-TVLauncher/releases); each one installs over
the previous and keeps your settings.

## 0.2.2 (2026-11-18)

### New
- **Pay to play** for HDMI inputs. A paid input shows a plan picker and a UPI QR code before it
  opens. You confirm or decline from your phone, on an owner page the TV serves on your home
  network. The page has Live, History and Setup tabs, live countdowns, payment alerts, today's
  takings, a price editor, and can be added to your phone's home screen.
- From the phone you can also set a custom amount and time, start a session for cash, give free
  time or end a session.
- Paid time counts down on screen and the input closes when it runs out. Changing the TV's clock
  can't add time, and a reboot pauses the timer instead of losing it.
- An admin code gives unlimited play, and while it's set, Fire OS's own Live TV player (Alexa,
  the Inputs menu, HDMI-CEC) and its settings bounce back to the launcher, so the gate can't be
  bypassed. The launcher's own settings ask for the code too (10-minute unlock).

## 0.2.1 (2026-10-28)

### Fixed
- Long dates ("Wednesday, 28 October") no longer run underneath the Now playing pill: the clock
  now shows the short form ("Wed, 28 Oct").

## 0.2.0 (2026-10-28)

First public release.

- **My Apps**: pin, reorder and hide apps; All apps (with A–Z search) and Hidden apps pop-ups;
  long-press and Menu-button menus for Remove / Move / Hide / App info / Uninstall / Close app.
- **Consoles**: a tile per HDMI input, found at runtime, shown as PlayStation, Xbox, Nintendo or
  Other, opening straight to that input.
- **Top bar**: clock, date and local weather; Now playing card with artwork, progress and
  playback controls; Wi-Fi, Cast and Settings buttons.
- **Backgrounds**: dynamic (tinted by the focused app), four built-in wallpapers, or your own
  photo sent from your phone via a QR code.
- **Home button** redirect from Amazon's home screen, and a remote color-button shortcut.
- **Themes**, animation settings, and backup and restore of your whole setup over adb.
