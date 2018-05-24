package net.corda.sandbox.rewiring

import net.corda.sandbox.messages.Message
import net.corda.sandbox.messages.MessageCollection
import net.corda.sandbox.references.ClassHierarchy

/**
 * Exception raised if the sandbox class loader for some reason fails to load one or more classes.
 *
 * @property messages A collection of the problem(s) that caused the class loading to fail.
 * @property classes The class hierarchy at the time the exception was raised.
 */
class SandboxClassLoadingException(
        val messages: MessageCollection,
        val classes: ClassHierarchy
) : Exception("Failed to load class") {

    /**
     * The detailed description of the exception.
     */
    override val message: String?
        get() = StringBuilder().apply {
            appendln(super.message)
            for (message in messages.sorted().map(Message::toString).distinct()) {
                appendln(" - $message")
            }
        }.toString().trimEnd('\r', '\n')

}
