package net.corda.djvm.validation

import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.messages.Message
import net.corda.djvm.messages.Severity

/**
 * Helper functions used to check constraints and consistently report status and messages back to the user.
 *
 * @property analysisContext A reference to the current analysis context, which is used to provide information about the
 * location from where a message gets generated and reported.
 */
open class ConstraintProvider(
        private val analysisContext: AnalysisRuntimeContext
) {

    /**
     * Indicate to user that a sandbox rule has been violated.
     *
     * @param message A detailed message describing which and how a rule was violated.
     */
    fun fail(message: String) = DraftMessage(message, analysisContext, Severity.ERROR)

    /**
     * Record a warning to be presented to the user.
     */
    fun warn(message: String) = DraftMessage(message, analysisContext, Severity.WARNING)

    /**
     * Record an informational message to be presented to the user.
     */
    fun inform(message: String) = DraftMessage(message, analysisContext, Severity.INFORMATIONAL)

    /**
     * Record a trace message to be presented to the user.
     */
    fun trace(message: String) = DraftMessage(message, analysisContext, Severity.TRACE)

    /**
     * Construct for representing a rule constraint. If the condition is true, the message gets recorded.
     */
    infix fun DraftMessage.given(condition: Boolean) {
        if (condition) {
            always()
        }
    }

    /**
     * Construct for representing a rule constraint.
     */
    private fun DraftMessage.always() {
        context.messages.add(Message(message, severity, context.location))
    }

    /**
     * A prepared message that gets reported to the user provided a certain pre-condition is met.
     *
     * @property message A textual description.
     * @property context The analysis context at the time the message was generated.
     * @property severity The severity of the message, e.g., [Severity.INFORMATIONAL] or [Severity.ERROR].
     */
    data class DraftMessage(
            val message: String,
            val context: AnalysisRuntimeContext,
            val severity: Severity
    )

}
