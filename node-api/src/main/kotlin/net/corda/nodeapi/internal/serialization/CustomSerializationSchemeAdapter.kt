package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.CustomSerializationContext
import net.corda.core.serialization.CustomSerializationScheme
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ByteSequence
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationScheme
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.nio.ByteBuffer

class CustomSerializationSchemeAdapter(private val customScheme: CustomSerializationScheme): SerializationScheme {

    companion object {
        const val SERIALIZATION_MAGIC_BUFFER_SIZE = 4
    }

    val serializationSchemeMagic = CordaSerializationMagic("CUS".toByteArray()
            + ByteBuffer.allocate(SERIALIZATION_MAGIC_BUFFER_SIZE).putInt(customScheme.getSchemeId()).array())

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return magic == serializationSchemeMagic
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        val readMagic = byteSequence.take(serializationSchemeMagic.size)
        if (readMagic != serializationSchemeMagic)
            throw NotSerializableException("Scheme ${customScheme::class.java} is incompatible with blob." +
                    " Magic from blob = $readMagic (Expected = $serializationSchemeMagic)")
        val withOutMagic = byteSequence.bytes.slice(serializationSchemeMagic.size until byteSequence.size).toByteArray()
      return customScheme.deserialize(SerializedBytes(withOutMagic), clazz, SerializationContextAdapter(context))
  }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        val stream = ByteArrayOutputStream()
        stream.write(serializationSchemeMagic.bytes)
        stream.write(customScheme.serialize<T>(obj, SerializationContextAdapter(context)).bytes)
        return SerializedBytes(stream.toByteArray())
    }

    private class SerializationContextAdapter(context: SerializationContext) : CustomSerializationContext {
        override val deserializationClassLoader = context.deserializationClassLoader
        override val whitelist = context.whitelist
    }
}