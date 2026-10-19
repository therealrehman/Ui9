package com.oklabs.oneui9launcher

/**
 * What the person chose on the setup screen of one of the launcher's own widgets (Clock, Mini clock, Analog clock, Date).
 * Plain Kotlin (no Android classes), so reading / writing it can be tested on a normal JVM.
 *
 * Saved as one short text ("style,color,hours,date,background") per widget. Anything unreadable falls back to the
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
    /** [BG_NONE], [BG_GLASS] or [BG_DARK]. */
    val background: Int
) {

    fun encode(): String = "$style,$color,$hours,${if (showDate) 1 else 0},$background"

    /** The text colour (ARGB) of this choice. */
    val colorInt: Int get() = COLORS[color.coerceIn(0, COLORS.size - 1)]

    /** Softer version of the colour for the small lines (date, weekday, month). */
    val softColorInt: Int get() = (colorInt and 0x00FFFFFF) or (0xE6 shl 24)

    fun copy(
        style: Int = this.style,
        color: Int = this.color,
        hours: Int = this.hours,
        showDate: Boolean = this.showDate,
        background: Int = this.background
    ): WidgetConfig = WidgetConfig(style, color, hours, showDate, background)

    companion object {
        const val STYLE_DIGITAL = 0
        const val STYLE_ANALOG = 1

        const val HOURS_PHONE = 0
        const val HOURS_12 = 1
        const val HOURS_24 = 2

        const val BG_NONE = 0
        const val BG_GLASS = 1
        const val BG_DARK = 2

        /** White, black, sky, mint, amber, rose (ARGB). The analog clock has one set of pictures per colour, in this order. */
        val COLORS = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFF111111.toInt(),
            0xFF8EC9FF.toInt(),
            0xFF9BE8C2.toInt(),
            0xFFFFC27A.toInt(),
            0xFFFFB3C7.toInt()
        )

        val COLOR_NAMES = listOf("White", "Black", "Sky", "Mint", "Amber", "Rose")

        /** Reads what [encode] wrote; [fallback] for missing or damaged text, and for any single value that is out of range. */
        fun decode(text: String?, fallback: WidgetConfig): WidgetConfig {
            if (text.isNullOrBlank()) return fallback
            val parts = text.trim().split(',')
            if (parts.size != 5) return fallback
            val style = parts[0].toIntOrNull()?.takeIf { it in STYLE_DIGITAL..STYLE_ANALOG } ?: fallback.style
            val color = parts[1].toIntOrNull()?.takeIf { it in COLORS.indices } ?: fallback.color
            val hours = parts[2].toIntOrNull()?.takeIf { it in HOURS_PHONE..HOURS_24 } ?: fallback.hours
            val date = when (parts[3]) {
                "1" -> true
                "0" -> false
                else -> fallback.showDate
            }
            val background = parts[4].toIntOrNull()?.takeIf { it in BG_NONE..BG_DARK } ?: fallback.background
            return WidgetConfig(style, color, hours, date, background)
        }
    }
}

/** Which of the launcher's own widgets this is. [defaults] is what a freshly added one looks like. */
enum class BuiltInKind(val providerClass: String, val title: String, val defaults: WidgetConfig) {
    CLOCK(
        "ClockWidgetProvider", "Clock",
        WidgetConfig(WidgetConfig.STYLE_DIGITAL, 0, WidgetConfig.HOURS_PHONE, true, WidgetConfig.BG_NONE)
    ),
    MINI_CLOCK(
        "ClockSmallWidgetProvider", "Mini clock",
        WidgetConfig(WidgetConfig.STYLE_DIGITAL, 0, WidgetConfig.HOURS_PHONE, true, WidgetConfig.BG_NONE)
    ),
    ANALOG_CLOCK(
        "AnalogClockWidgetProvider", "Analog clock",
        WidgetConfig(WidgetConfig.STYLE_ANALOG, 0, WidgetConfig.HOURS_PHONE, true, WidgetConfig.BG_NONE)
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
