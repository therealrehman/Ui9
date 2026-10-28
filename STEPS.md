# OneUI 9 Launcher - 10-step build plan

A One UI 9-style home screen for any Android 8.0+ phone (API 26+), written from scratch.
Original code and icons - nothing is copied from Samsung.

## Build + install (Termux, one command)

```
cd ~/storage/downloads && unzip -oq "$(ls -t OneUI9Launcher-step01*.zip | head -n1)" -d ~/OneUI9Launcher && cd ~/OneUI9Launcher && bash build.sh
```

Each new step ships as a full project zip. Use the same command with the new zip name
(step02, step03, step04, step05a, step05b, step05c, step06a, step06b1, step06b2, step06c1, step06c2, step07a, step07b, step07c, step08a, step08b, step08c, step08d, step09a, step09b, step10a, step10b, step10c, step10d, step11, step12, step13, step14, step15, step16a, step16b, step16c, step16d, step16e, step17a, step17b, step17c, step18, step19, step25). Your `local.properties` is kept between steps.

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

## Step 26 - the back layer of the panels is 100 % solid

- The layer at the very back of the **Quick panel** and the **Notifications page** (the coloured veil / gradient) now has an opacity of 100 %. The icons of the
  Home screen, the dock and the text of the app underneath (for example Settings) no longer show through it, on the Home screen and in the "In every app" panel alike.
  It is one number: `PanelTheme.BACK_OPACITY` (255). Lower it (for example 235) to let a little of the background through again.
- The buttons, sliders and cards keep their own frosted look on top of it.
- Version 1.0.26 (code 136). Compile-checked only by reading; not tested on a phone.

## Step 25 - no blur behind the Quick panel, ten more panel gradients

- **Glitch fixed ("apps shown twice" after swiping the panel up):** in other apps the panel used to put a blurred picture of the screen (and the phone's window blur)
  behind itself. While it closed, that picture and the live app behind it did not line up, so the apps looked doubled. The frosted backdrop of the Quick panel is
  now off everywhere: in other apps, and on the Home screen (the Home screen behind the Quick panel is no longer blurred). The panel's own veil is stronger
  (205 instead of 150 / 200) so the apps behind stay calm. The Notifications page keeps its blur on the Home screen.
  To bring the old backdrop back in other apps, set `BLUR_BEHIND_PANEL = true` in `SystemPanel.kt`.
- **Ten more gradients for the Quick panel** (Settings > Quick panel > Panel colour, scroll the dots): Twilight, Cherry, Arctic, Neon, Sand, Lagoon, Plum, Citrus,
  Steel, Dawn. They sit after the five older gradients (saved choices 0-10 are unchanged); the dock and wallpaper gradients are not touched.
- Compile-checked only by reading; not built or tested on a phone. Version 1.0.25 (code 135).

## Step 24c - panel blur and un-blur are fast

- **Dismissing:** the blurred picture behind the panel now fades out in 80 ms the moment the panel starts to close, and the phone's own window blur is switched off at the same moment, so the apps behind look sharp again at once (before, they stayed blurred for the whole slide-away).
- **Opening / closing animations are faster** (open 150 / 210 ms, close 110 / 150 ms; the Home screen's blur follows in 150 ms).
- **The phone's window blur is only a fallback now.** It is slow on many phones, so when the picture of the screen can be taken (Android 11+, screenshot permission allowed) only that picture is used. If the picture is not available or is late (over 0.3 s) the window blur steps in.
- A switch between the Quick panel and the Notifications page keeps the blur (no flash).
- Version 1.0.24 (code 134). Compile-checked only; not tested on a real phone.

## Step 24b - the Quick panel ignores the phone's dark / light mode

- The Quick panel, its pop-ups and the Notifications page now use their own look (`PanelLook`) and **never follow the phone's dark / light mode**, on the Home screen and in the "In every app" overlay alike. They stay the dark frosted glass with white text, like the reference phone, whatever the phone is set to.
- Only an explicit **Light** in Settings > Look and feel > Panels makes them light. "Auto" and "Dark" both give the dark glass for these two. The Apps screen, folders, menus and Settings still follow Auto as before.
- Version 1.0.23 (code 133). Compile-checked only; not tested on a real phone.

## Step 24 - Quick panel from the top edge: no flicker, no pop-ups, blurred apps behind

- **No flash of the phone's own panel:** with "In every app" on, a thin invisible bar now sits exactly over the status bar (accessibility overlay). A pull-down that starts there never reaches the phone's panel, so nothing of it ever shows; the launcher's panel opens after about 10 dp of movement. The panel is built and the screen is pictured the moment the finger touches the edge, so it appears at once.
- **No more panel popping up by itself:** the old way (watching for the phone's panel to open) is only a fallback now (Settings > Quick panel > In every app > *Catch swipes at the top edge* off). With the bar on, heads-up notifications, the volume panel etc. can no longer trigger the panel.
- **Blurred apps behind the panel:** a picture of the app you were in is taken (`AccessibilityService.takeScreenshot`, Android 11+), shrunk and blurred in memory and shown behind the panel (it fades in). Android 12+ also blurs the window behind the panel (radius 60). If the phone refuses the picture, only the window blur is used.
- The bar follows the screen: it is removed while the screen is off or locked and comes back when you unlock. The swipe follows the Home setting "Swipe down opens" (Notifications / Quick panel / Split); "Phone's panel" hands over to the phone's own notifications.
- **Trade-offs:** tapping the status bar itself (its icons, "tap to scroll to top") does nothing while the bar is on; in full-screen apps (video, games) a pull-down from the top opens the panel. Switch *Catch swipes at the top edge* off if you do not like that.
- **After updating:** the new "take pictures" permission only reaches the service after it is switched off and on again in Android Settings > Accessibility. Without it the panel still works (window blur only).
- Compile-checked only; not tested on a real phone. Version 1.0.22 (code 132).

## Step 23 - widget fonts, colours and a stacked clock

- **Ten fonts** for the Clock, Mini clock and Date widgets (widget setup screen > Font): Light, Thin, Regular, Medium, Bold, Black, Narrow, Serif, Playful, Script. Each name is shown in its own font. They are Android's built-in font families (no font files). A widget can not change a font while it runs, so each layout holds ten text blocks and shows only the chosen one.
- **Six more colours** (12 in all: + Coral, Violet, Lemon, Aqua, Lime, Orange) for digital clocks and the Date widget. The analog clock keeps its first six (it has one picture per colour).
- **Stacked style** for the clock: hour on one line, minutes under it, big. Style chips are now Digital / Stacked / Analog. 12 / 24 hour applies to both digital styles.
- Widgets saved before this keep their look (the saved text gained a sixth value, the font; the old five-value text still reads). Defaults: clocks Light, Date Regular.
- Layouts `widget_clock.xml` / `widget_date.xml` were written by a small script (ten blocks each); the save format is JVM-tested. Compile-checked only. Version 1.0.21 (code 131).

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

## Step 26a - one-tap buttons, no popup; no slide when Home appears

- **Every Quick panel button now switches with one tap and no popup.** Torch, sound mode (and auto rotate / Do not disturb once allowed) are switched directly.
  Wi-Fi, Bluetooth, mobile data, flight mode, location, hotspot, battery saver, dark mode, cast, NFC and night light are switched by the Quick panel service
  (Accessibility): it opens the phone's own Quick settings for a moment, presses that button by its name and closes it again. The panel stays open and the button updates.
  The service now needs "retrieve window content" (only to find the button by name); switch the service off and on once in Accessibility after installing.
- New buttons: **NFC** and **Night light / Eye comfort** (under "Available buttons" in the editor). Hotspot now shows On / Off.
- The "Open settings" popup is gone. Without the service a tap opens the phone's own screen for that button instead (and says so).
- **Unlock glitch**: the Home window has no opening / closing animation and no "preview" picture any more, so the apps no longer slide up from the bottom (or show twice) when the phone is unlocked.
- Next (26b): swipe left / right through pages of buttons in a loop.
- Compile-checked only by reading; not built or tested on a real phone.

## Step 26b - hidden force-switching, looping button pages, smooth close

- **The phone's own panel is never seen while a button is forced**: a frozen picture of the screen covers it (overlay of the Quick panel service) and the whole press takes a fraction of a second.
- **The Quick panel now holds every button** (NFC, Night light and Camera added once to your list). The round buttons sit on pages of eight: **drag left / right and the pages loop** (after the last comes the first), dots show the page.
  A swipe left anywhere else on the panel (clock, sliders, notifications area) still goes to the Notifications page.
- **Swipe up to close**: the veil now fades on the same clock as the slide and the panel is drawn as one picture while it moves, so no faint shade is left on the Home screen.
- Compile-checked only by reading; not built or tested on a real phone.

## Step 27 - every button of the phone's own Quick settings

- The first time the Quick panel opens (with the Quick panel service on), the launcher reads the phone's own Quick settings behind a picture of the screen,
  pages through all of it and adds every button it has no built-in button for (Speed up, Record screen, Quick Share, vivoshare, Calculator...). They sit at the end of the panel
  and can be moved / removed in the editor. Long-press the pencil to read the phone's buttons again.
- Tapping such a button presses the same button in the phone's Quick settings (found by its name); its On / Off is read from the phone and remembered.
- Wi-Fi and mobile data are also found by their place (the two big buttons on top) for phones that show the network name instead of "Wi-Fi".
- New names: Personal hotspot, Eye Protection. Icons of the extra buttons are a plain grid symbol for now.
- Compile-checked only by reading; not built or tested on a real phone.

## Step 27b - no faint shade over Home when a panel closes

- The solid veil of the Quick panel and the Notifications page no longer fades in or out over the Home screen (a fading solid layer looked like a faint shade); it slides with the panel like one curtain.

## Step 28a - reaching the phone's own buttons by every way, and a report

- Finding a button now has three levels: a strict one (visible, clickable), a relaxed one (also views the phone marks hidden / not important, a clickable view next to the name) and
  a last one that taps the place of the name with a real finger-tap (new permission "perform gestures"). A press the phone ignores as a click is repeated as a finger-tap.
- If the phone does not show its Quick settings, the notification shade is pulled down first and then its Quick settings.
- Nothing stays blocked after an error: a press that never finished counts as over after 9 seconds.
- When a button cannot be found, a **report** of what the launcher can see in the phone's Quick settings is copied to the clipboard (paste it in the chat). Long-press the pencil on the Quick panel: reads the phone's buttons again and copies the report.
- Without the service, a tap opens Accessibility settings instead of a phone screen of that button.
- The report is only written on those two occasions and never saved or sent anywhere by the launcher.
- Compile-checked only by reading; not built or tested on a real phone.

## Step 28b - more ways to reach the phone's buttons

- The phone's panel is now found by window type ("system" windows), not by the app name "systemui": phones that keep their control center in another app are covered.
  Other apps' windows are used only when no system window can be read (so a press never lands inside the app under the panel).
- If the panel still does not show, a real swipe down from the top-right corner is sent (control center on phones that keep it on the right).
- **Optional, one time:** `adb shell pm grant com.oklabs.oneui9launcher android.permission.WRITE_SECURE_SETTINGS` - then Location, Dark mode and Battery saver are switched
  directly (no Accessibility, no flash). If the write does not work the usual way is used. From Termux: `pkg install android-tools`, then Developer options > Wireless debugging > Pair device,
  `adb pair 127.0.0.1:<pair port>`, `adb connect 127.0.0.1:<port>`, and the grant line above.
- Buttons found on the phone show the first letter of their name instead of a grid symbol.
- Compile-checked only by reading; not built or tested on a real phone.

## Step 29 - finding the phone's own buttons on phones whose buttons are not marked "clickable"

- The earlier scan only accepted buttons that the phone marks as clickable; many phones (Vivo, and others) draw their buttons as custom views that are not. The scan now works from the **names**
  inside the buttons area (the sideways pager when the phone marks one, otherwise the middle of the screen below the two big buttons), clickable or not, and presses with a real finger-tap just above the name
  (the icon) when there is nothing to click. A tap is sent only after the button stopped moving (the panel slides open first).
- Pages of buttons are changed with the scroll action first and a real swipe second.
- The big Wi-Fi / mobile-data buttons are found whether clickable or not; the round icon at their left is pressed.
- **Nothing fails silently any more**: when the Quick panel opens the launcher says if the Quick panel service is off, or has no right to read the panel yet (switch off / on once), and after reading it says how many
  phone buttons it found (or what it saw: windows / views / area / names) and copies the report when it found none. Long-press the pencil reads again (up to four automatic tries).
- Compile-checked only by reading; not built or tested on a real phone.

## Step 30a - icons for every kind of Quick panel button (part 1 of 2)

- 26 new icons drawn in code (original line art): screenshot, screen record, speed up / boost, share (Quick Share, vivoshare, Nearby...), QR scan, wallet / pay, eye protection, power saving (leaf), sync, bedtime (moon),
  lock, VPN / privacy (shield), driving (car), game mode, microphone, magnifier, timer, calculator, split screen, small window, USB / reverse charging, headphones, accessibility, auto brightness, stay awake (cup), SIM.
- Every button found in the phone's own Quick settings now gets the icon that fits its **name** (Samsung, Vivo, Xiaomi, Oppo, Realme, OnePlus, Huawei, Pixel names; 300+ keywords, e.g. "Screen record", "Eye comfort",
  "Game Turbo", "Wireless PowerShare"). A name that fits nothing still shows its first letter.
- Part 2 (next): more icons + an icon gallery / own icon per button.
- Icon drawing was checked as a picture on a PC; the rest is compile-checked only. This zip holds only the changed files (copy over the project, like step 29).


## Step 30b - more icons + icon gallery (part 2 of 2)
- 14 new icons (music, heart, star, home, mail, clock, chat, folder, pin, book, bulb, globe, key, bolt) with matching keyword rules.
- **Change icons**: long-press the gear in the Quick panel -> list of your buttons -> tap one -> pick any icon from the grid. "Automatic" gives back the name-based icon. Saved in prefs key `qp_icons`.
- Not built or run on a phone. Only the save/load text logic was run as a JVM test; the rest is syntax-checked only.

## Step 31 - smooth animations everywhere
- New `Motion.kt`: one set of curves/times (open 300 ms soft landing, close 220 ms) used by every panel.
- Quick panel and Notification page: slide + fade 0 -> 100 on open, 100 -> 0 on close (the veil fades too, no hard edge).
- Home screen: fades in 0 -> 100 with a tiny zoom (240 ms) when you come back from an app, unlock or press Home (`res/anim/launcher_fade_in.xml`, theme + `noTransition()`).
- Apps drawer: while dragging, Home fades out and the Apps screen fades in (0 -> 100) with an eased curve.
- Not built or run on a phone. Closing an app is drawn by the phone itself, so only the Home side of that move can be changed.

## Step 32 - swipe works from empty spaces of the Quick panel
- Cause: the swipe (up = close, left = Notifications) was only read for touches that began on a button; a touch on an empty space is taken by the card itself, which never read it.
- Now one `swipeEvent()` (raw positions) is used by the card (both for buttons and empty spaces) and by the dark area beside the panel.
- Not built or run on a phone.

## Step 33 - smoother Quick panel animation with blur, and the double image after unlock removed
- **Quick panel and Notification page, open:** from top to bottom, opacity 0 -> 100 on a soft curve, and the panel **sharpens from a blur** (26 px -> 0) while it lands.
- **Close:** swiping up, the opacity falls through about 30% and lands softly on 0 (no sudden last drop), and the panel **blurs** while it goes (0 -> 26 px). Close takes 240 ms. The blur is on the panel itself (Android 12+, older phones just fade); nothing behind it is blurred, so no picture of an app is shown twice.
- **Double image after unlock removed:** Step 31 had put a fade + zoom into the Home window's system animations; the system drew that next to the app being unlocked / left, so each app showed with a second one under it that then dismissed. The window animations are back to "none" (as in Step 26), and the soft Home appearance (0 -> 100 + tiny zoom) is now drawn **inside** the launcher window (`playHomeFade`), so it can not show an app twice.
- The strip over the status bar (In every app) is now added / removed 0.35-0.7 s after the last change instead of at once, so no window is created in the middle of the unlock animation.
- Changed files only (copy over the project, like step 32): `Motion.kt`, `QuickPanel.kt`, `NotificationPage.kt`, `LauncherActivity.kt`, `SystemPanel.kt`, `res/values/themes.xml`. Version 1.0.38 (code 148). Not built or run on a phone.

## Step 34 - panel closes at once, less blur
- **Swipe up is much faster:** closing takes 130 ms (was 240), starting at full speed and slowing only at the very end.
- **Gone at once:** the moment the swipe starts the opacity drops to about 20% (within the first third of the move) and then lands softly on 0 (`Motion.fadeOut`).
- **Less blur:** 9 px instead of 26 px (about a third), for opening and closing, Quick panel and Notification page.
- Changed files only (copy over the project): `Motion.kt`, `build.gradle.kts`, `STEPS.md`. Version 1.0.39 (code 149). Not built or run on a phone.

## Step 35 - faster panel animation, no double icons after unlock, the Quick panel service keeps running
- **Swipe up (close):** the moment the swipe is accepted the panel's opacity falls straight from 100% to 20%, then runs down to 0 while it slides up. Closing takes 110 ms (was 130). There is **no blur while closing** any more (changing a blur on every frame was a source of glitches). Same for the Notification page.
- **Swipe down (open):** opacity 0 -> 100 fast: 170 ms (was 300), full opacity by 60% of the move. The small blur that sharpens while it lands stays.
- **Double icons when the screen turns on:** after the screen went off, the system still shows its last picture of Home for a moment, and the Home fade-in (with a tiny zoom) drew a second, smaller copy of the icons over it. Now the fade-in only plays when you come back from an app, never after lock / unlock, and it is opacity only (no zoom).
- **"Accessibility keeps switching off":** the phone stops the Quick panel service when the app's process dies (a crash, or a "clean background apps" on Vivo and others) and then leaves it in the list as switched on but never starts it again. (a) Nothing can throw out of the service's event and screen-on/off handlers any more, so it does not take the process down with it. (b) New `ServiceKeeper`: when the launcher is in front and the service is listed as on but not running, it switches it off and on by itself if the launcher was given WRITE_SECURE_SETTINGS (`adb shell pm grant com.oklabs.oneui9launcher android.permission.WRITE_SECURE_SETTINGS`, once); otherwise it says once "The phone stopped the Quick panel service..." so you can switch it on again.
- Vivo: iManager > App manager > Autostart: allow the launcher; Battery > Background power consumption: allow high consumption; lock the launcher in Recents. These stop the phone from killing it.
- Changed files only (copy over the project, like step 34): `Motion.kt`, `QuickPanel.kt`, `NotificationPage.kt`, `LauncherActivity.kt`, `SystemPanel.kt`, new `ServiceKeeper.kt`, `build.gradle.kts`, `STEPS.md`. Version 1.0.40 (code 150). Not built or run on a phone.

## Step 40 (redo) - only the Quick panel from 1.0.44, the rest stays 1.0.40
- `QuickPanel.kt`, `SystemPanel.kt` (Accessibility / fake shade cover, finding and pressing the phone's own buttons) and `res/values/bools_store.xml` are the 1.0.44 versions (touch from everywhere, no stuck panel, colour button, edit look, buttons read every second).
  Everything else is the 1.0.40 project (no AppGallery pack, no release manifest changes).
- Faster and smoother: open 170 -> 130 ms, close 110 -> 80 ms; closing fades from 100% on a smooth curve (no sudden drop to 20%); opening reaches full opacity sooner (50% / 55% of the move).
- Version 1.0.45 (code 155). Not built or run on a phone. No new Quick panel icon set was found in 1.0.41-1.0.44.

## Step 41 (redo) - touch fixes taken from the gesture-navigation zip, keyboard no longer closes the panel
- From the user's newer zip, only the Quick panel touch code: the button pages (`TilePager`) now also swipe sideways from an empty space, and `QuickNotifs.cardsAtRaw` counts only a touch ON a card (the empty part of the list is free for the page swipes).
- Button detection (`SystemPanel` finding / pressing the phone's own buttons) and all icons (`QuickGlyph*`, `QuickIcons`) were already identical in that zip: nothing to copy. Not taken: navigation bar, Recents, Shizuku, ForceAccess, the Shizuku toggles in `QuickSystem`.
- Keyboard: the zip has no fix for it; written here. When a keyboard window came up the service saw "another app came to the front" and hid the panel. `SystemPanel.isKeyboardPackage` (enabled input methods, plus any package with "inputmethod" / "keyboard" in the name) is now ignored there, so the panel stays.
- Version 1.0.46 (code 156). Changed: `QuickPanel.kt`, `QuickNotifs.kt`, `SystemPanel.kt`, `build.gradle.kts`, `STEPS.md`. Not built or run on a phone.

## Step 41c - Notifications page: swipe up from an empty space no longer missed
- `NotificationPage.Column.dispatchTouchEvent` reads the swipe up (close) and swipe right (Quick panel) from every touch on the page in screen coordinates, before any child; children get a cancel when it is taken. The angle is looser (before: up needed 1.5x more up than sideways; now about equal is enough) and the distance a little shorter (1.5 x touch slop).
- Version 1.0.47 (code 157). Changed: `NotificationPage.kt`, `build.gradle.kts`, `STEPS.md`. Not built or run on a phone.

## Step 42 - app name "Glassy launcher" and a new icon
- `app_name` is now **Glassy launcher** (`strings.xml`; the manifest, the launcher label and every message read it from there). The name in `docs/*.html` and `PLAY_STORE.md` changed too.
- New launcher icon (adaptive, drawn in code): aurora gradient (teal - blue - violet) with a soft light corner, a frosted glass pane with a sheen and four glass tiles (one amber). `ic_launcher_bg.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` (themed icon: pane outline + four tiles).
- Store pictures (512 / 216 / 1024 px) are in the separate icon zip. Version 1.0.48 (code 158). Not built or run on a phone.

## Step 43 - the Huawei AppGallery release pack is back
- New `app/src/release/AndroidManifest.xml`: removes `WRITE_SECURE_SETTINGS` (`tools:node="remove"`) so only the **release build type** loses it - a store build can never hold that permission anyway, it was only there for the debug build's "adb grant" shortcut.
- New `app/src/release/res/values/bools_store.xml`: `qs_other_windows` is `false` in the release build type, so the store build never looks at other apps' windows.
- Debug builds (`build.sh`, Android Studio "Debug") are unchanged - `WRITE_SECURE_SETTINGS` and `qs_other_windows = true` still come from `app/src/main`. Only `assembleRelease` / `bundleRelease` picks up this folder.
- This is the same pack from the 1.0.41 AppGallery step, brought back onto the Glassy launcher base (changed files only: `app/src/release/AndroidManifest.xml`, `app/src/release/res/values/bools_store.xml`, `build.gradle.kts`, `STEPS.md`). Version 1.0.49 (code 159). Not built or run on a phone.

## Step 50 - Quick panel over other apps WITHOUT Accessibility (Huawei plan, step 1 of 8)
- New switch Settings > Quick panel > **Quick panel over other apps** (`op_on`): the Quick panel opens over any app with only "Draw over other apps" and a small foreground service (`PanelOverlayService`). Its notification (lowest importance) opens the panel; action Settings opens the launcher settings. Show panel now = test.
- The window is drawn ABOVE apps but BELOW the status bar (Android's rule for this window type), so the status bar itself can not be taken over without Accessibility. Steps 2-7 add the other ways to open it (top handle under the status bar, Quick Settings tile, floating button, smart rules, force / keep alive); step 8 makes the Huawei store build without the Accessibility service.
- With Accessibility off, a button that needs it opens the phone's own screen instead of sending the person to Accessibility settings; no scan toasts then.
- New: `OverlayPanel.kt`, `res/drawable/ic_stat_panel.xml`, `PLAN_NO_ACCESSIBILITY.md` (the tracker: done / left / limits / test list). Changed: `AndroidManifest.xml` (SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE(+_SPECIAL_USE), POST_NOTIFICATIONS, the service), `HomeSettingsActivity.kt`, `LauncherActivity.kt`, `QuickSystem.kt`, `QuickPanel.kt`, `build.gradle.kts`. Version 1.0.50 (code 160). Not built or run on a phone.

## Step 51 - top handle (Huawei plan, step 2 of 8)
- Settings > Quick panel > **Top handle**: a thin strip right under the status bar; a swipe down on it opens the Quick panel over any app (needs the step 50 switch and "Draw over other apps", no Accessibility). Options: On / Off, pill shown or invisible, Middle / Left / Right, width, height, distance below the status bar, vibrate.
- The strip is removed while the panel is open and comes back after; setting changes apply at once (the service listens to the preferences).
- New `TopHandle.kt`; changed `OverlayPanel.kt`, `HomeSettingsActivity.kt`, `build.gradle.kts`, `PLAN_NO_ACCESSIBILITY.md`, `STEPS.md`. Version 1.0.51 (code 161). Syntax-checked with the compiler (only the known API-34 and R errors remain); not run on a phone.

## Step 52 - Quick Settings tile, App shortcut, notification options (Huawei plan, step 3 of 8)
- **Quick Settings tile** "Quick panel" (`PanelTileService`): the person adds it in the phone's own shade (pencil / edit button). Tap = the shade closes and the panel opens over the app. The tile is "active" when the step 50 switch is on and "Draw over other apps" is allowed; otherwise a tap opens the launcher settings and says what is missing.
- **App shortcuts** (long-press the launcher's icon in the phone's own launcher): "Quick panel" and "Notifications" (the panel straight on the Notifications page). `res/xml/shortcuts.xml`, shortcut icon `ic_shortcut_panel.xml`.
- Both start the new invisible `PanelShortcutActivity`, which starts the panel service (an activity on screen may always start it).
- **Notification options** (Settings > Quick panel > "The Quick panel notification"): tap opens the Quick panel or the Notifications page; show / hide the "Settings" button; "Hide the notification" opens the phone's own category screen (the service needs the notification, so the launcher can not remove it itself). Changes apply at once.
- New: `PanelTile.kt` (`PanelNote`, `PanelShortcutActivity`, `PanelTileService`), `res/xml/shortcuts.xml`, `res/values/strings_panel.xml`, `res/drawable/ic_shortcut_panel.xml`. Changed: `AndroidManifest.xml`, `OverlayPanel.kt`, `HomeSettingsActivity.kt`, `build.gradle.kts`, `PLAN_NO_ACCESSIBILITY.md`, `STEPS.md`. Version 1.0.52 (code 162). Not compiled and not run on a phone.

## Step 53 - floating button (Huawei plan, step 4 of 8)
- Settings > Quick panel > **Floating button**: a small round button (the panel's four-tile symbol) over every app. Tap = open the Quick panel; drag = move it; after a drag it glides to the nearest side edge (can be switched off). Options: On / Off, size (28-72 dp), opacity (20-100%), stick to the edge, vibrate, reset position.
- The place is saved as a share of the free screen (so it survives size changes). The button is removed while the panel is open and comes back after. Needs the step 50 switch and "Draw over other apps". Off by default.
- New `FloatingButton.kt` (`FloatButton` settings, `FloatingButtonView`). Changed: `OverlayPanel.kt`, `HomeSettingsActivity.kt`, `build.gradle.kts`, `PLAN_NO_ACCESSIBILITY.md`, `STEPS.md`. Version 1.0.53 (code 163). Not compiled and not run on a phone.

## Step 54 - smart rules (Huawei plan, step 5 of 8)
- Settings > Quick panel > **Smart rules**: five switches that hide the top handle and the floating button on purpose: full-screen apps / videos, keyboard open, lock screen, screen off, landscape ("Show in landscape" off = hidden when the phone is turned sideways). The first four are on by default, landscape is shown by default.
- The rules only touch the two touch windows. The Quick panel, the notification, the tile and the shortcut are never hidden by them.
- How it knows: a 1-pixel-wide invisible overlay window (`SmartProbeView`: no touches, no focus) gets the bar sizes Android gives every window. Status bar not visible = full-screen app; keyboard inset = keyboard open (Android 11+ `isVisible(statusBars / ime)`, older phones use the inset sizes). The service also looks again once a second while the probe exists. The probe exists only when a full-screen or keyboard rule is on AND the handle or the button is on AND the screen is on. Lock screen: `KeyguardManager.isKeyguardLocked`; screen off: `PowerManager.isInteractive`; both follow SCREEN_ON / SCREEN_OFF / USER_PRESENT; landscape follows `onConfigurationChanged` (which also re-places the handle and the button after a turn).
- While the panel is open the probe's answers are ignored (the panel itself changes the bars); when the panel closes the probe is checked at once.
- Honest limit: some phones do not give the keyboard inset to windows of other apps; then the keyboard rule never fires (nothing breaks). Needs a test on the Huawei phone.
- New `SmartRules.kt` (`SmartRules` settings + pure `hidden(...)`, `SmartProbeView`). Changed: `OverlayPanel.kt`, `HomeSettingsActivity.kt`, `build.gradle.kts`, `PLAN_NO_ACCESSIBILITY.md`, `STEPS.md`. No new permission. Version 1.0.54 (code 164). Not compiled and not run on a phone.

