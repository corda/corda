package net.corda.djvm.execution

import net.corda.djvm.source.ClassSource
import java.io.PrintWriter

/**
 * An exception raised due to a runtime error inside a sandbox.
 *
 * @param message The detailed message describing the problem.
 * @property sandboxName The name of the sandbox in which the error occurred.
 * @property sandboxClass The class used as the sandbox entry point.
 * @property executionSummary A snapshot of the execution summary for the sandbox.
 * @property exception The inner exception, as it was raised within the sandbox.
 */
@Suppress("MemberVisibilityCanBePrivate")
class SandboxException(
        message: String,
        val sandboxName: String,
        val sandboxClass: ClassSource,
        val executionSummary: ExecutionSummary,
        val exception: Throwable
) : Exception(message, exception) {

    /**
     * Provide programmatic access to the stack trace information captured at the time the exception was thrown.
     */
    override fun getStackTrace(): Array<out StackTraceElement>? = exception.stackTrace

    /**
     * Print the stack trace information of the exception using a [PrintWriter].
     */
    override fun printStackTrace(writer: PrintWriter) = exception.printStackTrace(writer)

}
