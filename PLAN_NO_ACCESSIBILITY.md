# Quick panel without Accessibility (Huawei-safe) - plan and tracker

This file travels inside every zip of this plan. It says what is DONE, what is LEFT, what is known to be limited, and how to test.
(Last update: step 1 of 8.)

## The one fact everything depends on
A window made with "Draw over other apps" is shown ABOVE apps but BELOW the status bar and the notification shade (Android's own rule for this window type).
So without the Accessibility service the phone's own status bar can NOT be replaced or covered: a pull-down that starts ON the status bar always goes to the phone.
The plan therefore gives the person several ways to open the launcher's Quick panel that do not need the status bar. Each way has its own On / Off switch.
(You asked for "a new bar on top that detects the swipe": that bar can only sit right UNDER the status bar - step 2.)
Not included, as you asked: no navigation bar.

## The 8 steps
| # | Step | Status |
|---|------|--------|
| 1 | Foundation: "Quick panel over other apps" (Draw over other apps + a small foreground service), master On / Off, permission help, Show-now test, the service notification opens the panel | **DONE (this zip)** |
| 2 | Top handle: a thin strip right under the status bar; swipe down on it opens the panel (position, width, height, visible pill or invisible, On / Off) | left |
| 3 | Quick Settings tile ("Quick panel" tile in the phone's own shade) + App shortcut + notification options (hide / show the notification action) | left |
| 4 | Floating button (draggable, snaps to the edge, size, opacity, On / Off) | left |
| 5 | Smart rules: hide the handle / button in full-screen apps and videos, when the keyboard is open, on the lock screen, when the screen is off; landscape on / off | left |
| 6 | More triggers: double-tap and long-press on the handle, side swipes (left half = Notifications page, right half = Quick panel), optional shake to open | left |
| 7 | Force / keep alive: start after reboot, restart after the phone's cleaner kills it, battery and "protected apps" help (Huawei screens), a Setup check page with green / red lines, one-time adb commands for the strongest settings | left |
| 8 | Huawei store build: the release build has NO Accessibility service in its manifest, the Accessibility rows are hidden there, the policy / AppGallery notes are updated, final test list | left |

## Step 1 - what is in it
- Settings > Quick panel > **"In every app, no Accessibility (recommended)"**:
  - **Quick panel over other apps** (master switch). Turning it on asks for "Draw over other apps" (Android's own screen) and, on Android 13+, for notifications.
  - **Draw over other apps** row: shows Allowed / Not allowed; tap opens the phone's screen for it.
  - **Show panel now**: opens the Quick panel over the current screen (test).
- While the switch is on a tiny foreground service runs. Its **notification** ("Quick panel", lowest importance, no sound): **tap = open the Quick panel** over whatever app you are in, action **Settings** = open the launcher's settings.
- Same panel as before (buttons, pages, sliders, notifications page, colours, edit look). Back closes it; screen off closes it.
- The Accessibility way (Settings > Quick panel > "In every app, with Accessibility") is unchanged; both can be on.
- Buttons that need Accessibility to be pressed (Wi-Fi, Bluetooth, mobile data, flight mode...): with Accessibility off, a tap opens the phone's own screen for that button (the panel closes). The buttons the launcher can switch itself (torch, sound mode, auto rotate and Do not disturb once allowed) switch in place.
- Phone buttons found by the scan (vivoshare, Speed up...) need Accessibility: without it a tap says so. No scan toast is shown when Accessibility is not used.
- New files: `OverlayPanel.kt` (`OverlayPanel`, `PanelOverlayService`), `res/drawable/ic_stat_panel.xml`. Changed: `AndroidManifest.xml`, `HomeSettingsActivity.kt`, `LauncherActivity.kt` (keeps the service matching the switch), `QuickSystem.kt`, `QuickPanel.kt`, `build.gradle.kts`.
- New permissions: SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS.

## Test list for step 1 (on the phone)
1. Settings > Quick panel > switch on "Quick panel over other apps". Allow "Draw over other apps", come back. A small notification "Quick panel" appears (if you allowed notifications).
2. Press **Show panel now**: the Quick panel opens over the settings. Close it with Back or a swipe up.
3. Go to another app (Chrome...). Pull the shade down, tap the "Quick panel" notification: the panel opens over that app. Tap the torch / a slider: they work. Back closes it.
4. Swipe left in the panel: the Notifications page; swipe right: back.
5. Switch off the master switch: the notification goes away; the panel no longer opens.

## Known limits (honest)
- The phone's own status bar can not be covered without Accessibility (see above). Step 2 puts the handle under it.
- The panel does not show over the lock screen, and on some phones not over full-screen videos / games (the phone hides overlays there).
- Huawei / EMUI may stop the service to save battery: step 7 adds the help for that. Until then, if the notification disappears, open the launcher once.
- The picture of the app behind the panel is not blurred (that needed Accessibility); only the panel's own veil.
- Not built or run on a phone by the author of this zip: please test the list above and send what fails.

## Ideas kept for later steps (tell me which you want)
- Per-app exclusion of the handle / button (needs "Usage access", a sensitive permission on stores: maybe not).
- A widget on the Home screen that opens the panel; a Home-screen gesture "pinch" to open the panel.
- Shizuku support (this lineage of the code does not have it).
