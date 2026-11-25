package app.plansnap.feature.editor.input

import org.junit.Assert.assertEquals
import org.junit.Test

class KeypadTextTest {
    private fun type(
        start: String,
        vararg keys: KeypadKey,
        separator: Char = ',',
    ): String = keys.fold(start) { text, key -> KeypadText.apply(text, key, separator) }

    private fun digits(vararg d: Int) = d.map { KeypadKey.Digit(it) }.toTypedArray()

    @Test
    fun `given empty text when digits are typed then they are appended`() {
        assertEquals("345", type("", *digits(3, 4, 5)))
    }

    @Test
    fun `given a leading zero when a digit is typed then the zero is replaced`() {
        assertEquals("5", type("", *digits(0, 5)))
    }

    @Test
    fun `given empty text when the separator is typed then a zero is put in front`() {
        assertEquals("0,", type("", KeypadKey.Separator))
        assertEquals("0.", type("", KeypadKey.Separator, separator = '.'))
    }

    @Test
    fun `given a separator when another separator is typed then nothing changes`() {
        assertEquals("3,5", type("3,5", KeypadKey.Separator))
        assertEquals("3.5", type("3.5", KeypadKey.Separator))
    }

    @Test
    fun `given text when backspace is typed then the last character is removed`() {
        assertEquals("34", type("345", KeypadKey.Backspace))
        assertEquals("", type("", KeypadKey.Backspace))
    }

    @Test
    fun `given the maximum length when a key is typed then nothing changes`() {
        val full = "1".repeat(KeypadText.MAX_CHARS)
        assertEquals(full, type(full, KeypadKey.Digit(2)))
        assertEquals(full, type(full, KeypadKey.Separator))
    }
}
