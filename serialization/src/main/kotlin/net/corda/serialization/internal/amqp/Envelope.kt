package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import org.apache.qpid.proton.ProtonException
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.Data
import org.apache.qpid.proton.codec.DecoderImpl
import org.apache.qpid.proton.codec.EncodingCodes
import org.apache.qpid.proton.codec.FastPathDescribedTypeConstructor
import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * This class wraps all serialized data, so that the schema can be carried along with it.  We will provide various
 * internal utilities to decompose and recompose with/without schema etc so that e.g. we can store objects with a
 * (relationally) normalised out schema to avoid excessive duplication.
 */
@KeepForDJVM
class Envelope(val obj: Any?, resolveSchema: () -> Pair<Schema, TransformsSchema>) : DescribedType {

    val resolvedSchema: Pair<Schema, TransformsSchema> by lazy(resolveSchema)

    val schema: Schema get() = resolvedSchema.first
    val transformsSchema: TransformsSchema get() = resolvedSchema.second

    companion object {
        val DESCRIPTOR = AMQPDescriptorRegistry.ENVELOPE.amqpDescriptor
        val DESCRIPTOR_OBJECT = Descriptor(null, DESCRIPTOR)

        // described list should either be two or three elements long
        private const val ENVELOPE_WITHOUT_TRANSFORMS = 2
        private const val ENVELOPE_WITH_TRANSFORMS = 3
    }

    class FastPathConstructor(private val decoder: DecoderImpl) : FastPathDescribedTypeConstructor<Envelope> {

        private val _buffer: ByteBuffer get() = decoder.byteBuffer

        @Suppress("ComplexMethod", "MagicNumber")
        private fun readEncodingAndReturnSize(buffer: ByteBuffer, inBytes: Boolean = true): Int {
            val encodingCode: Byte = buffer.get()
            return when (encodingCode) {
                EncodingCodes.LIST8 -> {
                    (buffer.get().toInt() and 0xff).let { if (inBytes) it else (buffer.get().toInt() and 0xff) }
                }
                EncodingCodes.LIST32 -> {
                    buffer.int.let { if (inBytes) it else buffer.int }
                }
                else -> throw ProtonException("Expected List type but found encoding: $encodingCode")
            }
        }

        override fun readValue(): Envelope? {
            val buffer = _buffer
            val size = readEncodingAndReturnSize(buffer, false)
            if (size != ENVELOPE_WITHOUT_TRANSFORMS && size != ENVELOPE_WITH_TRANSFORMS) {
                throw AMQPNoTypeNotSerializableException("Malformed list, bad length of $size (should be 2 or 3)")
            }
            val data = Data.Factory.create()
            data.decode(buffer)
            val obj = data.`object`
            val lambda: () -> Pair<Schema, TransformsSchema> = {
                data.decode(buffer)
                val schema = data.`object`
                val transformsSchema = if (size > 2) {
                    data.decode(buffer)
                    data.`object`
                } else null
                Schema.get(schema) to TransformsSchema.newInstance(transformsSchema)
            }
            return Envelope(obj, lambda)
        }

        override fun skipValue() {
            val buffer = _buffer
            val size = readEncodingAndReturnSize(buffer)
            (buffer as Buffer).position(buffer.position() + size)
        }

        override fun encodesJavaPrimitive(): Boolean = false

        override fun getTypeClass(): Class<Envelope> = Envelope::class.java
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(obj, schema, transformsSchema)
}
