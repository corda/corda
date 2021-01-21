package net.corda.nodeapi.internal.serialization

import jdk.nashorn.internal.ir.annotations.Ignore
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
        //If only this was C
        const val SIZE_OF_INT = 4
    }

    val serializationSchemeMagic = CordaSerializationMagic("CUS".toByteArray()
            + ByteBuffer.allocate(SIZE_OF_INT).putInt(customScheme.getSerializationMagic().magicNumber).array())

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return magic == serializationSchemeMagic
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        val readMagic = byteSequence.take(serializationSchemeMagic.size)
        if (readMagic != serializationSchemeMagic)
            throw NotSerializableException("Scheme ${customScheme::class.java} is incompatible with blob." +
                    " Magic from blob = $readMagic (Expected = $serializationSchemeMagic)")
        val withOutMagic = byteSequence.bytes.slice(serializationSchemeMagic.size .. byteSequence.size - 1).toByteArray()
      @Suppress("UNCHECKED_CAST")
      return customScheme.deserialize(SerializedBytes<T>(withOutMagic), clazz, SerializationContextAdapter(context)) as T
  }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        val stream = ByteArrayOutputStream()
        stream.write(serializationSchemeMagic.bytes)
        stream.write(customScheme.serialize(obj, SerializationContextAdapter(context)).bytes)
        return SerializedBytes(stream.toByteArray())
    }

    private class SerializationContextAdapter(context: SerializationContext) : CustomSerializationContext {
        override val deserializationClassLoader = context.deserializationClassLoader
        override val whitelist = context.whitelist
    }
}