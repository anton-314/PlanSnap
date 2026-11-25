package app.plansnap.core.export.svg

import java.util.Locale

/** Locale-independent number formatting and XML escaping for the SVG output. */
internal object SvgText {
    private const val DECIMALS = 3
    private const val FIRST_PRINTABLE = 0x20
    private const val FIRST_NONCHARACTER = 0xFFFE

    /**
     * Formats [value] with at most three decimals, a point as decimal separator and without trailing zeros, so the
     * output does not depend on the default locale.
     *
     * @throws IllegalArgumentException if [value] is not finite.
     */
    fun number(value: Double): String {
        require(value.isFinite()) { "SVG numbers must be finite: $value" }
        val fixed = String.format(Locale.ROOT, "%.${DECIMALS}f", value)
        val trimmed = if ('.' in fixed) fixed.trimEnd('0').trimEnd('.') else fixed
        return if (trimmed == "-0") "0" else trimmed
    }

    /**
     * Escapes [text] for element content or, with [attribute], for a double-quoted attribute value. Characters that
     * XML 1.0 forbids are dropped.
     */
    fun escape(
        text: String,
        attribute: Boolean = false,
    ): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                out.append(c).append(text[i + 1])
                i++
            } else {
                appendEscaped(out, c, attribute)
            }
            i++
        }
        return out.toString()
    }

    private fun appendEscaped(
        out: StringBuilder,
        c: Char,
        attribute: Boolean,
    ) {
        when {
            c == '&' -> out.append("&amp;")
            c == '<' -> out.append("&lt;")
            c == '>' -> out.append("&gt;")
            c == '"' && attribute -> out.append("&quot;")
            attribute && (c == '\n' || c == '\r' || c == '\t') -> out.append("&#").append(c.code).append(';')
            isXmlChar(c) -> out.append(c)
        }
    }

    private fun isXmlChar(c: Char): Boolean =
        c == '\t' ||
            c == '\n' ||
            c == '\r' ||
            (c.code >= FIRST_PRINTABLE && !Character.isSurrogate(c) && c.code < FIRST_NONCHARACTER)
}
