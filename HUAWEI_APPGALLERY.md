# Huawei AppGallery build - OneUI 9 Launcher (Huawei plan, step 8 of 8)

## What the store build is
The **release build type** (`bash release.sh`, or `assembleRelease` / `bundleRelease`) is the store build:
- **No Accessibility service.** `app/src/release/AndroidManifest.xml` removes `PanelAccessibilityService` from the merged manifest (`tools:node="remove"`).
  `app/src/release/res/values/bools_store.xml` sets `store_no_accessibility = true`, so the app hides every Accessibility row / text / policy line and never looks for the service.
- No `WRITE_SECURE_SETTINGS` (step 36), `qs_other_windows = false` (the store build never looks at other apps' windows).
- The **debug build** (`bash build.sh`) keeps the optional Accessibility way, for your own phone.

## Check the store build BEFORE you upload (2 minutes)
1. Build: `bash release.sh` (APK) or the GitHub Actions release job.
2. Look inside the APK for the service (must print nothing):
   `aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/release/app-release.apk | grep -i accessib`
   (or open the merged manifest: `app/build/intermediates/merged_manifests/release/.../AndroidManifest.xml`, search "Accessibility").
3. Install it, open Settings > Quick panel: there must be NO "In every app, with Accessibility" section, and Settings > Privacy policy > Permissions must have no Accessibility line.

## How the Quick panel is opened in this build
Draw over other apps + a small foreground service (notification). Ways to open: notification tap, top handle (swipe / double-tap / long-press / side swipes), floating button, Quick Settings tile, app shortcuts, shake. Smart rules hide the handle and the button in full-screen apps, with the keyboard, on the lock screen, when the screen is off, in landscape. Keep-alive: start after restart, restart after stop, a ~15-minute check, Setup check page.

## Permissions to declare / explain (AppGallery "App permission" and review notes)
- `SYSTEM_ALERT_WINDOW` (Draw over other apps): "Used only to draw the launcher's own Quick settings panel, a thin swipe handle and an optional floating button over the app the user is using, when the user switches on 'Quick panel over other apps'. Nothing on the screen is read, stored or sent."
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` (subtype: "Keeps the Quick panel window ready to open over other apps"): a small service with one low-importance notification.
- `POST_NOTIFICATIONS`: shows that notification (tap = open the panel).
- `RECEIVE_BOOT_COMPLETED`: starts that service again after the phone restarts (switchable: Settings > Quick panel > Keep it running).
- Alarms (inexact, no permission): restart the service a few seconds after it was stopped and a ~15-minute check.
- Package visibility (`<queries>` for `android.service.quicksettings.action.QS_TILE`, step 60): lets the app see the names of apps that offer Quick Settings tiles, only to list them as optional "Phone buttons" that open the app or its tile settings. The list is used on the phone only. The first time the Quick panel opens, up to 8 of these tiles are added as buttons by themselves (Step 78). The phone's own tile list (secure setting `sysui_qs_tiles`) is read once at that moment, and again only when the person taps "Match my phone's panel" / "Show the phone's tile list"; it is never stored beyond the buttons on the panel and never sent. No QUERY_ALL_PACKAGES.
- Motion sensor (no permission): only for the optional "Shake to open", only while the screen is on; readings are not stored or sent.
- The rest is unchanged from `PLAY_STORE.md` (notification listener for badges, lock-only device admin, REQUEST_DELETE_PACKAGES, EXPAND_STATUS_BAR, wallpaper, Wi-Fi/Bluetooth state, WRITE_SETTINGS, ACCESS_NOTIFICATION_POLICY). The app has no INTERNET permission.

## Privacy policy
`docs/privacy-policy.html` and the in-app text (`LegalText.kt`) are updated (effective date October 31, 2026): new rows for "Display over other apps / foreground service / notification / start after restart" and "Motion sensor". The Accessibility row says it is not in the AppGallery build, and the in-app list leaves it out in the store build. Push the `docs/` folder so the public page matches.

## Known limits to tell reviewers / users honestly
- The panel can not cover the phone's own status bar (Android's rule for overlay windows); that is why the handle sits under it and the other ways exist.
- No panel over the lock screen; some phones hide overlays over full-screen videos / games.
- Huawei's cleaner may stop the service: Setup check page + Auto-launch / Protected apps help + optional adb commands.
- Buttons that need Accessibility to be switched (Wi-Fi, Bluetooth, mobile data, flight mode...) open the phone's own screen in this build; torch, sound mode, auto rotate and Do not disturb switch in place.
- Not built or run on a phone by the author of these zips.

## Final test list (on a Huawei phone, store build)
1. Install the release APK. Press Home, choose the launcher as default.
2. Settings > Quick panel: no Accessibility section. Switch on "Quick panel over other apps", allow "Draw over other apps" and notifications: the Setup check lines turn green (battery optimisation and auto-launch: fix by hand).
3. Open Chrome: notification tap, top handle swipe, floating button tap, tile (after adding it in the shade), app shortcut all open the panel. Torch / sound / sliders work in place. Back closes it.
4. Handle extras: double-tap, long-press, side swipes, shake (set each, test each).
5. Smart rules: full-screen video (handle and button hidden), keyboard open, lock screen, screen off, landscape.
6. Keep alive: swipe the launcher away from Recents, wait 10 s; restart the phone, wait for the notification; leave it for an hour.
7. Settings > Privacy policy > Permissions: shows the new lines and no Accessibility line. Switch "Quick panel over other apps" off: notification, handle and button disappear.
