package app.plansnap.core.export.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

class SvgTextTest {
    @Test
    fun `given numbers when formatting then a point separates decimals and zeros are trimmed`() {
        assertEquals("1.5", SvgText.number(1.5))
        assertEquals("2", SvgText.number(2.0))
        assertEquals("0", SvgText.number(-0.0001))
        assertEquals("-12.346", SvgText.number(-12.3456))
        assertEquals("1000000", SvgText.number(1_000_000.0))
    }

    @Test
    fun `given a comma locale as default when formatting then the separator stays a point`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.5", SvgText.number(1.5))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `given a non finite number when formatting then it is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SvgText.number(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { SvgText.number(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `given markup when escaping content then the markup characters are replaced`() {
        assertEquals("a &lt;b&gt; &amp; \"c\" 'd'", SvgText.escape("a <b> & \"c\" 'd'"))
    }

    @Test
    fun `given an attribute value when escaping then quotes and line breaks are replaced`() {
        assertEquals("&quot;x&quot;&#10;&#9;&#13;", SvgText.escape("\"x\"\n\t\r", attribute = true))
    }

    @Test
    fun `given characters XML forbids when escaping then they are dropped and emoji stay`() {
        assertEquals("ab😀", SvgText.escape("a\u0000b￾\uD800😀"))
    }
}
