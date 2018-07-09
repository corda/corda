package net.corda.serialization.internal.amqp

import net.corda.core.internal.VisibleForTesting
import org.slf4j.Logger
import java.io.NotSerializableException
import java.lang.reflect.Type

class SyntheticParameterException(val type: Type) : NotSerializableException("Type '${type.typeName} has synthetic "
        + "fields and is likely a nested inner class. This is not support by the Corda AMQP serialization framework")

/**
 * Not a public property so will have to use reflection
 */
private fun Throwable.setMessage(newMsg: String) {
    val detailMessageField = Throwable::class.java.getDeclaredField("detailMessage")
    detailMessageField.isAccessible = true
    detailMessageField.set(this, newMsg)
}

/**
 * Utility function which helps tracking the path in the object graph when exceptions are thrown.
 * Since there might be a chain of nested calls it is useful to record which part of the graph caused an issue.
 * Path information is added to the message of the exception being thrown.
 */
internal inline fun <T> ifThrowsAppend(strToAppendFn: () -> String, block: () -> T): T {
    try {
        return block()
    } catch (th: Throwable) {
        if (th is AMQPNotSerializableException) {
            th.classHierarchy.add(strToAppendFn())
        } else {
            th.setMessage("${strToAppendFn()} -> ${th.message}")
        }
        throw th
    }
}

class AMQPNoTypeNotSerializableException(
        msg: String,
        mitigation: String = msg
) : AMQPNotSerializableException(Class::class.java, msg, mitigation, mutableListOf("Unknown"))

/**
 * The purpose of the [AMQPNotSerializableException] is to encapsulate internal serialization errors
 * within the serialization framework and to capture errors when building serializer objects
 * that will aid in debugging whilst also allowing "user helpful" information to be communicated
 * outward to end users.
 *
 * @property type the class that failed to serialize
 * @property msg the specific error
 * @property mitigation information useful to an end user.
 * @property classHierarchy represents the call hierarchy to the point of error. This is useful
 * when debugging a deeply nested class that fails to serialize as that classes position in
 * the class hierarchy is preserved, otherwise that information is lost. This list is automatically
 * updated by the [ifThrowsAppend] function used within this library.
 */
open class AMQPNotSerializableException(
        val type: Type,
        val msg: String,
        val mitigation: String = msg,
        val classHierarchy : MutableList<String> = mutableListOf(type.typeName)
) : NotSerializableException(msg) {
    @Suppress("Unused")
    constructor(type: Type) : this (type, "$type is not serializable")

    @VisibleForTesting
    fun errorMessage(direction: String) : String {
        return "Serialization failed direction=\"$direction\", type=\"${type.typeName}\", " +
                "msg=\"$msg\", " +
                "ClassChain=\"${classHierarchy.asReversed().joinToString(" -> ")}\""
    }

    fun log(direction: String, logger: Logger) {
        logger.error(errorMessage(direction))

        // if debug is enabled print the stack, the exception we allow to escape
        // will be printed into the log anyway by the handling thread
        logger.debug("", cause)
    }
}