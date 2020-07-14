package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.serialization.internal.amqp.CORDAPP_TYPE
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

/**
 * Adapts CheckpointCustomSerializer for use in Kryo
 */
internal class CustomSerializerCheckpointAdaptor<OBJ, PROXY>(private val userSerializer : CheckpointCustomSerializer<OBJ, PROXY>) : Serializer<OBJ>() {

    /**
     * The class name of the serializer we are adapting.
     */
    val serializerName: String = userSerializer.javaClass.name

    /**
     * The input type of this custom serializer.
     */
    val cordappType: Type

    /**
     * Check we have access to the types specified on the CheckpointCustomSerializer interface.
     *
     * Throws UnableToDetermineSerializerTypesException if the types are missing.
     */
    init {
        val types: List<Type> = userSerializer::class
                .supertypes
                .filter { it.jvmErasure == CheckpointCustomSerializer::class }
                .flatMap { it.arguments }
                .mapNotNull { it.type?.javaType }

        // We are expecting a cordapp type and a proxy type.
        // We will only use the cordapp type in this class
        // but we want to check both are present.
        val typeParameterCount = 2
        if (types.size != typeParameterCount) {
            throw UnableToDetermineSerializerTypesException("Unable to determine serializer parent types")
        }
        cordappType = types[CORDAPP_TYPE]
    }

    /**
     * Serialize obj to the Kryo stream.
     */
    override fun write(kryo: Kryo, output: Output, obj: OBJ) {

        fun <T> writeToKryo(obj: T) = kryo.writeClassAndObject(output, obj)

        // Write serializer type
        writeToKryo(serializerName)

        // Write proxy object
        writeToKryo(userSerializer.toProxy(obj))
    }

    /**
     * Deserialize an object from the Kryo stream.
     */
    override fun read(kryo: Kryo, input: Input, type: Class<OBJ>): OBJ {

        @Suppress("UNCHECKED_CAST")
        fun <T> readFromKryo() = kryo.readClassAndObject(input) as T

        // Check the serializer type
        checkSerializerType(readFromKryo())

        // Read the proxy object
        return userSerializer.fromProxy(readFromKryo())
    }

    /**
     * Throws a `CustomCheckpointSerializersHaveChangedException` if the serializer type in the kryo stream does not match the serializer
     * type for this custom serializer.
     *
     * @param checkpointSerializerType Serializer type from the Kryo stream
     */
    private fun checkSerializerType(checkpointSerializerType: String) {
        if (checkpointSerializerType != serializerName)
            throw CustomCheckpointSerializersHaveChangedException("The custom checkpoint serializers have changed while checkpoints exist. " +
                "Please restore the CorDapps to when this checkpoint was created.")
    }
}

/**
 * Thrown when the input/output types are missing from the custom serializer.
 */
class UnableToDetermineSerializerTypesException(message: String) : RuntimeException(message)

/**
 * Thrown when the custom serializer is found to be reading data from another type of custom serializer.
 *
 * This was expected to happen if the user adds or removes CorDapps while checkpoints exist but it turned out that registering serializers
 * as default made the system reliable.
 */
class CustomCheckpointSerializersHaveChangedException(message: String) : RuntimeException(message)
