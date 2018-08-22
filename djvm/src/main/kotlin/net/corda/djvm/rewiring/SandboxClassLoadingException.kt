package net.corda.djvm.rewiring

import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.messages.Message
import net.corda.djvm.messages.MessageCollection
import net.corda.djvm.references.ClassHierarchy
import net.corda.djvm.references.EntityReference

/**
 * Exception raised if the sandbox class loader for some reason fails to load one or more classes.
 *
 * @property messages A collection of the problem(s) that caused the class loading to fail.
 * @property classes The class hierarchy at the time the exception was raised.
 * @property context The context in which the analysis took place.
 * @property classOrigins Map of class origins. The resulting set represents the types referencing the class in question.
 */
class SandboxClassLoadingException(
        private val context: AnalysisContext,
        val messages: MessageCollection = context.messages,
        val classes: ClassHierarchy = context.classes,
        val classOrigins: Map<String, Set<EntityReference>> = context.classOrigins
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
