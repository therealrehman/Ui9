package com.oklabs.oneui9launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Step 9a: Home settings. Opened from the long-press menu of the Home screen.
 *  - Home screen grid: 4 x 5, 4 x 6, 5 x 5 or 5 x 6 icons per page.
 *  - Icon size on the home pages: Small / Medium / Large.
 *  - Hide apps: pick the apps that disappear from the Home screen and the Apps screen (switch them off again to get them back).
 *  - Step 9b: Notification badges (off / dot / number), Edge panel (on / off + the apps in it) and Landscape (portrait only / allowed).
 * Every choice is saved at once; the launcher applies them when you go back to it (it restarts its screen once).
 */
class HomeSettingsActivity : Activity() {

    private lateinit var holder: FrameLayout
    private var onHiddenPage = false
    private val hidden = HashSet<String>()
    private val edgeApps = HashSet<String>()

    // The app list page is shared by "Hide apps" and "Edge panel apps": which set it edits and where it saves it.
    private var pickerSet: HashSet<String> = hidden
    private var pickerMax = Int.MAX_VALUE
    private var pickerSave: (Set<String>) -> Unit = {}
    private var appList: List<AppEntry>? = null
    private var adapter: HiddenAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Look.load(this)
        hidden.addAll(HomeStore.savedHidden(this))
        edgeApps.addAll(HomeStore.savedEdgeApps(this))

        window.setBackgroundDrawable(ColorDrawable(Look.panel(255)))
        setDarkBarIcons(!Look.dark)
        holder = FrameLayout(this)
        setContentView(holder, ViewGroup.LayoutParams(MATCH, MATCH))
        showMain()

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { goBack() }
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goBack()
    }

    private fun goBack() {
        if (onHiddenPage) showMain() else finish()
    }

    // ------------------------------------------------------------------ main page

    private fun showMain() {
        onHiddenPage = false
        holder.removeAllViews()
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(16), dp(20), dp(28))
        scroll.addView(box, FrameLayout.LayoutParams(MATCH, WRAP))

        box.addView(title("Home settings"))

        box.addView(section("Home screen grid"))
        val gridNames = HomeStore.GRIDS.map { "${it.first} x ${it.second}" }
        val currentGrid = HomeStore.GRIDS.indexOfFirst {
            it.first == HomeStore.savedCols(this) && it.second == HomeStore.savedRows(this)
        }.coerceAtLeast(1)
        box.addView(chips(gridNames, currentGrid) { index ->
            HomeStore.setGrid(this, HomeStore.GRIDS[index].first, HomeStore.GRIDS[index].second)
        })
        box.addView(note("Columns x rows of icons on every page. Icons and widgets that no longer fit move to free spots."))

        box.addView(section("Icon size"))
        box.addView(chips(listOf("Small", "Medium", "Large"), HomeStore.savedIconSize(this)) { index ->
            HomeStore.setIconSize(this, index)
        })

        box.addView(section("Apps"))
        val count = hidden.size
        box.addView(
            row(
                "Hide apps",
                if (count == 0) "No app is hidden" else if (count == 1) "1 app is hidden" else "$count apps are hidden"
            ) {
                showPicker(
                    "Hide apps",
                    "Hidden apps disappear from the Home screen and the Apps screen. Switch an app off to get it back.",
                    hidden, Int.MAX_VALUE
                ) { HomeStore.setHidden(this, it) }
            }
        )

        box.addView(section("Notification badges"))
        box.addView(chips(listOf("Off", "Dot", "Number"), HomeStore.savedBadgeMode(this)) { index ->
            HomeStore.setBadgeMode(this, index)
            if (index != HomeStore.BADGE_OFF && !Badges.accessGranted(this)) {
                // Step 11 (Play Store): say clearly what the access is for before the phone's own screen opens.
                AlertDialog.Builder(this)
                    .setTitle("Notification access")
                    .setMessage(
                        "To show badges, " + getString(R.string.app_name) + " needs Notification access. " +
                            "It only counts the notifications of each app on this phone. It never reads, stores or sends " +
                            "their content, and it has no internet access. Nothing is shown until you allow it. " +
                            "You can turn it off at any time in the phone's settings."
                    )
                    .setPositiveButton("Continue") { _, _ -> Badges.openAccessSettings(this) }
                    .setNegativeButton("Not now", null)
                    .show()
            }
        })
        box.addView(note("Shows a red dot or the number of notifications on an app's icon. Needs notification access in the phone's settings. The launcher only counts notifications; it never reads or stores them."))

        box.addView(section("Edge panel"))
        box.addView(chips(listOf("Off", "On"), if (HomeStore.savedEdgeOn(this)) 1 else 0) { index ->
            HomeStore.setEdgeOn(this, index == 1)
        })
        val edgeCount = edgeApps.size
        val edgeRow = row(
            "Edge panel apps",
            if (edgeCount == 0) "No app chosen" else if (edgeCount == 1) "1 app" else "$edgeCount apps"
        ) {
            showPicker(
                "Edge panel apps",
                "Pick up to ${HomeStore.EDGE_MAX_APPS} apps for the panel that slides in from the right edge.",
                edgeApps, HomeStore.EDGE_MAX_APPS
            ) { HomeStore.setEdgeApps(this, it) }
        }
        val edgeLp = LinearLayout.LayoutParams(MATCH, WRAP)
        edgeLp.topMargin = dp(10)
        box.addView(edgeRow, edgeLp)
        box.addView(note("A thin handle appears on the right edge of the Home screen. Tap it or drag it to the left."))

        box.addView(section("Screen turning"))
        box.addView(chips(listOf("Portrait only", "Allow landscape"), if (HomeStore.savedLandscape(this)) 1 else 0) { index ->
            HomeStore.setLandscape(this, index == 1)
        })
        box.addView(note("In landscape the icons lose their labels so they fit the shorter page."))

        box.addView(section("Backup and restore"))
        box.addView(row("Back up layout and settings", "Saves a file; you choose where") { startBackup() })
        val restoreRow = row("Restore from a backup", "Replaces your layout and settings") { startRestore() }
        val restoreLp = LinearLayout.LayoutParams(MATCH, WRAP)
        restoreLp.topMargin = dp(10)
        box.addView(restoreRow, restoreLp)
        box.addView(note("A backup holds the Home layout, folders, dock and all settings. Widgets are not included: add them again after a restore."))

        box.addView(section("About"))
        box.addView(row(getString(R.string.app_name), "Version " + versionText()) { showAbout() })
        val linkLp = LinearLayout.LayoutParams(MATCH, WRAP)
        linkLp.topMargin = dp(10)
        box.addView(row("Privacy policy", "Opens in your browser") { openLink(R.string.privacy_url) }, linkLp)
        val termsLp = LinearLayout.LayoutParams(MATCH, WRAP)
        termsLp.topMargin = dp(10)
        box.addView(row("Terms of use", "Opens in your browser") { openLink(R.string.terms_url) }, termsLp)

        box.addView(note("Changes apply when you go back to the Home screen."))

        holder.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ------------------------------------------------------------------ about (Step 10c)

    @Suppress("DEPRECATION")
    private fun versionText(): String = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        (info.versionName ?: "?") + " (" + info.versionCode + ")"
    } catch (e: Exception) {
        "?"
    }

    // Step 11 (Play Store): the policy and terms pages live on the web; the addresses are in strings.xml.
    private fun openLink(urlRes: Int) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(urlRes))))
        } catch (e: Exception) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAbout() {
        onHiddenPage = true
        holder.removeAllViews()
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.gravity = Gravity.CENTER_HORIZONTAL
        page.setPadding(dp(28), dp(72), dp(28), dp(28))

        val icon = ImageView(this)
        icon.setImageDrawable(applicationInfo.loadIcon(packageManager))
        page.addView(icon, LinearLayout.LayoutParams(dp(96), dp(96)))

        val name = TextView(this)
        name.text = getString(R.string.app_name)
        name.setTextColor(Look.text)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        name.gravity = Gravity.CENTER
        val nameLp = LinearLayout.LayoutParams(WRAP, WRAP)
        nameLp.topMargin = dp(18)
        page.addView(name, nameLp)

        val version = TextView(this)
        version.text = "Version " + versionText()
        version.setTextColor(Look.textA(160))
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        page.addView(version, LinearLayout.LayoutParams(WRAP, WRAP))

        val about = TextView(this)
        about.text = getString(R.string.app_tagline) + "\n\n" + getString(R.string.app_credits)
        about.setTextColor(Look.textA(200))
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        about.gravity = Gravity.CENTER
        val aboutLp = LinearLayout.LayoutParams(WRAP, WRAP)
        aboutLp.topMargin = dp(26)
        page.addView(about, aboutLp)

        holder.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ------------------------------------------------------------------ backup and restore (Step 10b)

    private fun startBackup() {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, "OneUI9Launcher-backup-$stamp.txt")
        try {
            startActivityForResult(intent, REQ_BACKUP)
        } catch (e: Exception) {
            Toast.makeText(this, "This phone has no file picker", Toast.LENGTH_LONG).show()
        }
    }

    private fun startRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        try {
            startActivityForResult(intent, REQ_RESTORE)
        } catch (e: Exception) {
            Toast.makeText(this, "This phone has no file picker", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_BACKUP -> {
                val saved = try {
                    contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(this).toByteArray(Charsets.UTF_8)) } != null
                } catch (e: Exception) {
                    false
                }
                Toast.makeText(this, if (saved) "Backup saved" else "Could not save the backup", Toast.LENGTH_LONG).show()
            }
            REQ_RESTORE -> {
                val text = try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.size <= MAX_BACKUP_BYTES) String(bytes, Charsets.UTF_8) else null
                } catch (e: Exception) {
                    null
                }
                if (text == null || !Backup.isBackup(text)) {
                    Toast.makeText(this, "This is not a " + getString(R.string.app_name) + " backup", Toast.LENGTH_LONG).show()
                    return
                }
                AlertDialog.Builder(this)
                    .setTitle("Restore this backup?")
                    .setMessage("Your Home layout and settings are replaced. Widgets are not part of a backup, add them again afterwards.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore") { _, _ -> finishRestore(text) }
                    .show()
            }
        }
    }

    private fun finishRestore(text: String) {
        if (Backup.restore(this, text) < 0) {
            Toast.makeText(this, "Could not restore", Toast.LENGTH_LONG).show()
            return
        }
        hidden.clear()
        hidden.addAll(HomeStore.savedHidden(this))
        edgeApps.clear()
        edgeApps.addAll(HomeStore.savedEdgeApps(this))
        showMain()
        Toast.makeText(this, "Restored. Go back to the Home screen; add your widgets again.", Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------------------ hide apps

    private fun showPicker(titleText: String, noteText: String, set: HashSet<String>, max: Int, save: (Set<String>) -> Unit) {
        pickerSet = set
        pickerMax = max
        pickerSave = save
        onHiddenPage = true
        holder.removeAllViews()
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(20), dp(16), dp(20), 0)

        page.addView(title(titleText))
        page.addView(note(noteText))

        val list = ListView(this)
        list.divider = null
        list.isVerticalScrollBarEnabled = false
        val listAdapter = HiddenAdapter()
        adapter = listAdapter
        list.adapter = listAdapter
        val lp = LinearLayout.LayoutParams(MATCH, 0, 1f)
        lp.topMargin = dp(8)
        page.addView(list, lp)
        holder.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))

        val loaded = appList
        if (loaded != null) {
            listAdapter.set(loaded)
        } else {
            AppRepository.load(this, dp(40)) { found ->
                if (isDestroyed) return@load
                val sorted = AppRepository.sort(found, SortMode.AZ)
                appList = sorted
                adapter?.set(sorted)
            }
        }
    }

    private inner class HiddenAdapter : BaseAdapter() {
        private var items: List<AppEntry> = emptyList()

        fun set(list: List<AppEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val app = items[position]
            val row: LinearLayout
            if (convertView is LinearLayout) {
                row = convertView
            } else {
                row = LinearLayout(this@HomeSettingsActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(7), 0, dp(7))
                val icon = ImageView(this@HomeSettingsActivity)
                row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
                val name = TextView(this@HomeSettingsActivity)
                name.setTextColor(Look.text)
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                name.maxLines = 1
                name.ellipsize = android.text.TextUtils.TruncateAt.END
                val nameLp = LinearLayout.LayoutParams(0, WRAP, 1f)
                nameLp.leftMargin = dp(14)
                nameLp.rightMargin = dp(10)
                row.addView(name, nameLp)
                val toggle = Switch(this@HomeSettingsActivity)
                toggle.isClickable = false // the whole row is the button
                row.addView(toggle, LinearLayout.LayoutParams(WRAP, WRAP))
            }
            (row.getChildAt(0) as ImageView).setImageBitmap(app.icon)
            (row.getChildAt(1) as TextView).text = app.label
            val toggle = row.getChildAt(2) as Switch
            val key = app.component.flattenToString()
            toggle.isChecked = pickerSet.contains(key)
            row.setOnClickListener {
                if (!pickerSet.remove(key)) {
                    if (pickerSet.size >= pickerMax) {
                        Toast.makeText(this@HomeSettingsActivity, "Up to $pickerMax apps", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    pickerSet.add(key)
                }
                toggle.isChecked = pickerSet.contains(key)
                pickerSave(pickerSet)
            }
            return row
        }
    }

    // ------------------------------------------------------------------ small building blocks

    private fun title(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.setTextColor(Look.text)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        view.setPadding(0, dp(8), 0, dp(8))
        return view
    }

    private fun section(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.setTextColor(Look.textA(170))
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        view.setPadding(dp(2), dp(22), 0, dp(8))
        return view
    }

    private fun note(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.setTextColor(Look.textA(140))
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        view.setPadding(dp(2), dp(8), dp(2), 0)
        return view
    }

    /** A line of choices; the picked one is filled. [onPick] gets the index and the choice is shown at once. */
    private fun chips(names: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        val views = ArrayList<TextView>()
        fun paint(current: Int) {
            for ((i, view) in views.withIndex()) {
                val on = i == current
                view.setTextColor(if (on) Look.panel(255) else Look.text)
                view.background = roundRect(if (on) Look.text else Look.inkA(32), dpf(22f))
            }
        }
        for ((i, name) in names.withIndex()) {
            val view = TextView(this)
            view.text = name
            view.gravity = Gravity.CENTER
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            view.setPadding(dp(6), dp(12), dp(6), dp(12))
            view.isClickable = true
            view.setOnClickListener {
                paint(i)
                onPick(i)
            }
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            if (i > 0) lp.leftMargin = dp(8)
            line.addView(view, lp)
            views.add(view)
        }
        paint(selected)
        return line
    }

    /** A tappable line with a title, a small second line and an arrow. */
    private fun row(title: String, subtitle: String, onTap: () -> Unit): View {
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.setPadding(dp(18), dp(14), dp(18), dp(14))
        line.background = roundRect(Look.inkA(24), dpf(20f))
        line.isClickable = true
        line.setOnClickListener { onTap() }

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val main = TextView(this)
        main.text = title
        main.setTextColor(Look.text)
        main.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        texts.addView(main, LinearLayout.LayoutParams(WRAP, WRAP))
        val sub = TextView(this)
        sub.text = subtitle
        sub.setTextColor(Look.textA(150))
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        texts.addView(sub, LinearLayout.LayoutParams(WRAP, WRAP))
        line.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))

        val arrow = TextView(this)
        arrow.text = "\u203A"
        arrow.setTextColor(Look.textA(150))
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        line.addView(arrow, LinearLayout.LayoutParams(WRAP, WRAP))
        return line
    }

    /** Dark (true) or light (false) icons in the status and navigation bar. */
    @Suppress("DEPRECATION")
    private fun setDarkBarIcons(dark: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) mask else 0, mask)
        } else {
            var flags = window.decorView.systemUiVisibility
            val wanted = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            flags = if (dark) flags or wanted else flags and wanted.inv()
            window.decorView.systemUiVisibility = flags
        }
    }

    private companion object {
        const val REQ_BACKUP = 2001
        const val REQ_RESTORE = 2002
        const val MAX_BACKUP_BYTES = 2 * 1024 * 1024
    }
}
