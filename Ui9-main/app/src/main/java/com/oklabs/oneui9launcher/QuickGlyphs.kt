package com.oklabs.oneui9launcher

/**
 * Step 30a: picks the icon for a Quick panel button of the phone by its NAME (Samsung, Vivo, Xiaomi, Oppo, Realme, OnePlus, Huawei, Pixel...).
 * Plain Kotlin (no Android classes), so it can be tested on a normal JVM. A name that matches nothing gets [QuickGlyph.GRID] (its first letter is drawn).
 *
 * A keyword matches at the START of a word of the name ("pay" matches "Samsung Pay" but not "Display"). The first rule that matches wins,
 * so the specific rules come before the general ones.
 */
object QuickGlyphs {

    private class Rule(val glyph: Int, vararg val words: String)

    private val rules: List<Rule> = listOf(
        // things with their own special names first
        Rule(QuickGlyphArt.SCREENSHOT, "screenshot", "screen shot", "screen capture", "capture", "scrn shot"),
        Rule(QuickGlyphArt.RECORD, "record", "recorder", "screen rec"),
        Rule(QuickGlyph.HOTSPOT, "hotspot", "hot spot", "tethering", "portable", "personal hotspot"),
        Rule(QuickGlyph.CAST, "cast", "smart view", "smartview", "mirror", "miracast", "screen share", "wireless display", "projection", "multi screen", "multiscreen"),
        Rule(QuickGlyphArt.GAME, "game", "gaming", "turbo", "gamer"),
        Rule(QuickGlyphArt.SPEED, "speed", "boost", "clean", "accelerat", "optimi", "memory", "phone manager"),
        Rule(QuickGlyphArt.SHARE, "vivoshare", "vivo share", "quick share", "quickshare", "nearby", "near share", "airdrop", "oppo share", "o share", "mi share", "share", "send"),
        Rule(QuickGlyphArt.QR, "qr", "scan", "scanner"),
        Rule(QuickGlyphArt.WALLET, "wallet", "pay", "card", "payment", "nfc pay"),
        Rule(QuickGlyphArt.EYE, "eye", "comfort", "blue light", "bluelight", "reading", "reader", "paper", "eye care", "protect"),
        Rule(QuickGlyph.BATTERY, "battery"),
        Rule(QuickGlyphArt.ECO, "power sav", "ultra", "super power", "low power", "eco", "power mode", "performance", "extreme"),
        Rule(QuickGlyphArt.SYNC, "sync", "auto sync", "backup", "refresh"),
        Rule(QuickGlyphArt.MOON, "bedtime", "bed time", "sleep", "wind down", "winddown", "night mode"),
        Rule(QuickGlyph.NIGHT, "night", "night light", "night shield", "dim"),
        Rule(QuickGlyph.DARK, "dark", "theme", "dark mode"),
        Rule(QuickGlyph.DND, "dnd", "disturb", "focus", "priority", "do not"),
        Rule(QuickGlyph.MUTE, "mute", "silent", "silence"),
        Rule(QuickGlyph.VIBRATE, "vibrat", "haptic"),
        Rule(QuickGlyph.SOUND, "sound", "ring", "volume", "audio", "dolby", "hi fi", "hifi", "equalizer", "music"),
        Rule(QuickGlyphArt.LOCK, "lock", "lockdown", "screen lock", "fingerprint", "face unlock", "biometric"),
        Rule(QuickGlyphArt.COFFEE, "stay awake", "awake", "caffeine", "keep screen", "screen timeout", "timeout", "always on", "aod", "ambient"),
        Rule(QuickGlyphArt.SHIELD, "vpn", "privacy", "private", "secure", "security", "safe", "shield", "find my", "find device"),
        Rule(QuickGlyphArt.CAR, "driv", "car", "vehicle", "travel", "traffic"),
        Rule(QuickGlyphArt.MIC, "mic", "microphone", "voice", "assistant", "bixby", "google assistant"),
        Rule(QuickGlyphArt.MAGNIFIER, "magnif", "zoom", "search"),
        Rule(QuickGlyph.ALARM, "alarm"),
        Rule(QuickGlyphArt.TIMER, "timer", "stopwatch", "countdown", "clock"),
        Rule(QuickGlyphArt.CALC, "calc", "calculator"),
        Rule(QuickGlyphArt.SPLIT, "split", "multi window", "multiwindow", "dual window"),
        Rule(QuickGlyphArt.FLOAT, "float", "small window", "pop up", "popup", "pip", "freeform", "free form", "mini window"),
        Rule(QuickGlyphArt.USB, "powershare", "power share", "reverse charg", "wireless power", "usb", "otg", "charging", "file transfer"),
        Rule(QuickGlyphArt.HEADPHONES, "headphone", "headset", "earphone", "earbud", "buds", "hearing"),
        Rule(QuickGlyphArt.ACCESS, "accessib", "talkback", "assistive", "voice access", "one hand", "onehand", "touch assist", "smart touch"),
        Rule(QuickGlyphArt.AUTO_BRIGHT, "auto brightness", "adaptive brightness", "autobrightness"),
        Rule(QuickGlyph.SUN, "brightness", "bright", "sun"),
        // network
        Rule(QuickGlyph.AIRPLANE, "airplane", "aeroplane", "flight", "aircraft", "plane"),
        Rule(QuickGlyph.WIFI, "wifi", "wi fi", "wlan", "wireless", "internet"),
        Rule(QuickGlyph.BLUETOOTH, "bluetooth", "blue tooth", "bt"),
        Rule(QuickGlyph.DATA, "data", "cellular", "mobile network", "5g", "4g", "3g", "lte", "network"),
        Rule(QuickGlyphArt.SIM, "sim", "dual", "roaming", "carrier"),
        Rule(QuickGlyph.NFC, "nfc", "contactless"),
        Rule(QuickGlyph.TORCH, "torch", "flashlight", "flash", "lamp"),
        Rule(QuickGlyph.ROTATE, "rotat", "portrait", "landscape", "orientation"),
        Rule(QuickGlyph.LOCATION, "location", "gps", "position", "maps"),
        Rule(QuickGlyph.CAMERA, "camera", "selfie"),
        Rule(QuickGlyph.BELL, "notification", "notif", "bell", "alert", "reminder"),
        Rule(QuickGlyph.POWER, "power", "restart", "shut", "reboot"),
        Rule(QuickGlyph.GEAR, "setting", "settings", "gear", "config"),
        Rule(QuickGlyphArt.MUSIC, "spotify", "song", "playlist", "radio"),
        Rule(QuickGlyphArt.HEART, "heart", "health", "fitness", "like"),
        Rule(QuickGlyphArt.STAR, "starred", "favorite", "favourite", "bookmark"),
        Rule(QuickGlyphArt.HOME, "home", "house", "smart things", "smartthings"),
        Rule(QuickGlyphArt.MAIL, "mail", "gmail", "inbox", "message"),
        Rule(QuickGlyphArt.CHAT, "chat", "sms", "whatsapp", "messenger"),
        Rule(QuickGlyphArt.FOLDER, "folder", "files", "my files", "storage", "download"),
        Rule(QuickGlyphArt.BOOK, "book", "read", "news", "notes"),
        Rule(QuickGlyphArt.BULB, "bulb", "idea", "tips", "light"),
        Rule(QuickGlyphArt.GLOBE, "globe", "browser", "web", "language", "translate"),
        Rule(QuickGlyphArt.KEY, "key", "password", "pass"),
        Rule(QuickGlyphArt.BOLT, "bolt", "fast", "turbo", "performance", "boost", "saver", "power saving")
    )

    /** Lower case, every other character becomes a space ("Wi-Fi" -> "wi fi"). */
    fun norm(text: String): String = text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").trim().replace(Regex(" +"), " ")

    /** The icon kind for a phone's button called [label]; [QuickGlyph.GRID] when nothing fits. */
    fun forLabel(label: String): Int {
        val n = " " + norm(label) + " "
        if (n.isBlank()) return QuickGlyph.GRID
        for (r in rules) for (w in r.words) if (n.contains(" $w")) return r.glyph
        return QuickGlyph.GRID
    }
}
