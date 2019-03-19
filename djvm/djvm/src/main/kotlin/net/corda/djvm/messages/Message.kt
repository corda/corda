package net.corda.djvm.messages

import net.corda.djvm.analysis.SourceLocation
import net.corda.djvm.execution.SandboxException
import net.corda.djvm.rewiring.SandboxClassLoadingException

/**
 * A message of a given severity as it was reported during analysis or validation.
 *
 * @property message The message recorded.
 * @property severity The severity of the message.
 * @property location The location from which the message was recorded.
 */
data class Message(
        val message: String,
        val severity: Severity,
        val location: SourceLocation = SourceLocation()
) {

    override fun toString() = location.toString().let {
        when {
            it.isBlank() -> "[${severity.shortName}] $message"
            else -> "[${severity.shortName}] $it: $message"
        }
    }

    companion object {

        /**
         * Construct a message from a [Throwable] with an optional location.
         */
        fun fromThrowable(throwable: Throwable, location: SourceLocation = SourceLocation()): Message {
            return Message(getMessageFromException(throwable), Severity.ERROR, location)
        }

        /**
         * Get a clean description of the provided exception.
         */
        fun getMessageFromException(exception: Throwable): String {
            val exceptionType = when (exception::class.java.simpleName) {
                Exception::class.java.simpleName,
                SandboxClassLoadingException::class.java.simpleName,
                SandboxException::class.java.simpleName -> null
                else -> exception::class.java.simpleName.removeSuffix("Exception")
            }
            return exception.message?.let { message ->
                (exceptionType?.let { "$it: " } ?: "") + message
            } ?: exceptionType ?: "Unknown error"
        }

    }

}