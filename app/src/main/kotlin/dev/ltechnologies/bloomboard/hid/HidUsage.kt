package dev.ltechnologies.bloomboard.hid

/** USB HID keyboard usages. Values are positions, not characters. */
object HidUsage {
    const val A = 0x04
    const val B = 0x05
    const val C = 0x06
    const val D = 0x07
    const val E = 0x08
    const val F = 0x09
    const val G = 0x0A
    const val H = 0x0B
    const val I = 0x0C
    const val J = 0x0D
    const val K = 0x0E
    const val L = 0x0F
    const val M = 0x10
    const val N = 0x11
    const val O = 0x12
    const val P = 0x13
    const val Q = 0x14
    const val R = 0x15
    const val S = 0x16
    const val T = 0x17
    const val U = 0x18
    const val V = 0x19
    const val W = 0x1A
    const val X = 0x1B
    const val Y = 0x1C
    const val Z = 0x1D
    const val N1 = 0x1E
    const val N2 = 0x1F
    const val N3 = 0x20
    const val N4 = 0x21
    const val N5 = 0x22
    const val N6 = 0x23
    const val N7 = 0x24
    const val N8 = 0x25
    const val N9 = 0x26
    const val N0 = 0x27
    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val MINUS = 0x2D
    const val EQUAL = 0x2E
    const val LEFT_BRACE = 0x2F
    const val RIGHT_BRACE = 0x30
    const val BACKSLASH = 0x31
    const val SEMICOLON = 0x33
    const val APOSTROPHE = 0x34
    const val GRAVE = 0x35
    const val COMMA = 0x36
    const val DOT = 0x37
    const val SLASH = 0x38
    const val CAPS_LOCK = 0x39
    const val NON_US = 0x64
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52
}

object HidMod {
    const val LEFT_CTRL = 0x01
    const val LEFT_SHIFT = 0x02
    const val LEFT_ALT = 0x04
    const val LEFT_META = 0x08
    const val RIGHT_ALT = 0x40
}

object MouseButton {
    const val LEFT = 0x01
    const val RIGHT = 0x02
    const val MIDDLE = 0x04
}
