package com.oklabs.oneui9launcher

import java.text.Collator
import java.text.Normalizer

/** Text helpers for sorting and searching app names. No Android classes in here. */
object AppText {

    private val accents = Regex("\\p{M}+")

    /** Lower-case text without accents, so "Caf\u00e9" is found by typing "cafe". */
    fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(accents, "").lowercase()

    /** 'A'..'Z' for the first letter of [label] (accents ignored), '#' for everything else. */
    fun letterOf(label: String): Char {
        val first = label.firstOrNull { !it.isWhitespace() } ?: return '#'
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull() ?: first
        val upper = base.uppercaseChar()
        return if (upper in 'A'..'Z') upper else '#'
    }

    /** A..Z first, then the '#' group. */
    fun groupOrder(letter: Char): Int = if (letter == '#') 1000 else letter - 'A'

    /** Locale-aware comparison that ignores case and accents. Created per sort so a language change is picked up. */
    fun collator(): Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
}
