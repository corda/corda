package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

abstract class CustomSerializer<T> : AMQPSerializer<T> {
    abstract fun isSerializerFor(clazz: Class<*>): Boolean
    protected abstract val descriptor: Descriptor

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        data.withDescribed(descriptor) {
            writeDescribedObject(obj as T, data, type, output)
        }
    }

    abstract fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput)

    abstract class Is<T>(protected val clazz: Class<T>) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = clazz == this.clazz
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${clazz.simpleName}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
    }

    abstract class Implements<T>(protected val clazz: Class<T>) : CustomSerializer<T>() {
        override fun isSerializerFor(clazz: Class<*>): Boolean = this.clazz.isAssignableFrom(clazz)
        override val type: Type get() = clazz
        override val typeDescriptor: String = "$DESCRIPTOR_DOMAIN:${clazz.simpleName}"
        override fun writeClassInfo(output: SerializationOutput) {}
        override val descriptor: Descriptor = Descriptor(typeDescriptor)
    }
}