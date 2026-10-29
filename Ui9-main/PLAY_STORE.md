# Google Play publishing pack - OneUI 9 Launcher (Ok Labs)

Contact email for everything below: a.rehmanmazher11@gmail.com
(Not legal advice. Have a lawyer read the Terms if you want to be sure. Check Play Console for rule changes before you submit.)

## 0. Fix these BEFORE you upload (they can get the app rejected)

1. **Name / trademark.** "One UI" is Samsung's trademark. Play can reject or later remove an app whose name or listing uses it
   (impersonation / intellectual-property policy). Safest: publish under your own name, e.g. **"Glassy launcher"**.
   Change it in one place: `app_name` in `app/src/main/res/values/strings.xml`. Then replace "OneUI 9 Launcher" in `docs/*.html`
   (`sed -i 's/OneUI 9 Launcher/Glassy launcher/g' docs/*.html`). Do not mention Samsung or "One UI" in the store listing.
2. **Package name is permanent.** `com.oklabs.oneui9launcher` can never be changed after the first upload to Play.
   If you want something cleaner (e.g. `com.oklabs.launcher`), change it NOW, before the first upload (namespace, applicationId and the
   Kotlin package folders). Tell me and I will do it.
3. **Policy URLs.** In `app/src/main/res/values/strings.xml` replace `YOUR_NAME` in `privacy_url` and `terms_url` (see section 1).
   `release.sh` warns you while it is still there.
4. **Target API 36.** Done: the app now targets Android 16 (API 36), which Play requires for new apps and updates. Install the platform once:
   `sdkmanager "platforms;android-36"`. If Gradle complains about API 36 and AGP 8.7.3, tell me the first error line (a small AGP upgrade fixes it).
5. **Device admin (double-tap lock).** The app uses the Device Admin API for "lock the screen" only. Play reviews device-admin use closely and I could not
   confirm from Google's pages that this consumer use is always accepted. If review rejects it, the quick fix is to remove the feature
   (Android's Accessibility lock action is stricter, so do not swap to that). Explain it in the declaration form (section 5).
6. **Notification access.** A clear in-app dialog now appears before the phone's Notification access screen opens (Play's "prominent disclosure" idea).
7. **Upload an .aab, not an .apk.** `bash release.sh` now builds both. Upload `OneUI9Launcher-release.aab` from Downloads.
   Keep `oneui9-release.jks` + `keystore.properties` safe. Use Play App Signing when asked (recommended).
8. **New personal developer accounts** may have to run a closed test with a minimum number of testers for a set number of days before production.
   Organization accounts are usually exempt. Check the current rule in Play Console > Dashboard.

## 1. Put the policy pages online (free, 3 minutes)

The files are in `docs/` (`index.html`, `privacy-policy.html`, `terms.html`).
1. `bash push.sh https://github.com/YOUR_NAME/OneUI9Launcher.git` (repo must be public).
2. GitHub > the repo > Settings > Pages > Source: "Deploy from a branch", Branch: `main`, folder `/docs`, Save.
3. After a minute these work:
   - Privacy policy: `https://YOUR_NAME.github.io/OneUI9Launcher/privacy-policy.html`
   - Terms: `https://YOUR_NAME.github.io/OneUI9Launcher/terms.html`
   - Website: `https://YOUR_NAME.github.io/OneUI9Launcher/`
4. Put the privacy URL in Play Console (App content > Privacy policy) and in `strings.xml`. Open it in a browser first: it must load with no login.

## 2. Store listing text (copy and paste)

- **App name (max 30):** Glassy launcher   (or OneUI 9 Launcher if you accept the trademark risk)
- **Short description (max 80):** A clean, fast home screen with folders, widgets, themed icons and no ads.
- **Category:** Personalization.  **Tags:** Launcher, Personalization.
- **Email:** a.rehmanmazher11@gmail.com   **Website:** your GitHub Pages URL   **Phone:** optional

**Full description (max 4000):**

```
A clean, fast, modern home screen for Android 8.0 and newer - no ads, no account, no tracking.

HOME SCREEN
- Multi-page grid: 4x5, 4x6, 5x5 or 5x6, with three icon sizes
- Drag and drop icons, make folders by dropping one icon on another
- A dock for your favorite apps and folders
- Search bar on Home (optional), swipe down for notifications, pinch for the edit screen

APPS SCREEN
- Swipe up for all your apps, then swipe left and right through the pages
- Instant search at the bottom, sort A-Z or by newest, hide the apps you do not want to see

WIDGETS
- Add widgets from any app, move and resize them with handles
- Built-in clock, mini clock, analog clock and date widgets

LOOK AND FEEL
- Colourful icons for Phone, Messages, Camera, Contacts, Calendar, Clock and more (or keep each app's own icon)
- Themed icons in one colour (from your wallpaper or a colour you pick)
- Dark and light panels, blur behind panels (Android 12+), wallpaper that slides with the pages
- Choose the colour and opacity of the dock

EXTRAS
- Notification badges: dot or number (optional, needs notification access)
- Edge panel with your favorite apps
- Double-tap an empty spot to lock the screen (optional)
- Landscape support (optional)
- Back up and restore your layout and settings to a file

PRIVACY
OneUI-style home screen, written from scratch by Ok Labs. It has no internet permission, collects nothing and shows no ads.
Your layout and settings stay on your phone. Notification access and the lock-screen permission are optional and only used for the
features above: the app counts notifications for badges and, if you keep "Notifications in Quick panel" on, shows their title and text in the panel from memory only; nothing is saved or sent.

After installing, press Home and choose this launcher as your default (Always).

Questions or ideas? a.rehmanmazher11@gmail.com
```
(If you keep the name "OneUI 9 Launcher", still avoid the words "Samsung" and "One UI" in the description.)

## 3. Graphics you must upload

| Item | Size / rule | Who makes it |
|------|-------------|--------------|
| App icon | 512 x 512 PNG, up to 1 MB | export the launcher icon (blue-violet, four tiles) |
| Feature graphic | 1024 x 500 PNG or JPG | you / Canva |
| Phone screenshots | at least 2 (best 4-8), 16:9 or 9:16, 320-3840 px | screenshot your phone: Home, folder, Apps screen, widget picker, themed icons, edit screen |
| 7-inch / 10-inch tablet screenshots | optional | skip |

No Samsung logos, no phone-maker names, no fake "5 star" badges in the graphics. Do not show other apps' copyrighted artwork large.
I can make the 512 icon and a feature graphic if you ask.

## 4. App content (Play Console > Policy > App content)

| Question | Answer |
|----------|--------|
| Privacy policy | your GitHub Pages privacy URL |
| Ads | No, the app contains no ads |
| App access | All functionality is available without login or special access |
| Target audience | 18+ (or 13+). Not "designed for children / Families" |
| Content rating (IARC) | Category: Utility / Productivity. Violence, sexual content, language, drugs, gambling, user-generated content, location sharing, purchases: all **No** -> expect "Everyone / 3+" |
| News app | No |
| COVID-19 contact tracing / status | No |
| Data safety - Does the app collect or share user data? | **No** (collected: none; shared: none) |
| Data safety - data encrypted in transit / deletion | not applicable (no data leaves the device) |
| Government app / Financial features / Health features | No / None / None |
| Advertising ID | The app does not use it. Declare "No" |

Data safety note: the app reads notifications locally only (counts for badges; title and text for the Quick panel, in memory only, never saved) and never transmits them. Because nothing leaves the device, answering "No data collected" is correct
as long as you never add internet features. If you add any later, update the form and the policy first.

## 5. Declarations and review notes (paste into the forms / "Notes for reviewers")

**Notification Listener (badges):**
"OneUI 9 Launcher is a home-screen launcher. The optional 'Notification badges' feature uses NotificationListenerService to count active notifications per app
and draw a dot or number on that app's icon, and (Step 16c, switchable in Settings > Gestures) to list them in the app's Quick panel with swipe-to-remove and tap-to-open, and (Step 16d) to read the active media session (title, artist, cover) for the panel's play / pause / next card.
Title and text are held in memory only while the notifications are showing; they are never stored or transmitted, and the app has no INTERNET permission.
A prominent in-app dialog explains this before Android's Notification access screen opens. Notification badges are off by default."

**Device administrator (force-lock only):**
"The optional 'Double-tap to lock' feature uses the Device Administration API with a single policy, force-lock (res/xml/lock_admin.xml), to lock the screen when the user double-taps
an empty area of the Home screen. It is off by default, requires the user's explicit activation through Android's own screen, and can be disabled in Settings > Security > Device admin apps.
The app cannot wipe data, reset passwords or read any data with this policy."

**Package visibility (no QUERY_ALL_PACKAGES):**
The app does not use QUERY_ALL_PACKAGES. It uses `<queries>` intent filters (MAIN/LAUNCHER, DIAL, SENDTO, VIEW https, still-image camera, SETTINGS, APPWIDGET_UPDATE, SHOW_ALARMS, APP_CALENDAR).
Core purpose: a launcher must list launchable apps.

**Other permissions:** REQUEST_DELETE_PACKAGES (starts Android's own uninstall dialog from the quick menu), EXPAND_STATUS_BAR (swipe-down opens notification panel),
SET_WALLPAPER_HINTS (wallpaper scroll). All are user-triggered.

**Quick panel permissions (Step 16a):** ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE and BLUETOOTH (API 30 and older) are read-only: they show on / off on the panel buttons.
WRITE_SETTINGS (special access, the user grants it in Android's own screen) is used only for the brightness slider and the Auto rotate button.
ACCESS_NOTIFICATION_POLICY (special access, same) is used only for the Do not disturb and Mute buttons. Core purpose: a Samsung-style quick settings panel inside the Home screen.
Wi-Fi, Bluetooth, mobile data, flight mode and hotspot are NOT switched by the app (Android does not allow it); those buttons open the phone's own settings panel.

**Reviewer test steps:** Install, press Home, choose the launcher as default. Long-press an empty spot for Widgets / Home settings. Home settings > Notification badges > Dot shows the disclosure dialog.
No login is needed.

## 6. Release steps

1. Pay the one-time Play developer fee, finish identity verification (Organization account needs a D-U-N-S number; Personal does not).
2. Play Console > Create app: name, default language, App, Free, accept declarations.
3. Fill the dashboard tasks: privacy policy, ads, app access, content rating, target audience, data safety, store listing (sections 2-4).
4. Testing > Internal testing > create release > upload `OneUI9Launcher-release.aab` > add your email as tester > install from the link and test on a real phone.
5. If your account needs a closed test, run it with the required testers and days, then apply for production.
6. Production > create release > upload the same .aab (or a newer one) > release notes > send for review (can take from hours to several days).
7. Next updates: raise `versionCode` in `app/build.gradle.kts` every time (now 112), run `bash release.sh`, upload the new .aab. Same keystore every time.

## 7. After you publish

- Reply to reviews and keep the email above working; Play requires a monitored contact address.
- Any new permission or any internet feature means: update the privacy policy page, the Data safety form and the declarations before releasing.

## Step 13 additions (read before you upload)

- **Policies and contact are now inside the app** (Settings > Contact us / Privacy policy / Terms of use / Permissions and data use). Play wants the privacy policy
  reachable inside the app and on a public URL: you now have both. Put `docs/` online (section 1) and replace `YOUR_NAME` in `privacy_url`, `terms_url` and `website_url`
  in `strings.xml`; until then the "online" rows in Contact us are hidden.
- **Contact address** is `contact_email` in `strings.xml` (currently the address at the top of this file). Change it there and in `docs/*.html`.
- **Data safety form:** still "no data collected, no data shared". "Report a problem" only opens the user's own email app with a message the user can edit and sends nothing by itself.
- **Icons:** the new icons are original drawings for generic system apps. Do not add logos of other companies (Facebook, WhatsApp, Instagram, Chrome, YouTube, Gmail...) to the pack:
  that is a trademark / impersonation problem for a Play listing.

## Accessibility service (Step 22)

"Open in every app" is optional and off by default. Declaration text: "The Accessibility service is used only to catch a pull-down at the top edge with an invisible overlay (and, as a fallback, to detect window-state events, no content retrieval) so the app can show its own Quick panel. It uses the screenshot capability only to blur the screen behind that panel, in memory; nothing is read, stored or transmitted." If Google rejects the declaration, remove the service from AndroidManifest.xml; the launcher works without it.
