package com.oklabs.oneui9launcher

/**
 * What the person chose on the setup screen of one of the launcher's own widgets (Clock, Mini clock, Analog clock, Date).
 * Plain Kotlin (no Android classes), so reading / writing it can be tested on a normal JVM.
 *
 * Saved as one short text ("style,color,hours,date,background,font"; older widgets have no font and get the default one) per widget. Anything unreadable falls back to the
 * widget's defaults, so a damaged value never breaks a widget.
 */
class WidgetConfig(
    /** [STYLE_DIGITAL] or [STYLE_ANALOG] (clocks only). */
    val style: Int,
    /** Index into [COLORS]. */
    val color: Int,
    /** [HOURS_PHONE], [HOURS_12] or [HOURS_24] (digital clocks only). */
    val hours: Int,
    /** Show the date line under the clock (clocks only). */
    val showDate: Boolean,
    /** [BG_NONE], [BG_GLASS], [BG_DARK], or one of the five gradient glass themes ([BG_AURORA] .. [BG_MIDNIGHT], see [WidgetThemes]). */
    val background: Int,
    /** Step 22: index into [FONT_NAMES] (ten fonts: thin, bold, playful...). */
    val font: Int = FONT_REGULAR
) {

    fun encode(): String = "$style,$color,$hours,${if (showDate) 1 else 0},$background,$font"

    /** The text colour (ARGB) of this choice. */
    val colorInt: Int get() = COLORS[color.coerceIn(0, COLORS.size - 1)]

    /** Softer version of the colour for the small lines (date, weekday, month). */
    val softColorInt: Int get() = (colorInt and 0x00FFFFFF) or (0xE6 shl 24)

    fun copy(
        style: Int = this.style,
        color: Int = this.color,
        hours: Int = this.hours,
        showDate: Boolean = this.showDate,
        background: Int = this.background,
        font: Int = this.font
    ): WidgetConfig = WidgetConfig(style, color, hours, showDate, background, font)

    companion object {
        const val STYLE_DIGITAL = 0
        const val STYLE_ANALOG = 1

        /** Step 22: the hour on one line and the minutes under it, big (digital clocks only). */
        const val STYLE_STACKED = 2

        const val FONT_LIGHT = 0
        const val FONT_REGULAR = 2

        /** The ten fonts, in the order of the text blocks in widget_clock.xml / widget_date.xml. */
        val FONT_NAMES = listOf("Light", "Thin", "Regular", "Medium", "Bold", "Black", "Narrow", "Serif", "Playful", "Script")

        /** Android's own font family names for [FONT_NAMES] (the setup screen shows each name in its font), and whether it is bold. */
        val FONT_FAMILIES = listOf(
            "sans-serif-light", "sans-serif-thin", "sans-serif", "sans-serif-medium", "sans-serif", "sans-serif-black",
            "sans-serif-condensed", "serif", "casual", "cursive"
        )
        val FONT_BOLD = listOf(false, false, false, false, true, false, true, true, false, false)

        /** Colours the analog clock has pictures for (the first six). */
        const val ANALOG_COLORS = 6

        const val HOURS_PHONE = 0
        const val HOURS_12 = 1
        const val HOURS_24 = 2

        const val BG_NONE = 0
        const val BG_GLASS = 1
        const val BG_DARK = 2

        // Step 14: gradient glass themes. Values 0..2 keep their old meaning, so saved widgets look the same as before.
        const val BG_AURORA = 3
        const val BG_SUNSET = 4
        const val BG_OCEAN = 5
        const val BG_FOREST = 6
        const val BG_MIDNIGHT = 7

        /** White, black, sky, mint, amber, rose (ARGB). The analog clock has one set of pictures per colour, in this order. */
        val COLORS = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFF111111.toInt(),
            0xFF8EC9FF.toInt(),
            0xFF9BE8C2.toInt(),
            0xFFFFC27A.toInt(),
            0xFFFFB3C7.toInt(),
            // Step 22: six more (digital clocks and the Date widget only)
            0xFFFF8A80.toInt(),
            0xFFB9A4FF.toInt(),
            0xFFFFE066.toInt(),
            0xFF7FE3E3.toInt(),
            0xFFC6FF8A.toInt(),
            0xFFFF7A3D.toInt()
        )

        val COLOR_NAMES = listOf("White", "Black", "Sky", "Mint", "Amber", "Rose", "Coral", "Violet", "Lemon", "Aqua", "Lime", "Orange")

        /** Reads what [encode] wrote; [fallback] for missing or damaged text, and for any single value that is out of range. */
        fun decode(text: String?, fallback: WidgetConfig): WidgetConfig {
            if (text.isNullOrBlank()) return fallback
            val parts = text.trim().split(',')
            if (parts.size != 5 && parts.size != 6) return fallback
            val style = parts[0].toIntOrNull()?.takeIf { it in STYLE_DIGITAL..STYLE_STACKED } ?: fallback.style
            val color = parts[1].toIntOrNull()?.takeIf { it in COLORS.indices } ?: fallback.color
            val hours = parts[2].toIntOrNull()?.takeIf { it in HOURS_PHONE..HOURS_24 } ?: fallback.hours
            val date = when (parts[3]) {
                "1" -> true
                "0" -> false
                else -> fallback.showDate
            }
            val background = parts[4].toIntOrNull()?.takeIf { it in BG_NONE..BG_MIDNIGHT } ?: fallback.background
            val font = if (parts.size == 6) parts[5].toIntOrNull()?.takeIf { it in FONT_NAMES.indices } ?: fallback.font else fallback.font
            return WidgetConfig(style, color, hours, date, background, font)
        }
    }
}

/**
 * One gradient glass background of the launcher's own widgets (Step 14). [start] and [end] are ARGB colours,
 * top-left to bottom-right; they are see-through on purpose, so the wallpaper glows through like frosted glass.
 * The widget itself uses the drawable res/drawable/widget_theme_<[key]>.xml (written from this table), the setup
 * screen draws its little tiles from the same colours.
 */
class WidgetTheme(val background: Int, val key: String, val name: String, val start: Int, val end: Int)

object WidgetThemes {

    val all: List<WidgetTheme> = listOf(
        WidgetTheme(WidgetConfig.BG_AURORA, "aurora", "Aurora", 0xB31DD3B8.toInt(), 0xB37B61FF.toInt()),
        WidgetTheme(WidgetConfig.BG_SUNSET, "sunset", "Sunset", 0xB3FF9A4D.toInt(), 0xB3FF4D8D.toInt()),
        WidgetTheme(WidgetConfig.BG_OCEAN, "ocean", "Ocean", 0xB32F8CFF.toInt(), 0xB316D3E6.toInt()),
        WidgetTheme(WidgetConfig.BG_FOREST, "forest", "Forest", 0xB33CC27A.toInt(), 0xB30E6B6B.toInt()),
        WidgetTheme(WidgetConfig.BG_MIDNIGHT, "midnight", "Midnight", 0xCC24294F.toInt(), 0xCC5B2C83.toInt())
    )

    /** The theme behind a [WidgetConfig.background] value, or null for None / Glass / Dark. */
    fun forBackground(background: Int): WidgetTheme? = all.firstOrNull { it.background == background }
}

/** Which of the launcher's own widgets this is. [defaults] is what a freshly added one looks like. */
enum class BuiltInKind(val providerClass: String, val title: String, val defaults: WidgetConfig) {
    CLOCK(
        "ClockWidgetProvider", "Clock",
        WidgetConfig(WidgetConfig.STYLE_DIGITAL, 0, WidgetConfig.HOURS_PHONE, true, WidgetConfig.BG_NONE, WidgetConfig.FONT_LIGHT)
    ),
    MINI_CLOCK(
        "ClockSmallWidgetProvider", "Mini clock",
        WidgetConfig(WidgetConfig.STYLE_DIGITAL, 0, WidgetConfig.HOURS_PHONE, true, WidgetConfig.BG_NONE, WidgetConfig.FONT_LIGHT)
    ),
    ANALOG_CLOCK(
        "AnalogClockWidgetProvider", "Analog clock",
        WidgetConfig(WidgetConfig.STYLE_ANALOG, 0, WidgetConfig.HOURS_PHONE, true, WidgetConfig.BG_NONE, WidgetConfig.FONT_LIGHT)
    ),
    DATE(
        "DateWidgetProvider", "Date",
        WidgetConfig(WidgetConfig.STYLE_DIGITAL, 0, WidgetConfig.HOURS_PHONE, true, WidgetConfig.BG_GLASS)
    );

    /** Clock widgets can be digital or analog, show / hide the date line. The Date widget only has colour and background. */
    val isClock: Boolean get() = this != DATE

    companion object {
        /** The widget whose provider class is [className] (simple or full name), or null when it is not one of ours. */
        fun fromProvider(className: String?): BuiltInKind? {
            if (className == null) return null
            val simple = className.substringAfterLast('.')
            return values().firstOrNull { it.providerClass == simple }
        }
    }
}
