# Quick panel without Accessibility (Huawei-safe) - plan and tracker

This file travels inside every zip of this plan. It says what is DONE, what is LEFT, what is known to be limited, and how to test.
(Last update: step 7 of 8.)

## The one fact everything depends on
A window made with "Draw over other apps" is shown ABOVE apps but BELOW the status bar and the notification shade (Android's own rule for this window type).
So without the Accessibility service the phone's own status bar can NOT be replaced or covered: a pull-down that starts ON the status bar always goes to the phone.
The plan therefore gives the person several ways to open the launcher's Quick panel that do not need the status bar. Each way has its own On / Off switch.
(You asked for "a new bar on top that detects the swipe": that bar can only sit right UNDER the status bar - step 2.)
Not included, as you asked: no navigation bar.

## The 8 steps
| # | Step | Status |
|---|------|--------|
| 1 | Foundation: "Quick panel over other apps" (Draw over other apps + a small foreground service), master On / Off, permission help, Show-now test, the service notification opens the panel | DONE |
| 2 | Top handle: a thin strip right under the status bar; swipe down on it opens the panel (position, width, height, visible pill or invisible, On / Off) | DONE |
| 3 | Quick Settings tile ("Quick panel" tile in the phone's own shade) + App shortcut + notification options (hide / show the notification action) | DONE |
| 4 | Floating button (draggable, snaps to the edge, size, opacity, On / Off) | DONE |
| 5 | Smart rules: hide the handle / button in full-screen apps and videos, when the keyboard is open, on the lock screen, when the screen is off; landscape on / off | DONE |
| 6 | More triggers: double-tap and long-press on the handle, side swipes (left half = Notifications page, right half = Quick panel), optional shake to open | DONE |
| 7 | Force / keep alive: start after reboot, restart after the phone's cleaner kills it, battery and "protected apps" help (Huawei screens), a Setup check page with green / red lines, one-time adb commands for the strongest settings | **DONE (this zip)** |
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

## Step 7 - what is in it
- Settings > Quick panel > **Setup check** (green / red lines with a tap to fix), **Keep it running** (start after reboot / after being stopped + 15-minute check, adb commands). New `KeepAlive.kt`; changed `OverlayPanel.kt`, `HomeSettingsActivity.kt`, `AndroidManifest.xml`.

## Step 6 - what is in it
- Settings > Quick panel > **More ways to open the panel**: double-tap and long-press on the top handle (Nothing / Quick panel / Notifications), side swipes (left half = Notifications, right half = Quick panel), Shake to open (Gentle / Normal / Firm; motion sensor only while the screen is on). New `MoreTriggers.kt`; changed `TopHandle.kt`, `OverlayPanel.kt`, `HomeSettingsActivity.kt`.

## Step 5 - what is in it
- Settings > Quick panel > **Smart rules**: hide the handle and the floating button in full-screen apps / videos, while the keyboard is open, on the lock screen, while the screen is off, and (optional) in landscape. Each rule has its own switch. They never hide the panel, the notification, the tile or the shortcut.
- Done with an invisible 1-pixel overlay window that takes no touches and reads nothing; it only gets the bar sizes Android gives every window. No new permission.
- New `SmartRules.kt`; changed `OverlayPanel.kt`, `HomeSettingsActivity.kt`.

## Step 4 - what is in it
- Settings > Quick panel > **Floating button** (off by default): tap opens the panel, drag moves it, it glides to the nearest edge after a drag. Size, opacity, stick-to-edge, vibrate, reset position. New `FloatingButton.kt`; changed `OverlayPanel.kt`, `HomeSettingsActivity.kt`.

## Step 3 - what is in it
- Settings > Quick panel > **Quick Settings tile and App shortcut**: add the "Quick panel" tile in the phone's shade (pencil button); long-press the app icon for "Quick panel" / "Notifications" shortcuts. Both open the panel through the invisible `PanelShortcutActivity`; they need the step 1 switch and "Draw over other apps" (otherwise the launcher settings open with a message).
- Settings > Quick panel > **The Quick panel notification**: tap opens the Quick panel or the Notifications page; the "Settings" button can be hidden; "Hide the notification" opens the phone's category screen (the service needs the notification, so the launcher can not remove it by itself).
- New `PanelTile.kt`, `shortcuts.xml`, `strings_panel.xml`, `ic_shortcut_panel.xml`. Changed: `AndroidManifest.xml`, `OverlayPanel.kt`, `HomeSettingsActivity.kt`.

## Step 2 - what is in it
- Settings > Quick panel > **Top handle**: On / Off, Show the handle (pill or invisible), Place (Middle / Left / Right), Width (20-100%), Height of the touch area (8-48 dp), Distance below the status bar (0-60 dp), Vibrate on swipe.
- A swipe down on the strip (about 1.5x the touch slop, mostly down) opens the Quick panel over the app you are in. The strip is a small overlay window made by the same service as step 1, so it needs "Quick panel over other apps" switched on and "Draw over other apps" allowed.
- While the panel is open the strip is removed; it comes back when the panel is gone. Changes in Settings move / resize the strip at once.
- New file `TopHandle.kt` (`TopHandle` settings, `TopHandleView`). Changed: `OverlayPanel.kt`, `HomeSettingsActivity.kt`.
- Not in this step (later steps): hiding the strip in full-screen apps / on the keyboard / on the lock screen (step 5), double-tap / long-press on the strip (step 6).

## Test list for step 7 (on the phone)
1. Open Settings > Quick panel: the Setup check shows ticks / crosses; fix every cross by tapping it and coming back (the lines turn green).
2. "Auto-launch / Protected apps": the Huawei screen opens (or this app's info screen): allow auto-launch, secondary launch and run in background.
3. Restart the phone, unlock it: the panel notification comes back by itself (if not, tell me the Android / EMUI version).
4. Swipe the launcher away from Recents (or use the phone's cleaner): within a few seconds the notification returns. If not: wait 15 minutes for the check, and tell me.
5. "Copy the adb commands" and run them from a computer once if the service keeps dying.

## Test list for step 6 (on the phone)
1. Set "Double-tap on the handle" to Quick panel: two quick taps on the handle open it; one tap does nothing.
2. Set "Long-press" to Notifications: hold the handle ~half a second: the Notifications page opens. Moving the finger first cancels it.
3. "Side swipes" on: swipe down on the left half of the handle: Notifications page; right half: Quick panel. Off: both halves open the Quick panel.
4. "Shake to open" on: shake the phone twice, firmly: the panel opens. Not on the lock screen. Try Gentle / Firm; tell me if it fires while walking.

## Test list for step 5 (on the phone)
1. Switch on the handle and / or the floating button. Open a video in full screen (YouTube, gallery): both go away; leave full screen: they are back.
2. Tap a text field so the keyboard opens: both go away; close the keyboard: back. (If nothing happens, tell me: the phone does not report the keyboard to other apps.)
3. Press the power button: screen off. Wake it: on the lock screen nothing is drawn; after unlocking they are back.
4. Turn the phone sideways: with "Show in landscape" off both are hidden, with it on they stay (and are placed correctly).
5. Switch a rule off in Settings: that rule stops hiding at once. The notification still opens the panel in every case.

## Test list for step 4 (on the phone)
1. Switch on "Floating button" (step 1 switch on, "Draw over other apps" allowed): a round button appears at the right edge.
2. Tap it in any app: the panel opens; close it: the button is back in the same place.
3. Drag it: it follows the finger and glides to the nearest edge on release. Leave Settings and come back: same place. "Reset position" puts it back.
4. Change size / opacity: it changes at once. "Stick to the edge" off: it stays where you drop it.

## Test list for step 3 (on the phone)
1. Pull the shade down fully, tap the pencil / edit button, drag the "Quick panel" tile into the list. (Huawei: tiles are added from the shade's edit screen; a few EMUI versions do not allow third-party tiles - tell me.)
2. With the step 1 switch on, tap the tile in any app: the shade closes and the panel opens. With the switch off: the launcher settings open with a message.
3. In another launcher (or the phone's), long-press this app's icon: "Quick panel" and "Notifications" appear; each opens the right page.
4. Settings > "The Quick panel notification": change "Tap opens" and the "Settings" button: the notification changes at once. "Hide the notification" opens the phone's own screen for it.

## Test list for step 2 (on the phone)
1. Switch on "Quick panel over other apps" (step 1 test list). A pill appears right under the status bar.
2. In any app swipe down on the pill: the Quick panel opens. Close it: the pill is back.
3. Change Place / Width / Height / Distance: the strip moves at once. "Show the handle" off: no pill, but the swipe still works on the same spot.
4. Switch "Top handle" off: the strip is gone; the notification way still works.
5. Known: the strip sits over the app, so it takes touches on its own area (that is the point); make it thinner or move it if it covers a button of an app.

## Test list for step 1 (on the phone)
1. Settings > Quick panel > switch on "Quick panel over other apps". Allow "Draw over other apps", come back. A small notification "Quick panel" appears (if you allowed notifications).
2. Press **Show panel now**: the Quick panel opens over the settings. Close it with Back or a swipe up.
3. Go to another app (Chrome...). Pull the shade down, tap the "Quick panel" notification: the panel opens over that app. Tap the torch / a slider: they work. Back closes it.
4. Swipe left in the panel: the Notifications page; swipe right: back.
5. Switch off the master switch: the notification goes away; the panel no longer opens.

## Known limits (honest)
- The phone's own status bar can not be covered without Accessibility (see above). Step 2 puts the handle under it.
- Smart rules: the keyboard rule is best effort (some phones do not report the keyboard to other apps' windows); the full-screen rule needs the phone to hide the status bar in that app.
- The panel does not show over the lock screen, and on some phones not over full-screen videos / games (the phone hides overlays there).
- Huawei / EMUI may stop the service to save battery: step 7 adds the help for that. Until then, if the notification disappears, open the launcher once.
- The picture of the app behind the panel is not blurred (that needed Accessibility); only the panel's own veil.
- Not built or run on a phone by the author of this zip: please test the list above and send what fails.

## Ideas kept for later steps (tell me which you want)
- Per-app exclusion of the handle / button (needs "Usage access", a sensitive permission on stores: maybe not).
- A widget on the Home screen that opens the panel; a Home-screen gesture "pinch" to open the panel.
- Shizuku support (this lineage of the code does not have it).
