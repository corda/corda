package net.corda.serialization.djvm.serializers

import com.google.common.reflect.TypeToken
import net.corda.core.internal.objectOrNewInstance
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.CorDappCustomDeserializer
import net.corda.serialization.internal.amqp.AMQPNotSerializableException
import net.corda.serialization.internal.amqp.AMQPTypeIdentifiers
import net.corda.serialization.internal.amqp.CORDAPP_TYPE
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.Descriptor
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.ObjectSerializer
import net.corda.serialization.internal.amqp.PROXY_TYPE
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.typeDescriptorFor
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Collections.singleton
import java.util.function.Function

class SandboxCorDappCustomSerializer(
    private val serializerName: String,
    classLoader: SandboxClassLoader,
    rawTaskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    factory: SerializerFactory
) : CustomSerializer<Any>() {
    private val unproxy: Function<in Any?, out Any?>
    private val types: List<Type>

    init {
        val serializationCustomSerializer = classLoader.toSandboxClass(SerializationCustomSerializer::class.java)
        val customSerializerClass = classLoader.toSandboxClass(serializerName)
        types = customSerializerClass.genericInterfaces
            .mapNotNull { it as? ParameterizedType }
            .filter { it.rawType == serializationCustomSerializer }
            .flatMap { it.actualTypeArguments.toList() }
        if (types.size != 2) {
            throw AMQPNotSerializableException(
                type = SandboxCorDappCustomSerializer::class.java,
                msg = "Unable to determine serializer parent types"
            )
        }

        val unproxyTask = classLoader.toSandboxClass(CorDappCustomDeserializer::class.java)
            .getConstructor(serializationCustomSerializer)
            .newInstance(customSerializerClass.kotlin.objectOrNewInstance())
        unproxy = rawTaskFactory.apply(unproxyTask)
    }

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override val type: Type = types[CORDAPP_TYPE]
    private val proxySerializer: ObjectSerializer by lazy {
        ObjectSerializer.make(factory.getTypeInformation(types[PROXY_TYPE]), factory)
    }
    private val deserializationAlias: TypeIdentifier get() =
        TypeIdentifier.Erased(AMQPTypeIdentifiers.nameForType(type).replace("sandbox.", ""), 0)

    override val typeDescriptor: Symbol = typeDescriptorFor(type)
    override val descriptor: Descriptor = Descriptor(typeDescriptor)
    override val deserializationAliases: Set<TypeIdentifier> = singleton(deserializationAlias)

    /**
     * For 3rd party plugin serializers we are going to exist on exact type matching. i.e. we will
     * not support base class serializers for derived types.
     */
    override fun isSerializerFor(clazz: Class<*>): Boolean {
        return TypeToken.of(type) == TypeToken.of(clazz)
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return unproxy.apply(proxySerializer.readObject(obj, schemas, input, context))!!
    }

    override fun writeClassInfo(output: SerializationOutput) {
        abortReadOnly()
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }

    override fun toString(): String = "${this::class.java}($serializerName)"
}