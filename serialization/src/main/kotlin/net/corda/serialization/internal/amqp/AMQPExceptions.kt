package net.corda.serialization.internal.amqp

import org.slf4j.Logger
import java.io.NotSerializableException
import java.lang.reflect.Type

class SyntheticParameterException(val type: Type) : NotSerializableException("Type '${type.typeName} has synthetic "
        + "fields and is likely a nested inner class. This is not support by the Corda AMQP serialization framework")

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
class AMQPNotSerializableException(
        val type: Type,
        val msg: String,
        val mitigation: String = msg,
        val classHierarchy : MutableList<String> = mutableListOf(type.typeName)
) : NotSerializableException(msg) {
    constructor(type: Type) : this (type, "$type is not serializable")

    fun log(direction: String, logger: Logger) {
        logger.error("Serialization failed direction=\"$direction\", type=${type.typeName}\n" +
                "  msg=\"$msg\"\n" +
                "  Class chain = \"${classHierarchy.asReversed().joinToString(" -> ")}\"")

        // if debug is enabled print the string, the exception we allow to escape
        // will be printed into the log anyway by Artemis
        logger.debug("", cause)
    }
}