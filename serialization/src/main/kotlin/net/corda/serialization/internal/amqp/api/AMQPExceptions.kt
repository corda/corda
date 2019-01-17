package net.corda.serialization.internal.amqp.api

import net.corda.core.internal.VisibleForTesting
import net.corda.serialization.internal.model.AppendableException
import org.slf4j.Logger
import java.io.NotSerializableException
import java.lang.reflect.Type

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
) : NotSerializableException(msg), AppendableException {
    @Suppress("Unused")
    constructor(type: Type) : this (type, "type=${type.typeName} is not serializable")

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

    override fun append(strToAppend: String) {
        classHierarchy.add(strToAppend)
    }
}