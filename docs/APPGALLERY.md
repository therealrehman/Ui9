# Huawei AppGallery publishing pack - Ok Launcher (Ok Labs)

Contact email: a.rehmanmazher11@gmail.com
(Not legal advice. AppGallery Connect changes its forms and rules: where a form here differs from this file, the form wins. Read Huawei's current
"AppGallery Review Guidelines" once before you submit.)

## 0. What was changed in the app for the store build (Step 36)

- **The release (store) build has no WRITE_SECURE_SETTINGS.** That permission can never be given by a store; it was only for optional adb shortcuts of the debug build.
  It is removed by `app/src/release/AndroidManifest.xml` (the debug build keeps it).
- **The release build looks only at the phone's own system windows** when the Quick panel service searches for a button (`res/values/bools_store.xml`): it never
  reads or presses anything inside another app.
- **The Accessibility dialog and the privacy policy now say exactly what the service does** (blur picture in memory, and finding + pressing the phone's own Quick
  settings buttons by name). An old text said "does not read your screen"; reviewers compare the text with the behaviour. Run `bash appgallery.sh prepare` once.
- App name in the APK is **Ok Launcher** (not "One UI": Samsung's trademark). Keep the listing name identical to it.
- No Google / HMS libraries inside: nothing to remove for Huawei phones. No ads, no internet permission, no native code (so the APK is 64-bit ready).

## 1. Decide these BEFORE the first upload (permanent)

1. **Package name** `com.oklabs.oneui9launcher` can not change after the first upload. It contains "oneui" (a Samsung trademark word); a store reviewer
   may or may not object. A cleaner one (for example `com.oklabs.launcher`) costs nothing now and a lot later. Tell me and I change only `applicationId`
   (a separate app then: use Home settings > Back up layout and settings on the old one and Restore on the new one).
2. **Signing key.** `bash make_signing_key.sh` makes `oneui9-release.jks` once. Back it up. Every update must be signed with the same key. The Huawei account does not
   give you a key; AppGallery Connect also has an optional "App Signing" service (Distribute > Services > App Signing) - you do not need it for an APK signed with your own key.
3. **Privacy / terms pages online** (they are in `docs/`, served by GitHub Pages): `https://therealrehman.github.io/Ui9/privacy-policy.html` and `.../terms.html`.
   Open both in a browser (no login) before you submit. They must say what the app really does (they were updated in Step 36: run `bash push.sh` so Pages shows the new text).

## 2. Build the signed release APK on GitHub (no Android SDK needed on the phone)

One time only - make your signing key (Termux):
```
pkg install openjdk-17
cd ~/OneUI9Launcher
bash make_signing_key.sh
```
It puts two files in Downloads: `oneui9-release.jks` (**back it up**: cloud drive / another phone; lose it and the app can never be updated) and
`OkLauncher-github-secrets.txt`. Open the txt and add its 4 values in GitHub: your repo > Settings > Secrets and variables > Actions > New repository secret
(Name = the word before the colon, Secret = what follows). Then delete the txt. Never post these values anywhere.

Every release:
```
cd ~/OneUI9Launcher
bash appgallery.sh prepare     # honest Accessibility text + policy links; then:
bash push.sh                   # sends the project to GitHub (also updates the privacy page)
```
Then on GitHub: Actions > **Build signed release APK** > Run workflow. After about 5 minutes open the run, download the artifact **OkLauncher-release-apk**
(a zip) into Downloads, and run:
```
bash appgallery.sh check
```
`check` unpacks that zip, tells you if the APK is unsigned, debuggable, signed with a debug key, or still has WRITE_SECURE_SETTINGS, and puts the
upload files in `Downloads/AppGallery/`. If the GitHub run fails, send me the first red `error:` line.
**Install and try the signed APK on your phone before uploading** (Android refuses it over the debug build: back up your layout, uninstall the debug app,
install, restore). The release build is shrunk by R8; test: Home, Apps screen, folders, widgets, Quick panel, Notifications page, set as default Home, the
Accessibility switch, double-tap lock. If something works in debug but not in release, tell me which.

## 3. AppGallery Connect (https://developer.huawei.com/consumer/en/appgallery/)

1. Register a Huawei developer account and finish the identity verification (individual or company). This can take days: start it first.
2. My apps > New app (or "Add app"): platform **Android**, package name exactly as in the APK, name **Ok Launcher**, default language English,
   category: the closest of Tools / Personalization (a home-screen launcher).
3. App information: fill the texts in section 4, upload `icon-216.png` (216 x 216 PNG) and 3 or more screenshots (section 5).
4. Upload the APK: `OkLauncher-<version>-appgallery.apk` (Distribution > Version information > Software packages). AppGallery also accepts .aab,
   the APK is the simple way. A new upload needs a higher `versionCode` (it is raised in every step).
5. Privacy: privacy policy URL (section 1) and the **privacy tags / data form** (section 6).
6. Content rating (age) questionnaire: section 7. Then Submit for review. Huawei answers by email; fix what it names and submit again.

## 4. Store texts (copy and paste)

**App name:** Ok Launcher

**Brief introduction (keep under 80 characters):**
`A clean, fast home screen with folders, widgets and a Quick panel. No ads.`

**Full description:**
```
A clean, fast, modern home screen for Android - no ads, no account, no tracking.

HOME SCREEN
- Multi-page grid with three icon sizes, drag and drop, folders by dropping one icon on another
- A dock for your favourite apps and folders
- Optional search bar on Home, swipe down for notifications

APPS SCREEN
- Swipe up for all your apps, search instantly, sort A-Z or by newest, hide apps

WIDGETS
- Add widgets from any app, move and resize them
- Built-in clock, analog clock and date widgets

QUICK PANEL
- Your own Quick panel with the usual buttons (Wi-Fi, Bluetooth, torch, rotation, Do not disturb and more), brightness and volume sliders
- Swipe sideways through pages of buttons, edit the buttons and their icons
- Optional: open it in every app (needs the Accessibility service, see below)

LOOK AND FEEL
- Colourful icons or each app's own icon, themed one-colour icons, dark and light panels, wallpaper that slides with the pages

EXTRAS
- Notification badges (optional, needs Notification access)
- Double-tap an empty spot to lock the screen (optional, needs Device administrator)
- Back up and restore your layout and settings to a file

PERMISSIONS AND PRIVACY
The app has no internet permission, collects nothing and shows no ads. Everything stays on your phone.
The Accessibility service is OPTIONAL. If you switch it on it is used only for the Quick panel: to show the panel when you pull down the status bar, to blur
the screen behind it (a picture kept in memory only, never saved or sent), and to press the phone's own Quick settings buttons (Wi-Fi, Bluetooth...) for you when you tap
them in the panel. It does not read your messages, passwords or other apps. The launcher works fully without it.

After installing, press Home and choose Ok Launcher as your default (Always).
Questions? a.rehmanmazher11@gmail.com
```

**What is new (first version):** `First release.`

## 5. Graphics

| Item | What |
|------|------|
| App icon | `icon-216.png` (216 x 216, in the package folder `store/appgallery/`), opaque, no rounded corners drawn into it (the store rounds it) |
| Screenshots | at least 3 (best 5), PNG or JPG, taken on your phone: Home with folders, Apps screen, widget picker, Quick panel (both pages), Notifications page, settings. The screenshots must show the real app |
| Do not | show other phone makers' logos or names, Samsung / One UI, other apps' artwork large, or fake rating badges |

## 6. Privacy tags / data questions (answers that match the app)

The app has no network permission and sends nothing anywhere. Answer "does not collect or share personal information" where the form allows it.
If the form asks item by item, the honest answers are:

| Data the app touches | Where it goes |
|----------------------|---------------|
| List of installed apps (to draw Home and the Apps screen) | read on the device, never uploaded |
| Notification count / title and text (optional badges and Quick panel list) | read on the device, kept in memory, never saved or uploaded |
| Names and on / off of the phone's Quick settings buttons (optional Accessibility) | read on the device, never saved or uploaded |
| Screen picture for the blur (optional Accessibility) | kept in memory for a moment, never saved or uploaded |
| Layout and settings | a file only if YOU back up to a place you choose |

No ads, no analytics, no advertising ID, no third-party SDK.

## 7. Content rating

Utility / personalisation. Violence, sexual content, gambling, drugs, user-generated content, location sharing, purchases: all No. Expect the lowest age group.

## 8. Notes for the reviewer (paste into the review / remarks field)

```
Ok Launcher is a home-screen (launcher) app. Set it as the default Home app to use it. It has no internet permission and sends no data.
Sensitive access, all optional and explained in the app before the system screen opens:
- Accessibility service (optional, off by default): used only for the Quick panel. It shows the Quick panel when the status bar is pulled down, takes a screen
  picture in memory to blur behind the panel, and, when the user taps a button such as Wi-Fi in our panel, opens the phone's own Quick settings, finds that
  button by its name and presses it. In the store build it only looks at the phone's own system windows, never at other apps. The launcher works without it.
- Notification access (optional): counts notifications for badges and lists them in the panel, in memory only.
- Device administrator (optional): only to lock the screen on double-tap.
- WRITE_SETTINGS / Do Not Disturb access (optional): brightness / auto-rotate and Do not disturb buttons of the Quick panel.
- REQUEST_DELETE_PACKAGES: the "Uninstall" item of an app's long-press menu.
Contact: a.rehmanmazher11@gmail.com
```

## 9. Phones that can install it

The Android APK works on Huawei phones that run EMUI or HarmonyOS with Android app support. Phones on **HarmonyOS NEXT** run only native HarmonyOS apps and
can not install Android APKs: no Android launcher can reach them. Check Huawei's current device list when you set the target devices.
On Huawei phones allow the launcher to run in the background (Settings > Battery > App launch > Manage manually, all three switches on), or the phone may stop the Quick panel service.

## 10. If it is rejected

Read the reason in the email, change only what it names, raise `versionCode` if the APK changed, and submit again. Typical reasons: the privacy policy URL does not open
or does not match the app, the name differs from the one in the package, too few or not real screenshots, a sensitive permission without an explanation (use section 8).
If review refuses the Accessibility service, tell me: the launcher can ship a store build without it (the panel then opens from the Home screen only).
