# OneUI 9 Launcher - 10-step build plan

A One UI 9-style home screen for any Android 8.0+ phone (API 26+), written from scratch.
Original code and icons - nothing is copied from Samsung.

## Build + install (Termux, one command)

```
cd ~/storage/downloads && unzip -oq "$(ls -t OneUI9Launcher-step01*.zip | head -n1)" -d ~/OneUI9Launcher && cd ~/OneUI9Launcher && bash build.sh
```

Each new step ships as a full project zip. Use the same command with the new zip name
(step02, step03, step04, step05a, step05b, step05c, step06a, step06b1, step06b2, step06c1, step06c2, step07a, step07b, step07c, step08a, step08b, step08c, step08d, step09a, step09b, step10a, step10b, step10c, step10d, step11, step12, step13, step14, step15, step16a, step16b, step16c, step16d, step16e, step17a, step17b, step17c, step18, step19). Your `local.properties` is kept between steps.

## Status

| # | Step | Status |
|---|------|--------|
| 1 | Foundation: project, home screen, Favorites dock, squircle icons, default-home prompt | DONE |
| 2 | Apps screen: swipe up/down, vertical scroll, search pill, A-Z sort, open animation | DONE |
| 3 | Home grid: multi-page grid, swipe between pages, page dots, saved layout | DONE |
| 4 | Edit mode: long-press, drag and drop, quick menu (App info / Uninstall / Remove), app shortcuts | DONE |
| 5 | Folders: create by dropping one icon on another, open popup, rename, folders in the dock | DONE |
| 5a | Folders part 1: create (drop on an icon), add to a folder, folder tile on Home, open card, start apps | DONE |
| 5b | Folders part 2: rename, drag apps out of the card, move / ungroup / remove a whole folder (long-press) | DONE |
| 5c | Folders part 3: folders in the dock (dock becomes editable), open / close polish | DONE |
| 6 | Widgets: widget picker, resize, built-in clock/date widget | DONE |
| 6a | Widgets part 1: widget picker, add (allow + set up), live widgets on the page, saved, remove (long-press) | DONE |
| 6b | Widgets part 2: move a widget (drag, other pages), resize with handles | DONE |
| 6b1 | Widgets part 2a: move a widget (lift, drag, other pages, apps step aside, Remove zone) | DONE |
| 6b2 | Widgets part 2b: resize a widget with handles (drag the edges, snaps to cells) | DONE |
| 6c | Widgets part 3: built-in clock / date widget, setup screen, polish | DONE |
| 6c1 | Widgets part 3a: the launcher's own Clock, Mini clock and Date widgets (picker, live, auto-fit, tap opens Clock / Calendar) | DONE |
| 6c2 | Widgets part 3b: widget setup screen (style, colour, 12 / 24 h, date on / off, background), analog clock, "Widget settings" in the menu | DONE |
| 7 | Search + gestures: "Show search on Home screen" bar, swipe down for notifications, double-tap lock, pinch for edit mode | DONE |
| 7a | Search + gestures part 1: search bar on the Home screen (tap = Apps screen with keyboard), show / hide from the long-press menu | DONE |
| 7b | Search + gestures part 2: swipe down for the notification panel, double-tap to lock the screen | DONE |
| 7c | Search + gestures part 3: pinch for the edit screen (zoomed-out Home with Widgets / Search bar / Done) | DONE |
| 8 | Look and feel: wallpaper scroll, blur, dock background (blur / colour / transparency), dark mode, themed icons | DONE |
| 8a | Look part 1: wallpaper scrolls with the pages, dock background colour + opacity (edit screen > Dock) | DONE |
| 8b | Look part 2: blur behind the Apps screen, open folders and the widget picker (Android 12+, on / off) | DONE |
| 8c | Look part 3: dark / light mode for the panels (follows the phone or fixed) | DONE |
| 8d | Look part 4: themed icons (one colour tint from the wallpaper / chosen colour) | DONE |
| 9 | Settings + extras: Home settings (grid, icon size), hide apps, notification badges, Edge panel, landscape | DONE |
| 9a | Settings part 1: Home settings screen (grid 4x5 / 4x6 / 5x5 / 5x6, icon size, hide apps) | DONE |
| 9b | Settings part 2: notification badges, Edge panel, landscape | DONE |
| 10 | Polish + release: speed, backup/restore layout, own branding, signed release build | DONE |
| 10a | Release part 1: speed (icon cache, no needless rebuilds) | DONE |
| 10b | Release part 2: backup / restore of the layout and settings | DONE |
| 10c | Release part 3: own icon (with themed-icon layer), About screen | DONE |
| 10d | Release part 4: signed, shrunk release build (release.sh) | DONE |
| 11 | Play Store pack: privacy policy, terms, listing text, target API 36, .aab, in-app policy links | DONE |
| 12 | Look like the reference: 12a colourful system-app icons + rounder squircle, 12b Apps screen in left/right pages | DONE |
| 13 | Settings app + Apps screen grid options + more icons + Contact / policies inside the app | DONE |
| 14 | Gradient glass backgrounds for the clock / date widgets: 5 themes (Aurora, Sunset, Ocean, Forest, Midnight) | DONE |
| 15 | Dock gradients, gradient wallpapers, search bar gradient | DONE |
| 16 | Quick panel (Samsung One UI style, customizable): 5 parts | in progress (16c-16e left) |
| 16a | Quick panel part 1: the panel itself (swipe down), buttons that work, brightness slider, "Swipe down opens" setting | DONE |
| 16b | Quick panel part 2: edit the buttons (Done / Reset, active buttons + Available buttons, drag to add / remove / reorder, more buttons) | DONE |
| 16c | Quick panel part 3: notifications list inside the panel (swipe away, clear all, tap to open) | todo |
| 16d | Quick panel part 4: volume + media cards, Media output, Device control row | todo |
| 16e | Quick panel part 5: Panel settings (left-side panel, brightness position, reset layout), polish, optional system-wide mode | DONE |
| 17 | Notification panel like the reference video: 3 parts | DONE |
| 17a | Notifications page: full-screen page over the blurred wallpaper, time + date top left, cards, centred "No notifications", Notification settings + Clear all at the bottom | DONE |
| 17b | Quick panel full-screen look: header (time + date, pencil / power / gear), wide Wi-Fi + Bluetooth tiles, round buttons, sliders with a moon / speaker button, cards | DONE |
| 17c | Panel edit look ("Panel settings - Touch and hold to change order - Done") and swipe between the Quick panel and the Notifications page | DONE |
| 18 | Fixes after testing: crash on opening the Notifications page twice, blurred Home screen behind the panels, dark frosted buttons and cards | DONE |
| 19 | Fixes after testing 2: slider levels on reopening, one frosted rectangle behind the round buttons, more icons in the icon set | DONE |

## Step 1 - what is in it

- Gradle project (AGP 8.7.3, Kotlin 2.0.21, minSdk 26) with no external libraries
- Registered as a Home app; asks once to become the default and shows a chip until it is
- Wallpaper behind everything, edge-to-edge, see-through status and navigation bars
- Favorites dock (translucent pill): default Phone, Messages, Browser, Camera, Settings
- Every icon is cut to one squircle shape (adaptive and old-style icons)
- Press "squish" on icons and a scale-up animation when an app opens
- Back / Home never close the launcher
- `build.sh`: finds your SDK, builds, copies the APK to Downloads, opens the installer

## Step 2 - what is in it

- Swipe up anywhere on the home screen (dock included) opens the Apps screen; swipe down closes it
  (only when the list is already at the top). The finger drives the animation, a fling settles it.
- Back / Home closes it. Home pressed inside another app resets it (no search, list at top).
- One vertically scrolling grid of every launchable app, same squircle icons, press squish, scale-up on open.
- Search pill: type to filter (starts-with first, accents ignored: "cafe" finds "Café"),
  X to clear, keyboard Search opens the first match.
- A-Z sort (default) with a fast-scroll letter strip on the right edge (drag it, letter bubble follows).
  The three-dot menu switches to "Recently installed". Your choice is remembered.
- List refreshes by itself when you install / update / uninstall an app.
- Home screen fades and shrinks behind the Apps screen while it opens.

## Step 3 - what is in it

- Home screen is now a row of pages with a 4 x 6 icon grid (labels under icons, same squircle icons).
- Swipe left / right between pages. A quick flick turns one page; first / last page rubber-band and spring back.
  Page dots follow your finger. Swipe up still opens the Apps screen from anywhere.
- Layout is saved (which app, which page, which cell) and survives restarts and app updates.
- First run: the starter page is filled with the phone's own Gallery, Calendar, Clock, Contacts, Calculator,
  Files, Maps, Play Store, Email... (whatever exists). Apps already in the dock are not repeated.
- Long-press an app in the Apps screen: "Add to Home screen" (goes to the first free cell, a new page opens
  when the pages are full, max 8 pages) or "Remove from Home screen", plus "App info".
- Long-press a home icon: "Remove from Home screen" / "App info". (Step 4 replaces this with edit mode,
  drag and drop and the full quick menu.)
- Uninstalled apps disappear from the home screen by themselves; an emptied page is dropped.
- Home button / Back: closes the Apps screen first, then returns to the first home page.
- Icon size adapts to the screen height so labels never overlap on small phones.

## Step 4 - what is in it

- Long-press an icon (home screen, Apps screen or dock): the icon lifts and the quick menu opens next to it.
  Menu = the app's own shortcuts (e.g. "Take a selfie", up to 4), then Add to / Remove from Home screen,
  App info, Uninstall (not offered for system apps). Lift your finger and the menu stays; tap outside or Back closes it.
- Keep the finger down and move: the menu goes away and the icon follows your finger. The cell you are about to
  drop on lights up; a short buzz marks each new cell.
- Drop on an empty cell = the icon moves there. Drop on a taken cell = the two icons swap (same page), or the icon
  that was there steps aside to the nearest free cell (icon from another page / the Apps screen).
  A page that ends up empty disappears.
- Hold the finger at the left / right screen edge: the pages slide. Past the last page a new empty page opens (max 8).
- While carrying an icon, drop zones appear at the top: Remove (home icons), App info, Uninstall.
- Drag an app out of the Apps screen: the Apps screen closes and you drop the app on any home cell.
- Back / Home closes an open quick menu first. Drops are saved right away.
- App shortcuts need this launcher to be the phone's default home app (Android only shares them with it).
- Not in this step: long-press on empty wallpaper (the zoomed-out edit screen with wallpaper / widgets / settings)
  - it arrives with widgets (6), gestures (7) and settings (9). Dock icons get the menu but are not dragged yet.

## Step 5a - what is in it (Folders, part 1 of 3)

- Drag an icon (from Home or the Apps screen) and rest it on another icon for a moment (about 0.4 s): the cell
  lights up rounder and brighter and you feel a tick. Let go = both apps go into a new folder called "Folder"
  that takes the cell of the icon underneath. Let go BEFORE the tick and it works like Step 4 (move / swap).
- Rest an icon on a folder and let go: the app joins the folder (up to 20 apps; then "This folder is full").
  An app that was loose on Home, or in another folder, moves into the folder.
- A folder on Home is a soft glass squircle showing its first four apps (2 x 2) with its name underneath.
- Tap a folder: the home screen dims and a card grows out of the folder icon with its name and its apps
  (4 per row, scrolls when there are many). Tap an app to start it. Tap outside the card, Back or Home closes it.
  Swipe up (Apps screen) is switched off while a folder is open.
- A folder always has 2+ apps: if one is removed ("Remove from Home screen" on the app in the Apps screen,
  or uninstall) and one app is left, the folder turns back into that app in the same cell; an empty folder vanishes.
- Folders are saved with the layout (the save format is now "v2"; layouts from Step 3 / 4 load as before).
- Not in this part: long-press on a folder (move it, rename it, remove it), renaming, dragging apps out of the card,
  folders in the dock. These are 5b and 5c.

## Step 5b - what is in it (Folders, part 2 of 3)

- Rename: tap the folder's name in the open card (or long-press the folder > Rename). The name turns into a text field
  and the keyboard opens; Done or a tap outside saves it, closing the card saves it too. Up to 24 characters;
  a blank name goes back to "Folder". The card slides up if the keyboard would cover it.
- Drag an app out of a folder: long-press the app in the open card. Lift and hold = quick menu (its shortcuts,
  "Remove from folder", "Remove from Home screen", App info, Uninstall). Keep the finger down and move = the card
  closes and the app follows your finger: drop it on any home cell (or the Remove / App info / Uninstall zones).
  Dropping it back on its own folder leaves it inside. Rest it on another icon / folder and it joins that one.
  "Remove from folder" puts the app in the free cell nearest the folder. A folder left with one app turns back into it.
- Long-press a folder on Home: it lifts and a menu opens: Rename / Ungroup (the apps spread onto free cells, the
  first one takes the folder's cell) / Remove folder (asks first; the apps stay in the Apps screen).
- Keep the finger down on a lifted folder and move: it is dragged like an app (move, swap, other page, edge slide).
  The only drop zone for a folder is Remove (asks first). A folder cannot be dropped into another folder.
- Not in this part: folders in the dock, open / close polish. That is 5c.

## Step 5c - what is in it (Folders, part 3 of 3)

- The dock is now editable and saved with the layout (5 slots). Long-press a dock icon: quick menu (shortcuts,
  "Remove from Dock", App info, Uninstall); keep the finger down and move to drag it.
- Drag an icon from the dock to any home cell, from a home page / the Apps screen / a folder card onto a dock slot
  (the slot lights up). Dropping on a taken slot moves that icon to the nearest free slot; with all 5 slots taken you
  get "The dock is full". Dropping on another dock icon swaps them. An app lives in one place only: moving it to the
  dock takes it off the home page.
- Folders in the dock: rest an icon on a dock icon for a moment (tick, rounder highlight) and let go: a folder with
  no label forms in that slot. Tap it: the card opens above the dock, rename / drag out / long-press work as on Home.
  A dock folder can be dragged around (Home <-> dock) like any icon. "Ungroup" and "Remove from folder" keep apps in
  free dock slots first, then on the home pages.
- First run (and the first start after updating from 5b): the dock gets the phone's default Phone / Messages /
  Browser / Camera / Settings once. If you empty the dock later it stays empty. Layout format is unchanged ("v2";
  the dock is saved as page 100).
- Polish: the folder icon fades out underneath while its card grows out of it and fades back in as the card shrinks;
  the apps in the card pop in one after another; close is a little slower and softer. Tapping the name to rename and
  the keyboard behave the same from the dock.
- Dock icons are 54 dp now (same as the Apps screen, so they are never stretched).

## Step 6a - what is in it (Widgets, part 1 of 3)

- Long-press an empty spot of a home page: a small menu with **Widgets**. Tap it: a sheet slides up with every app that
  has widgets (A-Z). Tap an app: its widgets open as cards (picture, name, size in cells, e.g. "2 x 2"). Tap a card to add it.
- The widget goes to the first spot that fits on the page you are on (top to bottom, left to right), else on another page,
  else on a new page (max 8 pages). No room anywhere = "No room for this widget on the Home screen".
- Android may first ask "Allow OneUI 9 Launcher to create widgets?" (once per widget, system screen) and some widgets open
  their own setup screen. Cancel there = nothing is added.
- Widgets are live (clock ticks, weather updates...) and work as in their own app: tap, scroll. They keep their size in cells
  and are saved with the layout (the save format is now "v3"; layouts from 5c load as before).
- Long-press a widget: menu with **Remove widget**. Uninstalling the widget's app removes the widget by itself.
- Apps and folders can not be dropped onto a widget ("A widget is on this spot") and never merge into it.
- Back / Home closes the picker first.
- Not in this part: moving a widget and resizing it (6b), the built-in clock / date widget (6c).

## Step 6b1 - what is in it (Widgets, part 2a: moving)

- Long-press a widget: it lifts a little and the menu opens (Remove widget). Lift the finger and the menu stays; tap outside
  or Back closes it. (Before 6b1 the long-press only opened the menu.)
- Keep the finger down and move: the menu goes away and the widget follows your finger. The block of cells it would cover
  lights up (it snaps to the nearest cell, always inside the page); it turns **red** when the widget can not go there.
  A short buzz marks each new spot. Let go on a red spot = nothing moves ("No room for the widget there").
- Apps and folders under the new spot step aside to the nearest free cells of that page (not under the widget).
  No free cells for them, or another widget in the way = red / refused. The cells the widget leaves become free at once,
  so a small step (e.g. one row down) always works when only apps are in the way.
- Other pages: hold the finger at the left / right screen edge and the pages slide (a new empty page opens past the last,
  max 8 pages). A page the widget leaves empty disappears.
- While a widget is carried the only drop zone at the top is Remove (it deletes the widget, like the menu entry).
  Widgets never go into the dock or into a folder.
- The move is saved at once (same "v3" layout format, nothing changed). The widget keeps running while it is moved
  (its live view is reused, nothing is added again).
- Not in this part: resizing with handles (6b2), the built-in clock / date widget (6c).

## Step 6b2 - what is in it (Widgets, part 2b: resizing)

- Long-press a widget: the menu now has **Resize** (above Remove widget) when the widget allows resizing
  (some widgets are fixed-size: no Resize entry). Tap it: a white rounded frame appears around the widget with a dot (handle)
  in the middle of every side the widget can be resized on (wider / narrower, taller / shorter, or both).
- Drag a handle: that edge follows your finger and a soft block shows the cells the widget will cover. It snaps to whole
  cells, never smaller than the widget's own minimum size, never bigger than the widget's maximum or the page. The block
  turns **red** when it can not be there (another widget in the way, or no free cells for the apps under it).
  A short buzz marks each new size. Let go: the frame glides into the block and the widget takes the new size
  (the widget is told its new size, so widgets that change their look with their size do that).
- Growing over apps / folders: they step aside to the nearest free cells of that page (same rule as moving a widget).
  Let go on a red block = the widget keeps its old size ("No room to resize the widget there").
  Dragging the left / top handle moves the widget's top-left cell too, so it can grow or shrink in any direction.
- The frame stays after a resize so you can resize again. Tap outside the frame, Back or Home ends resize mode.
  While it is on, page swipes, the Apps screen and the widgets underneath do not react.
- The new size is saved at once (same "v3" layout format, nothing changed) and survives restarts. The widget keeps running
  while it is resized (its live view is reused).
- Not in this part: the built-in clock / date widget (6c).

## Step 6c1 - what is in it (Widgets, part 3a: the launcher's own widgets)

- Long-press an empty spot > Widgets: the first row of the picker is now **OneUI 9 Launcher** (it always sits above the other
  apps) with three widgets: **Clock** (4 x 2), **Mini clock** (2 x 2) and **Date** (2 x 2). Each card shows a real picture of the widget.
- They are real Android widgets that this app provides, so everything from 6a / 6b works with them the same way:
  add (the same "Allow ... to create widgets?" question once), long-press = lift + menu (Resize / Remove widget), drag to
  another spot or page, resize with handles, saved with the layout, removed with "Remove widget".
- **Clock**: big time with the date underneath (12 or 24 hour follows the phone's setting; the date is written the way the phone's
  language writes it, e.g. "Mon, Oct 19"). **Mini clock** is the same clock, smaller. White text with a soft shadow, no background,
  so it sits right on the wallpaper. The time ticks by itself; midnight changes the date by itself.
- **Date**: weekday, a big day number and the month on a soft glass tile (same glass as the dock and folders).
- The numbers fit the widget: make a Clock 2 x 1 or a Date 4 x 4 and the text shrinks / grows to fill it, nothing is cut off.
  The Clock can be made as small as 2 x 1 (a Date never smaller than 2 x 2) and as big as a whole page.
- Tap the clock: the phone's Clock app (alarms) opens. Tap the Date: the phone's Calendar opens.
  (If the phone has no such app, tapping does nothing.)
- (Choosing a style / colour / 12-24 h / hiding the date and an analog clock came in 6c2.)

## Step 6c2 - what is in it (Widgets, part 3b: setup screen, analog clock)

- **Setup screen** for the launcher's own widgets. It opens by itself when you add one (after the "Allow ... to create widgets?"
  question) and again any time from the widget's long-press menu > **Widget settings**. The top half shows the real widget, live,
  at its real size over your wallpaper; the bottom sheet has the choices. **Done** saves and updates the widget, **Cancel** / Back
  changes nothing (cancelling while adding a widget adds nothing).
  - **Style**: Digital or Analog (clocks only).
  - **Colour**: White, Black, Sky, Mint, Amber, Rose (all widgets; the date line, weekday and month use a slightly softer version).
  - **Time format**: Phone setting, 12 hour or 24 hour (digital clocks only).
  - **Date**: Show or Hide the date line under the clock (clocks only; hiding it lets the time grow to fill the space).
  - **Background**: None, Glass (the soft tile of the dock / folders) or Dark (a darker tile for bright wallpapers).
  Every widget remembers its own choices, so two clocks can look different. They are forgotten when the widget is removed.
- **Analog clock**: a new widget in the picker (OneUI 9 Launcher > Analog clock, 2 x 2): a round glass face with 12 tick marks, hour and
  minute hands and the date underneath. It ticks by itself, shrinks with the widget, and has a face in each of the six colours.
  The Clock and Mini clock can be switched to analog (and the Analog clock to digital) on the setup screen.
- **Widget settings** is also offered for any other app's widget that says it can be set up again (Android 9+).
- The Date widget starts on Glass, the clocks on None (just text on the wallpaper), as before.
- Not changed: how widgets are added, moved, resized and removed (6a, 6b1, 6b2), tapping the clock / date still opens the phone's
  Clock / Calendar app.
- This finishes Step 6.

## Step 7a - what is in it (Search + gestures, part 1 of 3)

- A search bar (glass pill, magnifier + "Search") sits above the page dots on the Home screen. Tap it: the Apps screen opens
  and the keyboard comes up, ready to type. Everything else about search is as on the Apps screen.
- On by default. Long-press an empty spot of a home page: "Hide search bar" / "Show search bar". The choice is remembered.
  (Step 9 adds the same switch to Home settings.) The pages make room for the bar, so icons never sit under it.
- Not in this part: swipe down for notifications, double-tap to lock (7b), pinch for the edit screen (7c).

## Step 7b - what is in it (Search + gestures, part 2 of 3)

- Swipe down anywhere on the Home screen (not on the Apps screen): the notification panel opens. On by default.
  If a phone refuses (a few ROMs block it) you get a short message instead.
- Double-tap an empty spot of a home page: the screen locks. Off by default. Long-press an empty spot >
  "Double-tap to lock: Off" turns it on; Android then asks once to allow "lock the screen" (device admin, that one
  policy only). Turn the admin off any time in Settings > Security > Device admin apps.
- Both switches are in the long-press menu of an empty spot (next to Widgets and the search bar). Step 9 repeats them in Home settings.
- Swipe up (Apps screen) and horizontal page swipes work as before. The swipe-down is ignored while a folder, the widget
  picker or widget resize is open.
- Not in this part: pinch for the edit screen, polish (7c).

## Step 7c - what is in it (Search + gestures, part 3 of 3)

- Pinch in with two fingers on the Home screen (or long-press an empty spot > "Edit Home screen"): the home screen
  shrinks back a little and a bar slides up with **Widgets** (opens the picker), **Search bar: On / Off** and **Done**.
- Tap the zoomed-out home, Done, Back or Home: it zooms back. The Apps screen (swipe up) is off while the edit screen is open.
- Pinch is ignored while a folder, the picker, widget resize or a drag is active, and on the Apps screen.
- Not in this step: moving icons / pages inside the edit screen, wallpaper and Home settings (they come with Steps 8 and 9).

## Step 8a - what is in it (Look and feel, part 1 of 4)

- The wallpaper now slides sideways a little when you swipe between pages (it stays in the middle when there is one page).
  Long-press an empty spot > "Wallpaper scroll: On / Off". Works with any wallpaper that is wider than the screen.
- Edit screen (pinch in) > **Dock**: a card opens with 6 colours (white, black, blue, purple, green, pink) and an
  **Opacity** slider (0 = no background, 100 = solid). It changes live and is remembered. Default = white 22%, the same look as before.
- The edit bar is now: Widgets | Dock | Search: On/Off | Done.
- Not in this part: blur (8b), dark / light mode (8c), themed icons (8d).

## Step 8b - what is in it (Look and feel, part 2 of 4)

- Android 12 and newer: the wallpaper behind the launcher gets blurred while you pull up the Apps screen (it follows
  your finger), and while a folder or the widget picker is open. Long-press an empty spot > "Blur: On / Off".
- Older phones, and phones that switched window blur off (battery saver, some low-end models), just keep the dim background.
- A blurred glass dock is not possible for a launcher: Android only blurs the whole window behind it, not one small tile.
  The dock keeps its colour + opacity from 8a.
- Not in this part: dark / light mode (8c), themed icons (8d).

## Step 8c - what is in it (Look and feel, part 3 of 4)

- The panels now have a dark and a light look: the Apps screen (veil, search pill, labels, A-Z strip), the open-folder card,
  the quick menu, the widget picker and the dock card of the edit screen.
- Long-press an empty spot > **Panels: Auto / Light / Dark** (each tap goes to the next one; the screen restarts once).
  Auto follows the phone's dark mode, also when you change it later.
- In light mode the status / navigation bar icons turn dark while the Apps screen is open.
- The Home screen keeps white labels in both modes, because they sit on your wallpaper. Edit-screen buttons and the drop
  zones stay dark glass in both modes.
- Not in this part: themed icons (8d).

## Step 8d - what is in it (Look and feel, part 4 of 4)

- Themed icons: all app icons (Home, dock, folders, Apps screen, widget picker) get ONE colour family: a soft plate with the
  app's glyph on it. Long-press an empty spot > **Themed icons: Off / Wallpaper / Blue / Purple / Green / Pink / Orange**
  (each tap goes to the next one; the icons are redrawn at once, no restart).
- Wallpaper = the main colour of your wallpaper (Android 8.1+). When you change the wallpaper, the icons follow the next time
  you come back to the launcher. Phones that can not tell the wallpaper colour use blue.
- Android 13+: apps that ship a monochrome layer show it as the glyph. Apps without one are turned into two tones of the same
  colour (dark parts = plate colour, bright parts = glyph colour), so every icon still fits.
- Dark panel mode (8c): dark plate + light glyph. Light mode: light plate + dark glyph.
- Off = the normal squircle icons, as before. The choice is remembered.
- This finishes Step 8.

## Step 9a - what is in it (Settings + extras, part 1 of 2)

- Long-press an empty spot > **Home settings** opens a settings screen (dark or light like the panels, see 8c).
- **Home screen grid**: 4 x 5, 4 x 6 (default), 5 x 5 or 5 x 6 icons per page (columns x rows). Icons and widgets that no longer
  fit move to free spots; a widget that fits nowhere is dropped.
- **Icon size**: Small / Medium (default) / Large for the icons on the home pages. Large never grows so much that the label is squeezed.
- **Hide apps**: a list of every app with a switch. A hidden app disappears from the Home screen (also from folders and the dock)
  and from the Apps screen and its search. Switch it off again to get it back in the Apps screen (it does not jump back to Home).
- Every choice is saved at once. When you go back to the Home screen it restarts its screen once and shows the new look.
- Not in this part: notification badges, Edge panel, landscape (9b).

## Step 9b - what is in it (Settings + extras, part 2 of 2)

- **Notification badges** (Home settings > Notification badges: Off / Dot / Number): a red dot, or the number of notifications,
  on the top-right corner of an app's icon (Home, dock, Apps screen, folder card, Edge panel). Turning it on opens the phone's
  "Notification access" screen: allow "OneUI 9 Launcher" there. The launcher only counts notifications (ongoing ones such as a
  music player and group summaries do not count); it never reads or stores their text. Without the access nothing is shown.
- **Edge panel** (Home settings > Edge panel: On, then "Edge panel apps"): a thin handle on the right edge of the Home screen.
  Tap it or drag it to the left: a column of your chosen apps (up to 12, A-Z, with labels and badges) slides in. Tap an app to
  start it; tap outside, Back or Home closes it. The Apps screen, pinch and swipe-down are off while it is open.
- **Landscape** (Home settings > Screen turning: Allow landscape): the Home screen turns with the phone. Same grid, but the icons
  lose their labels and use nearly the whole row, and the pages keep clear of a side navigation bar or the notch. Other screens
  (widget setup, Home settings) stay upright. Portrait only is the default.
- Not done on purpose: a separate layout per orientation and the Edge panel's own tools (clipboard, shortcuts...); the panel
  holds apps only.
- This finishes Step 9.

## Step 10a - what is in it (Polish + release, part 1 of 4)

- Faster app list: the shaped icons are kept in a memory cache. After an install, an update or a theme change only the apps that
  really changed are drawn again (the cache key holds the app's update time, the icon size and the themed-icon colours).
- No needless rebuilds: when the phone sends an install / update broadcast that changes nothing the launcher shows, the Home
  pages, the dock and the Apps screen are left alone (no flicker, widgets are not touched).
- Not in this part: backup / restore (10b), own icon and About (10c), signed release build (10d).
## Step 10b - what is in it (Polish + release, part 2 of 4)

- Long-press an empty spot > Home settings > **Back up layout and settings**: Android's file picker opens; choose where to save
  `OneUI9Launcher-backup-DATE.txt` (Downloads, Drive...). It holds the Home layout, folders, dock and every setting.
- **Restore from a backup**: pick such a file; after a question the layout and settings are replaced and the Home screen rebuilds
  itself when you go back to it. A file that is not a backup is refused and nothing changes.
- Widgets are not in a backup (Android gives every widget an id that only exists on this phone): add them again after a restore.
  The look of the launcher's own clock / date widgets is not saved either.
- Not in this part: own icon and About (10c), signed release build (10d).
## Step 10c - what is in it (Polish + release, part 3 of 4)

- New launcher icon: a blue-to-violet gradient with four rounded tiles (one amber). It also has a monochrome layer, so on
  Android 13+ it follows the phone's own themed-icon setting.
- Home settings > **About**: the icon, the name, the version, a short description and the note that the code and icons are original.
- The name is written in one place (`app_name` in `app/src/main/res/values/strings.xml`); the launcher's messages read it from
  there. To rename the launcher, change that one line (the label under the icon follows). The package name stays the same.
- Not in this part: signed release build (10d).

## Step 10d - what is in it (Polish + release, part 4 of 4)

- **Signed, shrunk release build**: in the project folder run `bash release.sh`. The first run makes your own signing key
  (`oneui9-release.jks` + `keystore.properties`); every run after that reuses it. The APK lands in Downloads as
  `OneUI9Launcher-release.apk` and the installer opens.
- **Back up the two key files** (cloud drive, another phone). Without the same key Android refuses to update the app.
  They are in `.gitignore`, so `push.sh` never uploads them.
- The release build removes unused code and resources (R8 + resource shrinking, rules in `app/proguard-rules.pro`), so the APK is
  smaller and not debuggable. Crash traces keep file names and line numbers.
- Version is now **1.0.0** (code 110); the About screen shows it.
- A release APK has a different signature than the debug APK from `build.sh`, so it can not be installed over it. First use
  Home settings > Back up layout and settings, uninstall the debug app, install the release APK, then Restore.
- Cloud: `bitrise.yml` has a second workflow, `release`. Add the secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
  `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` in Bitrise; without them it builds an unsigned APK.
- This finishes Step 10 and the 10-step plan.

## Step 11 - Play Store pack

- `docs/` holds the public pages (`privacy-policy.html`, `terms.html`, `index.html`) for GitHub Pages; `PLAY_STORE.md` has the checklist,
  store listing text, Data safety / content rating answers and reviewer notes. Read its section 0 first.
- App: now targets Android 16 (API 36) as Play requires; `release.sh` also builds `OneUI9Launcher-release.aab` for the Play upload.
- Home settings > About now has **Privacy policy** and **Terms of use** links (addresses in `strings.xml`: replace `YOUR_NAME`),
  and turning on notification badges first shows a short dialog that says what the access is for.
- Version 1.0.1 (code 111).

## Step 12 - Look like the reference

- **12a Icons.** Phone, Messages, Contacts, Camera, Albums / Gallery, Browser, Calendar (shows today's date), Clock, Calculator, Files, Settings and Weather
  get the launcher's own colourful icons (drawn in code, original). Only apps that came with the phone are replaced; Chrome, Instagram, games... keep their
  own icon. The squircle is a little rounder. Long-press an empty spot > **Icon style: Colourful / Original**. Themed icons (8d) override it while on.
- **12b Apps screen.** Swipe up from Home opens it as before; inside, swipe left / right between pages (4 columns, up to 5 rows), page dots,
  the search pill at the bottom, the sort menu (three dots) at the top right. A swipe down closes it. The A-Z strip is gone.
- Version 1.0.2 (code 112).

## Step 13 - Settings app, Apps screen grid, more icons, Contact and policies

- **The app icon opens Settings.** The launcher's icon in the phone's app list (and in the Apps screen) is the Settings app. The Home activity has only the HOME category (some phones hide an app whose Home activity also has LAUNCHER). Open the icon > "Make this your Home app".
- **Settings UI.** Long-press an empty spot on Home > **Settings** (or three dots on the Apps screen > Apps screen grid...,).
  A categorized settings app with colour tiles: Home screen (grid, icon size, search bar) - Apps screen - Dock (colour, opacity) - Icons - Look and feel
  (panels, blur, wallpaper scroll) - Gestures (swipe down for notifications, double-tap lock) - Notification badges - Edge panel - Hide apps - Screen turning -
  Backup and restore - Contact us - Privacy policy - Terms of use - Permissions and data use - About. Every switch is saved at once; Home rebuilds itself when you go back.
- **Apps screen grid.** Settings > Apps screen: Columns (Auto / 3 / 4 / 5 / 6), Rows (Auto / 4 / 5 / 6 / 7) with a live preview and "N apps per page",
  Icon size (Small / Medium / Large), Show app names, Sort. Icons shrink by themselves when a cell is too small.
- **Icons.** Nine more launcher-drawn icons for generic system apps: Mail, Music, Videos, Maps, Notes, Recorder, Downloads, Games, Store (same squircle, drawn in code).
  Brand apps (Facebook, Instagram, WhatsApp, Chrome, YouTube, Gmail...) keep their own real icon on purpose: their logos are the owners' trademarks and are not redrawn.
  Settings > Icons: Colourful / Original and Themed icons (Off, Wallpaper, 5 colours).
- **Contact and policies, inside the app (no internet needed).** Settings > Contact us (Email us, Report a problem with app / Android / phone model added, Privacy question,
  Copy address), Privacy policy, Terms of use, Permissions and data use (every permission and why), About (rate, share, report, code / icons / trademarks).
  The text is in `LegalText.kt` and says the same as `docs/privacy-policy.html` and `docs/terms.html`. New page `docs/contact.html`.
  The contact address, developer name and website are in `strings.xml` (`contact_email`, `developer_name`, `website_url`): change them there once.
  The "online" rows stay hidden until `YOUR_NAME` in `privacy_url` / `terms_url` / `website_url` is replaced (see PLAY_STORE.md).
- **Crash note.** If the app ever crashes, the error is saved on the phone and shown on the next Settings start (Copy / Email / Clear). Never sent by the app.
- **Easier page swipe.** Pages (Apps screen and Home) now start moving after half the touch slop on any mostly-sideways move, a fast short flick turns the page, a drag of ~18% of a page is enough, and a slanted swipe on the open Apps screen no longer closes it.
- Version 1.0.3 (code 113).

## Step 14 - Gradient glass backgrounds for the clock widgets

- Long-press a Clock, Mini clock or Analog clock widget > **Widget settings** > **Background**: next to None / Glass / Dark there is now a
  second row, **Gradient glass**, with five themes shown as little tiles with their names:
  **Aurora** (teal to violet), **Sunset** (orange to pink), **Ocean** (blue to cyan), **Forest** (green to deep teal) and **Midnight** (navy to purple).
  Tap a tile and the live widget at the top changes at once; **Done** saves it for that widget.
- It is real frosted-glass look: the colours are see-through, so your wallpaper glows through them; there is a soft light sheen from the top
  and a hairline light edge, with the same rounded corners as the Glass and Dark tiles. White text (or any colour you pick) reads on all five.
- Every widget keeps its own choice (two clocks can use two themes). The Date widget uses the same row, since it shares the Background setting.
- Nothing changes for widgets you already have: None / Glass / Dark are saved as before and look the same. The save format is unchanged
  (the background number just goes up to 7).
- The colours live in one table (`WidgetThemes` in `WidgetConfig.kt`); the five drawables `res/drawable/widget_theme_*.xml` are written from it.
- Version 1.0.4 (code 114).

## Step 15 - Dock gradients and gradient wallpapers (portrait and landscape)

- **Dock gradient.** Settings > Dock (and the Dock card of the edit screen) now has a **Gradient** row under the colours: None + 12 gradients
  (Aurora, Sunset, Ocean, Forest, Midnight, Rose, Lavender, Peach, Mint, Graphite, Candy, Ember). The dock pill becomes a left-to-right glass gradient with a hairline
  light edge; the **opacity** slider still works on it. Picking a plain colour switches the gradient off. Picking a gradient while the opacity is under 40%
  lifts it to 75% (22% was too faint to see). Settings > Dock shows a live preview pill.
- **Gradient wallpapers.** New **Settings > Wallpaper** (also: long-press an empty spot on Home > **Wallpaper**). 12 gradient wallpapers drawn in code
  (same table as the dock, `Gradients.kt`: gradient + two soft glows + a shade at the bottom so the dock reads well).
  - **Made for:** Portrait (tall), Landscape (wide) or Both (a square, which the phone crops well in either direction). The tiles change shape with the choice.
  - **Set on:** Home, Lock or Both. Tap a tile, then the big button. The picture is made at your real screen size (never above 2560 px) and given to Android.
  - New permission `SET_WALLPAPER` (normal, no prompt). Added to Permissions and data use + `docs/privacy-policy.html`. No photos are read.
- Saved key: `dock_gradient` (0 = none, 1.. = table order). Old docks look exactly the same. Backup and restore carry it automatically.
- **Left / not done:** Android keeps ONE wallpaper picture, so "Portrait" and "Landscape" are two ways to cut it, not two pictures that swap on rotation
  (that needs the phone's own wallpaper service). Not tested on a real phone; compile-checked only (R-free sources, 0 errors).
- **Search bar gradient.** Settings > Home screen > Search > **Search bar gradient**: None + the same 12 gradients. The Home search pill becomes a left-to-right
  glass gradient (about 75% clear, hairline light edge); None = the old soft white glass. Saved key `search_gradient`. The Apps screen's own search pill is unchanged.
- Version 1.0.5 (code 115).

## Step 16a - Quick panel, part 1 of 5

- **Swipe down on Home** now has a setting: Settings > Gestures > **Swipe down opens**: Notifications (the phone's own panel, as before) / Quick panel / **Split** (default).
  Split = swipe down on the **left half** for notifications, on the **right half** for the Quick panel (like One UI's "Separate" panel). The old on / off switch is still there.
- **The Quick panel** slides down from the top (blurred wallpaper behind it on Android 12+, dark / light like the other panels): time and date, a gear (opens the phone's Settings),
  two wide buttons, a grid of round buttons, the brightness slider and a grab bar. Tap outside, swipe up, Back or Home closes it.
- **Buttons (10):** Wi-Fi, Bluetooth (wide), Mobile data, Flight mode, Sound (Sound > Vibrate > Mute), Torch, Auto rotate, Do not disturb, Location, Mobile hotspot.
  All icons are drawn in code (`QuickGlyph.kt`). A lit button = on. The state updates by itself while the panel is open.
- **What really switches, and what cannot (Android rule, not a bug):**
  - Work directly: **Torch**, **Sound mode** (Mute asks for Do-not-disturb access once), **Auto rotate** (asks "Modify system settings" once), **Do not disturb** (asks DND access once), **Brightness** (same one-time "Modify system settings").
  - Show the real state, then **open the phone's own small panel** on tap (long-press = the full settings page): Wi-Fi, Bluetooth, Mobile data, Flight mode, Location, Hotspot. Android does not let a normal app switch these; only the system or Shizuku / root can.
  - Mobile data shows "On" when a mobile-data network exists; Hotspot has no state (Android hides it).
- New permissions (all documented in Permissions and data use + `docs/privacy-policy.html` + PLAY_STORE.md): ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE, BLUETOOTH (API 30-), WRITE_SETTINGS, ACCESS_NOTIFICATION_POLICY.
- Saved keys: `swipe_down_mode` (0 Notifications, 1 Quick panel, 2 Split), `qp_tiles` (the button order; 16b makes it editable). Backup and restore carry both.
- **Only on the Home screen:** the panel is part of the launcher, so it opens from the Home screen only. Opening it over other apps (replacing the system panel) would need an Accessibility service: planned as an optional extra in 16e, not promised.
- **Left for 16b-16e:** editing the buttons, notifications inside the panel, volume + media + device cards, panel settings, finger-follow pull-down. Not tested on a real phone; compile-checked only.
- Version 1.0.6 (code 116).

## Step 16b - Quick panel, part 2 of 5 (edit the buttons)

- **Pencil** in the Quick panel's header (next to the gear) opens **Edit buttons**: the panel's buttons in a box at the top, **Available buttons** in a box under it.
  - **Move / add:** touch and hold a button, then drag. A white bar shows where it will land in the top box. Drop an available button there = it joins the panel; drop a panel button in another place = it moves.
  - **Remove:** tap the red minus on a panel button, or drag it out of the top box (it fades while you hold it outside). At least **6** buttons must stay.
  - **Add by tap:** a tap on an available button puts it at the end of the panel.
  - The **first two** buttons of the panel are the wide ones (name + state), the rest are round. **Reset** brings back the first ten buttons; **Done** saves; Back / Home / tap outside leaves without saving.
- **4 new buttons** (14 in all): **Battery saver** (real state; tap opens the phone's battery saver page), **Dark mode** (real state; tap opens Display settings), **Screen cast** (opens the phone's cast settings), **Camera** (opens the camera).
  A fresh install shows the same ten buttons as 16a; the four new ones wait under "Available buttons". The panel grows by itself when it holds more buttons (no pages yet).
- The order is saved (`qp_tiles`, same key as 16a) and carried by backup / restore. No new permissions.
- Not done on purpose: dark mode / battery saver cannot be switched by an app (Android rule), and a separate page of buttons (swipe sideways) is not in this part.
- Version 1.0.7 (code 117).

## Step 16c - Quick panel, part 3 of 5 (notifications in the panel)

- **Notifications under the brightness slider.** A "Notifications" heading with **Clear all**, then one card per notification, newest first:
  app icon, app name and age (now / 5m / 3h / 2d), title and text (2 lines). The list scrolls inside the panel when it is long
  (it never takes more than about a third of the screen height), and a swipe up on the list scrolls it instead of closing the panel.
- **Swipe a card sideways** to remove it (far enough or a quick flick); it slides away and the phone removes the notification.
  A notification the app marked "can not be cleared" only wobbles. **Clear all** removes every notification that can be removed.
- **Tap a card** to open it: the app's own intent runs (the right chat, the right mail), or else the app opens; the panel closes. Cards whose app asked for
  "remove when tapped" are removed too.
- **No access yet?** One card says "Allow notification access..." with an **Allow** button that opens the phone's access screen. "No notifications" when the list is empty.
- **Switch:** Settings > Gestures > **Notifications in Quick panel** (on by default). Off = no title or text is read at all.
- **Privacy:** same Notification access as the badges. The text is held in memory only while the notifications are showing (up to 40), never saved or sent.
  Ongoing notifications (music, downloads) and group summaries are not listed; notifications an app marks "secret" are left out.
  The access dialog, Permissions and data use, `docs/privacy-policy.html` and PLAY_STORE.md (Data safety + permission declaration) now say this.
- Code: `NotifLogic.kt` (pure rules, JVM-tested), `Notifs.kt` (store + removing), `QuickNotifs.kt` (the list), `BadgeService` also feeds the store. Saved key `qp_notifs`.
- Not done on purpose: grouping by app, reply / action buttons on a card (16d+ if wanted), the panel over other apps.
- **Left for 16d-16e:** volume + media cards, Media output, Device control row; panel settings, polish, optional system-wide mode.
- Version 1.0.8 (code 118). Compile-checked and rules JVM-tested; not tested on a real phone.

## Step 16d - Quick panel, part 4 of 5 (media output, devices, media card, volume)

Under the brightness slider (above the notifications) the panel now has three more things. One switch turns all of them off: Settings > Gestures > **Volume and media in Quick panel** (on by default).
- **Media output + Devices row.** Two wide buttons. **Media output** shows where the sound goes now (a Bluetooth speaker / headphones by name, "Headphones" for wired, else "Phone")
  and opens the phone's output switcher. **Devices** opens SmartThings or Google Home when one is installed (its name is shown), else Bluetooth settings.
  Honest note: Android has no public "switch output" screen, so Media output is best effort: it tries the system's media output panel, then the volume panel, then Bluetooth settings.
  Real smart-home device controls (lights, locks) need each home app's own Device Controls: not done.
- **Media card.** Shows only while something is playing (or paused with a title): cover, app name, title, artist, **previous / play-pause / next**. Tap the card to open the player.
  Needs Notification access (Android only lets a notification listener see media sessions); without it the card just stays hidden. The title is held in memory only, never saved or sent.
- **Volume card.** A **Media** slider (same pill as brightness) and an arrow that opens **Ring** and **Alarm** sliders. The sliders follow the phone's volume keys while the panel is open.
  Ring to silent can be refused by Android while Do not disturb limits it: the panel then says so.
- Code: `CardLogic.kt` (pure rules: slider to volume step, which session to show; JVM-tested), `QuickCards.kt` (the cards), nine new icons in `QuickGlyph.kt` (play, pause, previous, next, output, devices, arrows, bell, alarm). No new permission.
- Saved key: `qp_cards`. Privacy text updated (access dialog, Permissions and data use, `docs/privacy-policy.html`, PLAY_STORE.md).
- **Left for 16e:** panel settings (panel on the left side, where brightness sits, reset layout), polish, optional system-wide mode.
- Version 1.0.9 (code 119). Compile-checked and rules JVM-tested; not tested on a real phone.

## Step 16e - Quick panel, part 5 of 5 (panel settings, polish)

- **Settings > Quick panel** (new page; also a row in Settings > Gestures and on the main page):
  - **Swipe down opens** (Notifications / Quick panel / Split) - the same switch as in Gestures.
  - **Quick panel side (Split):** Right half (default) or **Left half**. In Split mode a swipe down on that half opens the Quick panel, on the other half the phone's notifications.
  - **Brightness slider:** Below the buttons (default) or **Under the clock**.
  - **Show in the panel:** Notifications, Volume and media (the two switches moved here from Gestures).
  - **Reset Quick panel** (asks first): the buttons go back to the first ten, slider below the buttons, panel on the right half, notifications and media on again.
- **Polish:** the panel can no longer grow past the screen: when buttons + cards + notifications are too tall it scrolls inside itself (a swipe up still closes it once it is scrolled to the end,
  and a long notification list scrolls on its own first). A tiny tick when you tap a button. Saved keys: `qp_left`, `qp_bright_top` (both in backup and restore).
- Rules JVM-tested: which half opens the panel (`CardLogic.splitOpensQuick`).
- **Not done (decided):**
  - **Panel over other apps (system-wide).** It would need an Accessibility service drawing a touch strip over the status bar. I did not build it: it cannot be tested without a real phone, many phones
    (One UI, MIUI) do not let overlays take the status-bar swipe, and Google Play treats Accessibility services as a restricted permission that needs its own declaration. The Quick panel stays a Home-screen panel.
  - **Finger-follow pull-down** (the panel following your finger while you drag): the panel opens with a short slide after the swipe is recognised. Needs touch handling in `LauncherRoot`; left out to avoid breaking page swipes.
  - Separate pages of buttons (swipe sideways), reply buttons on notifications, real smart-home device controls.
- Quick panel is now **complete for this plan (16a-16e)**. Version 1.0.10 (code 120). Compile-checked; not tested on a real phone.

## Step 17a - Notifications page (reference video), part 1 of 3

- A swipe down on the Home screen (left half in Split mode, or always when "Swipe down opens" = Notifications) now opens the launcher's **own full-screen Notifications page**
  instead of the phone's panel: the wallpaper blurred behind a soft veil, **time and date** at the top left, then your notifications as glass cards (newest first;
  swipe a card sideways to remove it, tap it to open the app), or a big centred **No notifications**. At the bottom: **Notification settings** (left) and **Clear all** (right, only when something can be cleared).
- Tap an empty spot, swipe up, Back or Home closes it. A long list scrolls by itself first.
- Without Notification access the page shows one centred message with an **Allow** button.
- Settings > Quick panel > **Notification panel**: *Launcher's page* (default) or *Phone's panel* (the old behaviour). Saved key: `np_own`.
- Not in this part: the Quick panel's new look (17b) and swiping between the two pages (17c).
- Compile-checked only; not tested on a real phone. Version 1.0.11 (code 121).

## Step 17b - Quick panel look (reference video), part 2 of 3

- **No card any more:** the Quick panel's buttons float over the blurred wallpaper under the same soft veil as the Notifications page (no rounded white box).
- **Header like the reference:** time and date on one line at the left (time big, date small), and **pencil (edit) - power - gear (settings)** at the right.
  The **power** icon locks the screen (the phone's own power menu is not open to launchers); it works once "Double-tap to lock" is allowed (long-press an empty spot on Home), else it says how to turn it on.
- **Brightness** slider has a round **moon** button at its right end: it opens the phone's Display settings (dark mode). **Volume** slider has a round **speaker** button at its right end: it opens / closes the Ring and Alarm sliders.
- Wide Wi-Fi and Bluetooth buttons, the round button grid, Media output / Devices and the media card stay as they were (they already matched the reference).
- Not in this part: the edit look ("Panel settings - Touch and hold to change order - Done") and swiping between the Quick panel and the Notifications page (17c).
- Compile-checked only; not tested on a real phone. Version 1.0.12 (code 122).

## Step 17c - edit look and swiping between the panels (reference video), part 3 of 3

- **Edit look:** the button editor (pencil in the Quick panel) now has the reference header: **Panel settings** with *Touch and hold to change order* under it, and **Reset** / **Done** as plain words at the right (no more pills).
- **Swipe between the two panels:** on the Quick panel swipe **left** to go to the Notifications page; on the Notifications page swipe **right** to come back. No trip through the Home screen in between.
  A sideways swipe that starts on a notification card still just removes that card, and a swipe on a slider moves the slider. The swipe to the Notifications page only works when "Notification panel" is *Launcher's page* (Settings > Quick panel).
- The Quick panel and Notifications page plan (Step 17) is **complete**. Not done: a finger-follow pull-down, the second page of buttons. Compile-checked only; not tested on a real phone. Version 1.0.13 (code 123).

## Step 18 - fixes after testing (crash, blur, frosted look)

- **Crash fixed** ("The specified child already has a parent", `NotificationPage.build`): the page reused its "Clear all" button every time it opened,
  and the second opening tried to add a button that still sat in the old row. It now makes a new button each time. A safety net also stops
  any other drawing problem on that page from closing the launcher (it shows a message instead).
- **The Home screen behind the Quick panel and the Notifications page is blurred**: its icons, widgets and dock are blurred by the launcher
  itself (Android 12+), so it works even on phones that switch the wallpaper blur off (some vivo / battery-saver cases). It follows
  Settings > Blur.
- **Darker veil when the phone can not blur the wallpaper**: then the wallpaper stays sharp, so the dark layer over it is stronger.
- **Dark frosted look like the reference**: the buttons that are off, the sliders' empty track, the volume / media cards, the notification
  cards and the pills are now dark translucent glass (white / milky in light mode); buttons that are on stay bright.
- Not tested on a real phone: compile-checked only. Version 1.0.14 (code 124).

## Step 19 - fixes after testing 2 (slider levels, one rectangle, icon set)

- **Brightness and sound levels now show every time the panel opens.** The bar used to be placed through layout parameters while the
  panel was still hidden, and after the second opening it stayed invisible. It is now placed during the layout itself.
- **One dark frosted rectangle behind the round buttons** (the two rows of four), like the reference; the wide Wi-Fi / Bluetooth buttons stay
  outside it. The sliders keep their dark frosted track with the light bar on top.
- **More icons in the icon set** (Settings > Icons > Colourful): Health, Wallet, Tips and Themes join Phone, Messages, Contacts, Camera, Gallery,
  Browser, Calendar, Clock, Calculator, Files, Settings, Weather, Mail, Music, Videos, Maps, Notes, Recorder, Downloads, Games and Store.
  Every icon now has a soft light from the top (glossy, like the reference set). They are drawn in code from simple shapes: original art in the
  same style, not Samsung's pictures. Only apps that came with the phone and have a plain name get one; every other app keeps its own icon.
- Not done: the extra quick buttons of the reference (Power saving, Screen record, Dark mode, Eye comfort, Share); say so if you want them next.
- Compile-checked only; not tested on a real phone. Version 1.0.15 (code 125).

## Step 21a - Quick panel fixes (part 1 of 3)

- The panel now ends right above the navigation bar (the empty strip is gone); the lower box (player + notifications) takes only the space that is left and is cropped at the bottom, so it fits the screen.
- Sliders (brightness, volume) no longer jump when a swipe up starts on them: they react only to a sideways drag or a clean tap. Swipe up to close is easier.
- Tapping a notification card in the panel now starts it with the permission Android 14+ wants.
- Settings: a "Set as default" row is always at the top.
- Left: 21b (panel colours: white glass default, 5 colours, 5 gradients), 21c (single-tap popups for Wi-Fi etc., system launcher showing up sometimes).
- Version 1.0.17 (code 127). Compile-checked only.

## Step 21b - Panel colours (part 2 of 3)

- Settings > Quick panel > **Panel colour**: White glass (default), 5 plain colours (Blue, Purple, Green, Red, Graphite) and 5 gradients (Aurora, Sunset, Ocean, Forest, Midnight). Tints the veil behind the Quick panel and the Notifications page and the glass of the buttons and cards. Light mode uses a pale version so text stays readable.
- Saved key `qp_theme` (0 = white glass). Applies the next time the panel opens. Version 1.0.18 (code 128). Compile-checked only.
- Left: 21c.

## Step 21c - Tool popups (part 3 of 3)

- Single tap on Wi-Fi, Mobile data and Flight mode opens the phone's own small popup panel (Android 10+). Bluetooth, Location, Hotspot, Battery saver, Dark mode and Screen cast open a small popup of the launcher (state + "Open settings"), the panel stays until you leave. Long-press still opens the full settings page.
- "System launcher shows up sometimes": no code change, cause not found without a phone. Check the phone's battery / autostart settings for this app (some phones kill the launcher in the background), and Settings > Set as default. A crash is saved by the app and shown in Settings.
- Version 1.0.19 (code 129). Compile-checked only.

## Step 22 - Quick panel in every app (optional)

- **Settings > Quick panel > In every app:** switch **Open in every app**. Pull the status bar down in any app and the launcher's Quick panel opens instead of the phone's own panel (swipe sideways for the Notifications page).
- **How it works (honest):** Android does not let an app replace the phone's panel. `PanelAccessibilityService` listens only to "window changed" events (no screen text is read), notices System UI opening its panel, closes it (`GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`, Android 12+; older phones get Back) and shows the Quick panel in an accessibility overlay window (`SystemPanel.kt`). Expect a short flicker.
- **Phones differ:** the phone's panel is recognised by its window name / title ("Notification shade"). If it does not open on your phone: switch on **Detect any system panel**, and send me the line "Last system window seen" from that page so I can add your phone's name. **Show panel now** tests the overlay itself.
- **Setup:** the switch sends you to Android's Accessibility screen (a dialog explains first). On Android 13+ for apps installed outside Google Play, allow "Restricted settings" in App info first or the switch stays grey.
- Popups of the panel (Bluetooth, Location...) become overlay windows there. No new manifest permission except the Accessibility service itself. Permissions page, `docs/privacy-policy.html` updated. Google Play treats Accessibility as a restricted permission (needs its own declaration): see PLAY_STORE.md.
- Not tested on a real phone: compile-checked only, and the pure recognising rule is JVM-tested. Version 1.0.20 (code 130).

## Cloud build (Bitrise) - no Termux build needed

`push.sh` puts the project on GitHub; Bitrise builds the APK from there.

```
bash push.sh https://github.com/YOUR_NAME/OneUI9Launcher.git   # first time (empty PUBLIC repo)
bash push.sh                                                    # every later step
```

`bitrise.yml` in the project root is the cloud workflow: clone, `assembleDebug`, publish the APK (workflow `primary`).
The `release` workflow does the same for the signed release APK (see Step 10d).

## Step 20 - music player in the Quick panel's lower area

- **A music player now sits in the lower area of the Quick panel**, above the notifications (the area under the volume slider). It is always there:
  while something plays it shows the art, title, artist, a seek bar (tap or drag to jump) and previous / play-pause / next; a tap on the card opens the playing app.
  What is playing needs Notification access (same as before).
- **When nothing plays** it shows the phone's own music app (its icon and name, "Tap to open"). The three buttons then send the phone's media keys.
- **The lower area is one frosted box that stretches to the bottom of the screen**: the player on top, the Notifications list under it taking all the space that is left.
  The panel is now as tall as the screen; the Media output / Devices row and the volume card stay above it. Switch the player off with Settings > Gestures > Volume and media.
- The old media card between Media output and the volume card is gone (the player replaces it).
- Compile-checked only by reading; not built or tested on a real phone. Version 1.0.16 (code 126).
