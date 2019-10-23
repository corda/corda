@file:JvmName("ExceptionUtils")
package net.corda.serialization.djvm.serializers

import net.corda.serialization.internal.amqp.AMQPNotSerializableException

/**
 * Utility function which helps tracking the path in the object graph when exceptions are thrown.
 * Since there might be a chain of nested calls it is useful to record which part of the graph caused an issue.
 * Path information is added to the message of the exception being thrown.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> ifThrowsAppend(strToAppendFn: () -> String, block: () -> T): T {
    try {
        return block()
    } catch (th: Throwable) {
        when (th) {
            is AMQPNotSerializableException -> th.classHierarchy.add(strToAppendFn())
            // Do not overwrite the message of these exceptions as it may be used.
            is ClassNotFoundException -> {}
            is NoClassDefFoundError -> {}
            else -> th.resetMessage("${strToAppendFn()} -> ${th.message}")
        }
        throw th
    }
}

/**
 * Not a public property so will have to use reflection
 */
private fun Throwable.resetMessage(newMsg: String) {
    val detailMessageField = Throwable::class.java.getDeclaredField("detailMessage")
    detailMessageField.isAccessible = true
    detailMessageField.set(this, newMsg)
}

/**
 * We currently only support deserialisation, and so we're going to need this.
 */
fun abortReadOnly(): Nothing = throw UnsupportedOperationException("Read Only!")