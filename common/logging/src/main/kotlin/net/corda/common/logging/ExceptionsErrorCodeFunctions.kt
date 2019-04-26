package net.corda.common.logging

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.message.Message
import org.apache.logging.log4j.message.SimpleMessage
import java.util.*

fun Message.withErrorCodeFor(error: Throwable?, level: Level): Message {

    return when {
        error != null && level.isInRange(Level.FATAL, Level.WARN) -> CompositeMessage("$formattedMessage [errorCode=${error.errorCode()}, moreInformationAt=${error.errorCodeLocationUrl()}]", format, parameters, throwable)
        else -> this
    }
}

fun Throwable.errorCodeLocationUrl() = "https://errors.corda.net/${CordaVersion.platformEditionCode}/${CordaVersion.semanticVersion}/${errorCode()}"

fun Throwable.errorCode(hashedFields: (Throwable) -> Array<out Any?> = Throwable::defaultHashedFields): String {

    val hash = staticLocationBasedHash(hashedFields)
    return hash.toBase(36)
}

private fun Throwable.staticLocationBasedHash(hashedFields: (Throwable) -> Array<out Any?>, visited: Set<Throwable> = setOf(this)): Int {

    val cause = this.cause
    val fields = hashedFields.invoke(this)
    return when {
        cause != null && !visited.contains(cause) -> Objects.hash(*fields, cause.staticLocationBasedHash(hashedFields, visited + cause))
        else -> Objects.hash(*fields)
    }
}

private fun Int.toBase(base: Int): String = Integer.toUnsignedString(this, base)

private fun Array<StackTraceElement?>.customHashCode(maxElementsToConsider: Int = this.size): Int {

    return Arrays.hashCode(take(maxElementsToConsider).map { it?.customHashCode() ?: 0 }.toIntArray())
}

private fun StackTraceElement.customHashCode(hashedFields: (StackTraceElement) -> Array<out Any?> = StackTraceElement::defaultHashedFields): Int {

    return Objects.hash(*hashedFields.invoke(this))
}

private fun Throwable.defaultHashedFields(): Array<out Any?> {

    return arrayOf(this::class.java.name, stackTrace?.customHashCode(3) ?: 0)
}

private fun StackTraceElement.defaultHashedFields(): Array<out Any?> {

    return arrayOf(className, methodName)
}

private class CompositeMessage(message: String?, private val formatArg: String?, private val parameters: Array<out Any?>?, private val error: Throwable?) : SimpleMessage(message) {

    override fun getThrowable(): Throwable? = error

    override fun getParameters(): Array<out Any?>? = parameters

    override fun getFormat(): String? = formatArg
}