package app.plansnap.core.document

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Reads a JSON number literal. Quoted numbers and `null` are rejected, unlike the library default. */
private fun Decoder.numberLiteral(): JsonPrimitive {
    val primitive =
        (this as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Expected a JSON number")
    if (primitive.isString) throw SerializationException("Expected a JSON number, found a string")
    return primitive
}

/** Strict [Long]: only unquoted integers. */
internal object StrictLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StrictLong", PrimitiveKind.LONG)

    override fun serialize(
        encoder: Encoder,
        value: Long,
    ) = encoder.encodeLong(value)

    override fun deserialize(decoder: Decoder): Long =
        decoder.numberLiteral().longOrNull ?: throw SerializationException("Expected an integer")
}

/** Strict [Int]: only unquoted integers. */
internal object StrictIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StrictInt", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: Int,
    ) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int =
        decoder.numberLiteral().intOrNull ?: throw SerializationException("Expected an integer")
}

/**
 * Strict finite [Double]: only unquoted finite numbers. Encoding uses the shortest representation that parses back
 * to the same bits (`Double.toString`), which includes `-0.0`, subnormals and 17 significant digits.
 */
internal object StrictDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StrictDouble", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: Double,
    ) = encoder.encodeDouble(value)

    override fun deserialize(decoder: Decoder): Double {
        val value = decoder.numberLiteral().doubleOrNull ?: throw SerializationException("Expected a number")
        if (!value.isFinite()) throw SerializationException("Expected a finite number")
        return value
    }
}
