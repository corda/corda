package net.corda.serialization.internal.verifier

import net.corda.core.CordaException
import net.corda.core.internal.loadClassOfType
import net.corda.core.serialization.CustomSerializationScheme
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationSchemeContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CustomSerializationSchemeUtils.Companion.getCustomSerializationMagicFromSchemeId
import net.corda.core.utilities.ByteSequence
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationScheme
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException

class CustomSerializationSchemeAdapter(private val customScheme: CustomSerializationScheme): SerializationScheme {
    private val serializationSchemeMagic = getCustomSerializationMagicFromSchemeId(customScheme.getSchemeId())

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return magic == serializationSchemeMagic
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        val readMagic = byteSequence.take(serializationSchemeMagic.size)
        if (readMagic != serializationSchemeMagic) {
            throw NotSerializableException("Scheme ${customScheme::class.java} is incompatible with blob." +
                    " Magic from blob = $readMagic (Expected = $serializationSchemeMagic)")
        }
        return customScheme.deserialize(
            byteSequence.subSequence(serializationSchemeMagic.size, byteSequence.size - serializationSchemeMagic.size),
            clazz,
            SerializationSchemeContextAdapter(context)
        )
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        val stream = ByteArrayOutputStream()
        stream.write(serializationSchemeMagic.bytes)
        stream.write(customScheme.serialize(obj, SerializationSchemeContextAdapter(context)).bytes)
        return SerializedBytes(stream.toByteArray())
    }

    private class SerializationSchemeContextAdapter(context: SerializationContext) : SerializationSchemeContext {
        override val deserializationClassLoader = context.deserializationClassLoader
        override val whitelist = context.whitelist
        override val properties = context.properties
    }
}

@Suppress("ThrowsCount")
fun loadCustomSerializationScheme(className: String, classLoader: ClassLoader): SerializationScheme {
    val schemeClass = try {
        loadClassOfType<CustomSerializationScheme>(className, false, classLoader)
    } catch (e: ClassNotFoundException) {
        throw CordaException("$className was declared as a custom serialization scheme but could not be found.")
    } catch (e: ClassCastException) {
        throw CordaException("$className was declared as a custom serialization scheme but does not implement CustomSerializationScheme")
    }
    val constructor = try {
        schemeClass.getDeclaredConstructor()
    } catch (e: NoSuchMethodException) {
        throw CordaException("$className was declared as a custom serialization scheme but does not have a no argument constructor.")
    }
    return CustomSerializationSchemeAdapter(constructor.newInstance())
}
