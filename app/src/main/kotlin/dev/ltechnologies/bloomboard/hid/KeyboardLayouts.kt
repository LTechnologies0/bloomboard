package dev.ltechnologies.bloomboard.hid

import java.util.Locale

enum class LayoutId(val storageKey: String, val title: String) {
    QWERTY("qwerty", "QWERTY"),
    AZERTY("azerty", "AZERTY"),
    QWERTZ("qwertz", "QWERTZ"),
    COLEMAK("colemak", "Colemak"),
    DVORAK("dvorak", "Dvorak"),
    BEPO("bepo", "BÉPO"),
    ;

    companion object {
        fun fromKey(key: String?): LayoutId = entries.firstOrNull { it.storageKey == key } ?: AZERTY
    }
}

data class Glyph(val base: String, val shifted: String)

data class ResolvedUsage(val usage: Int, val shift: Boolean)

sealed interface KeyAction {
    data class Usage(val code: Int) : KeyAction
    data object Backspace : KeyAction
    data object Enter : KeyAction
    data object Tab : KeyAction
    data object Escape : KeyAction
    data object Shift : KeyAction
    data object Caps : KeyAction
    data object Space : KeyAction
    data object Emoji : KeyAction
    data class Latch(val bit: Int, val label: String) : KeyAction
    data class Arrow(val code: Int, val label: String) : KeyAction
}

data class KeyDef(val action: KeyAction, val weight: Float = 1f)

fun glyphs(layout: LayoutId): Map<Int, Glyph> = when (layout) {
    LayoutId.QWERTY -> qwerty()
    LayoutId.AZERTY -> azerty()
    LayoutId.QWERTZ -> qwertz()
    LayoutId.COLEMAK -> colemak()
    LayoutId.DVORAK -> dvorak()
    LayoutId.BEPO -> bepo()
}

fun glyphText(layout: LayoutId, usage: Int, shifted: Boolean): String {
    val glyph = glyphs(layout)[usage] ?: return ""
    return if (shifted) glyph.shifted else glyph.base
}

/** Finds the HID usage whose label is [symbol] on [layout]. */
fun usageForLabel(layout: LayoutId, symbol: String): ResolvedUsage? {
    val map = glyphs(layout)
    map.entries.firstOrNull { it.value.base.equals(symbol, ignoreCase = true) }?.let {
        return ResolvedUsage(it.key, shift = false)
    }
    map.entries.firstOrNull { it.value.shifted.equals(symbol, ignoreCase = true) }?.let {
        return ResolvedUsage(it.key, shift = true)
    }
    return null
}

fun keyboardRows(): List<List<KeyDef>> {
    val u = HidUsage
    return listOf(
        listOf(
            KeyDef(KeyAction.Escape, 1.15f),
            KeyDef(KeyAction.Tab, 1.15f),
            KeyDef(KeyAction.Latch(HidMod.LEFT_CTRL, "Ctrl"), 1.25f),
            KeyDef(KeyAction.Latch(HidMod.LEFT_ALT, "Alt"), 1.1f),
            KeyDef(KeyAction.Latch(HidMod.RIGHT_ALT, "AltGr"), 1.35f),
            KeyDef(KeyAction.Latch(HidMod.LEFT_META, "⌘"), 1f),
            KeyDef(KeyAction.Arrow(u.LEFT, "←")),
            KeyDef(KeyAction.Arrow(u.DOWN, "↓")),
            KeyDef(KeyAction.Arrow(u.UP, "↑")),
            KeyDef(KeyAction.Arrow(u.RIGHT, "→")),
        ),
        listOf(
            KeyDef(KeyAction.Usage(u.GRAVE)),
            KeyDef(KeyAction.Usage(u.N1)),
            KeyDef(KeyAction.Usage(u.N2)),
            KeyDef(KeyAction.Usage(u.N3)),
            KeyDef(KeyAction.Usage(u.N4)),
            KeyDef(KeyAction.Usage(u.N5)),
            KeyDef(KeyAction.Usage(u.N6)),
            KeyDef(KeyAction.Usage(u.N7)),
            KeyDef(KeyAction.Usage(u.N8)),
            KeyDef(KeyAction.Usage(u.N9)),
            KeyDef(KeyAction.Usage(u.N0)),
            KeyDef(KeyAction.Usage(u.MINUS)),
            KeyDef(KeyAction.Usage(u.EQUAL)),
            KeyDef(KeyAction.Backspace, 1.45f),
        ),
        listOf(
            KeyDef(KeyAction.Usage(u.Q)),
            KeyDef(KeyAction.Usage(u.W)),
            KeyDef(KeyAction.Usage(u.E)),
            KeyDef(KeyAction.Usage(u.R)),
            KeyDef(KeyAction.Usage(u.T)),
            KeyDef(KeyAction.Usage(u.Y)),
            KeyDef(KeyAction.Usage(u.U)),
            KeyDef(KeyAction.Usage(u.I)),
            KeyDef(KeyAction.Usage(u.O)),
            KeyDef(KeyAction.Usage(u.P)),
            KeyDef(KeyAction.Usage(u.LEFT_BRACE)),
            KeyDef(KeyAction.Usage(u.RIGHT_BRACE)),
            KeyDef(KeyAction.Usage(u.BACKSLASH), 1.35f),
        ),
        listOf(
            KeyDef(KeyAction.Caps, 1.55f),
            KeyDef(KeyAction.Usage(u.A)),
            KeyDef(KeyAction.Usage(u.S)),
            KeyDef(KeyAction.Usage(u.D)),
            KeyDef(KeyAction.Usage(u.F)),
            KeyDef(KeyAction.Usage(u.G)),
            KeyDef(KeyAction.Usage(u.H)),
            KeyDef(KeyAction.Usage(u.J)),
            KeyDef(KeyAction.Usage(u.K)),
            KeyDef(KeyAction.Usage(u.L)),
            KeyDef(KeyAction.Usage(u.SEMICOLON)),
            KeyDef(KeyAction.Usage(u.APOSTROPHE)),
        ),
        listOf(
            KeyDef(KeyAction.Shift, 1.45f),
            KeyDef(KeyAction.Usage(u.NON_US), 0.9f),
            KeyDef(KeyAction.Usage(u.Z)),
            KeyDef(KeyAction.Usage(u.X)),
            KeyDef(KeyAction.Usage(u.C)),
            KeyDef(KeyAction.Usage(u.V)),
            KeyDef(KeyAction.Usage(u.B)),
            KeyDef(KeyAction.Usage(u.N)),
            KeyDef(KeyAction.Usage(u.M)),
            KeyDef(KeyAction.Usage(u.SLASH)),
            KeyDef(KeyAction.Shift, 1.45f),
        ),
        listOf(
            KeyDef(KeyAction.Emoji, 1.25f),
            KeyDef(KeyAction.Usage(u.COMMA)),
            KeyDef(KeyAction.Space, 5.2f),
            KeyDef(KeyAction.Usage(u.DOT)),
            KeyDef(KeyAction.Enter, 1.55f),
        ),
    )
}

private fun qwerty(): Map<Int, Glyph> = usPunctuation() + letters(
    HidUsage.Q to "q", HidUsage.W to "w", HidUsage.E to "e", HidUsage.R to "r",
    HidUsage.T to "t", HidUsage.Y to "y", HidUsage.U to "u", HidUsage.I to "i",
    HidUsage.O to "o", HidUsage.P to "p",
    HidUsage.A to "a", HidUsage.S to "s", HidUsage.D to "d", HidUsage.F to "f",
    HidUsage.G to "g", HidUsage.H to "h", HidUsage.J to "j", HidUsage.K to "k",
    HidUsage.L to "l",
    HidUsage.Z to "z", HidUsage.X to "x", HidUsage.C to "c", HidUsage.V to "v",
    HidUsage.B to "b", HidUsage.N to "n", HidUsage.M to "m",
) + mapOf(
    HidUsage.LEFT_BRACE to Glyph("[", "{"),
    HidUsage.RIGHT_BRACE to Glyph("]", "}"),
    HidUsage.BACKSLASH to Glyph("\\", "|"),
    HidUsage.SEMICOLON to Glyph(";", ":"),
    HidUsage.APOSTROPHE to Glyph("'", "\""),
    HidUsage.NON_US to Glyph("<", ">"),
    HidUsage.COMMA to Glyph(",", "<"),
    HidUsage.DOT to Glyph(".", ">"),
    HidUsage.SLASH to Glyph("/", "?"),
)

private fun azerty(): Map<Int, Glyph> = mapOf(
    HidUsage.GRAVE to Glyph("²", ""),
    HidUsage.N1 to Glyph("&", "1"),
    HidUsage.N2 to Glyph("é", "2"),
    HidUsage.N3 to Glyph("\"", "3"),
    HidUsage.N4 to Glyph("'", "4"),
    HidUsage.N5 to Glyph("(", "5"),
    HidUsage.N6 to Glyph("-", "6"),
    HidUsage.N7 to Glyph("è", "7"),
    HidUsage.N8 to Glyph("_", "8"),
    HidUsage.N9 to Glyph("ç", "9"),
    HidUsage.N0 to Glyph("à", "0"),
    HidUsage.MINUS to Glyph(")", "°"),
    HidUsage.EQUAL to Glyph("=", "+"),
    HidUsage.LEFT_BRACE to Glyph("^", "¨"),
    HidUsage.RIGHT_BRACE to Glyph("$", "£"),
    HidUsage.BACKSLASH to Glyph("*", "µ"),
    HidUsage.SEMICOLON to Glyph("m", "M"),
    HidUsage.APOSTROPHE to Glyph("ù", "%"),
    HidUsage.NON_US to Glyph("<", ">"),
    HidUsage.M to Glyph(",", "?"),
    HidUsage.COMMA to Glyph(";", "."),
    HidUsage.DOT to Glyph(":", "/"),
    HidUsage.SLASH to Glyph("!", "§"),
) + letters(
    HidUsage.Q to "a", HidUsage.W to "z", HidUsage.E to "e", HidUsage.R to "r",
    HidUsage.T to "t", HidUsage.Y to "y", HidUsage.U to "u", HidUsage.I to "i",
    HidUsage.O to "o", HidUsage.P to "p",
    HidUsage.A to "q", HidUsage.S to "s", HidUsage.D to "d", HidUsage.F to "f",
    HidUsage.G to "g", HidUsage.H to "h", HidUsage.J to "j", HidUsage.K to "k",
    HidUsage.L to "l",
    HidUsage.Z to "w", HidUsage.X to "x", HidUsage.C to "c", HidUsage.V to "v",
    HidUsage.B to "b", HidUsage.N to "n",
)

private fun qwertz(): Map<Int, Glyph> = mapOf(
    HidUsage.GRAVE to Glyph("^", "°"),
    HidUsage.N1 to Glyph("1", "!"),
    HidUsage.N2 to Glyph("2", "\""),
    HidUsage.N3 to Glyph("3", "§"),
    HidUsage.N4 to Glyph("4", "$"),
    HidUsage.N5 to Glyph("5", "%"),
    HidUsage.N6 to Glyph("6", "&"),
    HidUsage.N7 to Glyph("7", "/"),
    HidUsage.N8 to Glyph("8", "("),
    HidUsage.N9 to Glyph("9", ")"),
    HidUsage.N0 to Glyph("0", "="),
    HidUsage.MINUS to Glyph("ß", "?"),
    HidUsage.EQUAL to Glyph("´", "`"),
    HidUsage.LEFT_BRACE to Glyph("ü", "Ü"),
    HidUsage.RIGHT_BRACE to Glyph("+", "*"),
    HidUsage.BACKSLASH to Glyph("#", "'"),
    HidUsage.SEMICOLON to Glyph("ö", "Ö"),
    HidUsage.APOSTROPHE to Glyph("ä", "Ä"),
    HidUsage.NON_US to Glyph("<", ">"),
    HidUsage.COMMA to Glyph(",", ";"),
    HidUsage.DOT to Glyph(".", ":"),
    HidUsage.SLASH to Glyph("-", "_"),
) + letters(
    HidUsage.Q to "q", HidUsage.W to "w", HidUsage.E to "e", HidUsage.R to "r",
    HidUsage.T to "t", HidUsage.Y to "z", HidUsage.U to "u", HidUsage.I to "i",
    HidUsage.O to "o", HidUsage.P to "p",
    HidUsage.A to "a", HidUsage.S to "s", HidUsage.D to "d", HidUsage.F to "f",
    HidUsage.G to "g", HidUsage.H to "h", HidUsage.J to "j", HidUsage.K to "k",
    HidUsage.L to "l",
    HidUsage.Z to "y", HidUsage.X to "x", HidUsage.C to "c", HidUsage.V to "v",
    HidUsage.B to "b", HidUsage.N to "n", HidUsage.M to "m",
)

private fun colemak(): Map<Int, Glyph> = qwerty() + letters(
    HidUsage.E to "f", HidUsage.R to "p", HidUsage.T to "g",
    HidUsage.Y to "j", HidUsage.U to "l", HidUsage.I to "u",
    HidUsage.O to "y",
    HidUsage.S to "r", HidUsage.D to "s", HidUsage.F to "t", HidUsage.G to "d",
    HidUsage.J to "n", HidUsage.K to "e", HidUsage.L to "i",
    HidUsage.N to "k",
) + mapOf(
    HidUsage.P to Glyph(";", ":"),
    HidUsage.SEMICOLON to Glyph("o", "O"),
)

private fun dvorak(): Map<Int, Glyph> = mapOf(
    HidUsage.GRAVE to Glyph("`", "~"),
    HidUsage.N1 to Glyph("1", "!"),
    HidUsage.N2 to Glyph("2", "@"),
    HidUsage.N3 to Glyph("3", "#"),
    HidUsage.N4 to Glyph("4", "$"),
    HidUsage.N5 to Glyph("5", "%"),
    HidUsage.N6 to Glyph("6", "^"),
    HidUsage.N7 to Glyph("7", "&"),
    HidUsage.N8 to Glyph("8", "*"),
    HidUsage.N9 to Glyph("9", "("),
    HidUsage.N0 to Glyph("0", ")"),
    HidUsage.MINUS to Glyph("[", "{"),
    HidUsage.EQUAL to Glyph("]", "}"),
    HidUsage.Q to Glyph("'", "\""),
    HidUsage.W to Glyph(",", "<"),
    HidUsage.E to Glyph(".", ">"),
    HidUsage.LEFT_BRACE to Glyph("/", "?"),
    HidUsage.RIGHT_BRACE to Glyph("=", "+"),
    HidUsage.BACKSLASH to Glyph("\\", "|"),
    HidUsage.APOSTROPHE to Glyph("-", "_"),
    HidUsage.Z to Glyph(";", ":"),
    HidUsage.NON_US to Glyph("<", ">"),
    HidUsage.COMMA to Glyph("w", "W"),
    HidUsage.DOT to Glyph("v", "V"),
    HidUsage.SLASH to Glyph("z", "Z"),
) + letters(
    HidUsage.R to "p", HidUsage.T to "y", HidUsage.Y to "f", HidUsage.U to "g",
    HidUsage.I to "c", HidUsage.O to "r", HidUsage.P to "l",
    HidUsage.A to "a", HidUsage.S to "o", HidUsage.D to "e", HidUsage.F to "u",
    HidUsage.G to "i", HidUsage.H to "d", HidUsage.J to "h", HidUsage.K to "t",
    HidUsage.L to "n", HidUsage.SEMICOLON to "s",
    HidUsage.X to "q", HidUsage.C to "j", HidUsage.V to "k", HidUsage.B to "x",
    HidUsage.N to "b", HidUsage.M to "m",
)

private fun bepo(): Map<Int, Glyph> = mapOf(
    HidUsage.GRAVE to Glyph("$", "#"),
    HidUsage.N1 to Glyph("\"", "1"),
    HidUsage.N2 to Glyph("«", "2"),
    HidUsage.N3 to Glyph("»", "3"),
    HidUsage.N4 to Glyph("(", "4"),
    HidUsage.N5 to Glyph(")", "5"),
    HidUsage.N6 to Glyph("@", "6"),
    HidUsage.N7 to Glyph("+", "7"),
    HidUsage.N8 to Glyph("-", "8"),
    HidUsage.N9 to Glyph("/", "9"),
    HidUsage.N0 to Glyph("*", "0"),
    HidUsage.MINUS to Glyph("=", "°"),
    HidUsage.EQUAL to Glyph("%", "`"),
    HidUsage.Y to Glyph("^", "!"),
    HidUsage.LEFT_BRACE to Glyph("z", "Z"),
    HidUsage.RIGHT_BRACE to Glyph("w", "W"),
    HidUsage.BACKSLASH to Glyph("ç", "Ç"),
    HidUsage.G to Glyph(",", ";"),
    HidUsage.NON_US to Glyph("ê", "Ê"),
    HidUsage.V to Glyph(".", ":"),
    HidUsage.B to Glyph("k", "K"),
    HidUsage.N to Glyph("'", "?"),
    HidUsage.M to Glyph("q", "Q"),
    HidUsage.COMMA to Glyph("g", "G"),
    HidUsage.DOT to Glyph("h", "H"),
    HidUsage.SLASH to Glyph("f", "F"),
) + letters(
    HidUsage.Q to "b", HidUsage.W to "é", HidUsage.E to "p", HidUsage.R to "o",
    HidUsage.T to "è", HidUsage.U to "v", HidUsage.I to "d", HidUsage.O to "l",
    HidUsage.P to "j",
    HidUsage.A to "a", HidUsage.S to "u", HidUsage.D to "i", HidUsage.F to "e",
    HidUsage.H to "c", HidUsage.J to "t", HidUsage.K to "s", HidUsage.L to "r",
    HidUsage.SEMICOLON to "n", HidUsage.APOSTROPHE to "m",
    HidUsage.Z to "à", HidUsage.X to "y", HidUsage.C to "x",
)

private fun usPunctuation(): Map<Int, Glyph> = mapOf(
    HidUsage.GRAVE to Glyph("`", "~"),
    HidUsage.N1 to Glyph("1", "!"),
    HidUsage.N2 to Glyph("2", "@"),
    HidUsage.N3 to Glyph("3", "#"),
    HidUsage.N4 to Glyph("4", "$"),
    HidUsage.N5 to Glyph("5", "%"),
    HidUsage.N6 to Glyph("6", "^"),
    HidUsage.N7 to Glyph("7", "&"),
    HidUsage.N8 to Glyph("8", "*"),
    HidUsage.N9 to Glyph("9", "("),
    HidUsage.N0 to Glyph("0", ")"),
    HidUsage.MINUS to Glyph("-", "_"),
    HidUsage.EQUAL to Glyph("=", "+"),
)

private fun letters(vararg pairs: Pair<Int, String>): Map<Int, Glyph> =
    pairs.associate { (usage, lower) ->
        usage to Glyph(lower, lower.uppercase(Locale.ROOT))
    }
