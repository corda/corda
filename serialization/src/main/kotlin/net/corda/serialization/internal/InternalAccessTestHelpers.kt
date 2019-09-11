package net.corda.serialization.internal

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.amqp.*
import java.lang.reflect.Type

/**
 * A set of functions in serialization:test that allows testing of serialization internal classes in the serialization-tests project.
 */

const val MAX_TYPE_PARAM_DEPTH = AMQPTypeIdentifierParser.MAX_TYPE_PARAM_DEPTH

fun Class<out Any?>.accessPropertyDescriptors(validateProperties: Boolean = true): Map<String, PropertyDescriptor> = propertyDescriptors(validateProperties)
fun Type.accessAsClass(): Class<*> = asClass()
fun <T> ifThrowsAppend(strToAppendFn: () -> String, block: () -> T): T = net.corda.serialization.internal.amqp.ifThrowsAppend(strToAppendFn, block)
fun SerializationOutput.andFinally() = this.andFinally()
fun <T : Any> SerializationOutput._serialize(obj: T, context: SerializationContext): SerializedBytes<T> = this._serialize(obj, context)

object EmptyWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = false
}