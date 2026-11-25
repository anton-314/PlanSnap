package app.plansnap.feature.editor.input

private const val MAX_DIGIT = 9

/** A key of the numeric keypad that edits the running input text. */
internal sealed interface KeypadKey {
    /** A digit key, [digit] in `0..9`. */
    data class Digit(
        val digit: Int,
    ) : KeypadKey {
        init {
            require(digit in 0..MAX_DIGIT) { "Not a digit: $digit" }
        }
    }

    /** The decimal separator key. */
    data object Separator : KeypadKey

    /** Deletes the last character. */
    data object Backspace : KeypadKey
}

/**
 * Text editing of the keypad. The keypad is the only input of a length, so the text can never contain anything but
 * digits and one separator. Interpreting the text as a length is left to the editor state.
 */
internal object KeypadText {
    /** Longest text the keypad produces; longer values are no plausible wall length. */
    const val MAX_CHARS = 12

    /**
     * Applies [key] to [text] and returns the new text, the unchanged [text] if the key has no effect.
     *
     * @param separator decimal separator of the device language, typed by [KeypadKey.Separator].
     */
    fun apply(
        text: String,
        key: KeypadKey,
        separator: Char,
    ): String =
        when (key) {
            is KeypadKey.Digit -> appendDigit(text, key.digit)
            KeypadKey.Separator -> appendSeparator(text, separator)
            KeypadKey.Backspace -> text.dropLast(1)
        }

    private fun appendDigit(
        text: String,
        digit: Int,
    ): String =
        when {
            text.length >= MAX_CHARS -> text
            text == "0" -> digit.toString()
            else -> text + digit
        }

    private fun appendSeparator(
        text: String,
        separator: Char,
    ): String =
        when {
            text.any { it == ',' || it == '.' } -> text
            text.length >= MAX_CHARS -> text
            text.isEmpty() -> "0$separator"
            else -> text + separator
        }
}
