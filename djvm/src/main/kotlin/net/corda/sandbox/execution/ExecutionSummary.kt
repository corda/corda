package net.corda.sandbox.execution

/**
 * The summary of the execution of a [SandboxedRunnable] in a sandbox. This class has no representation of the outcome,
 * and is typically used when there has been a pre-mature exit from the sandbox, for instance, if an exception was
 * thrown.
 *
 * @property costs The costs accumulated when running the sandboxed code.
 */
open class ExecutionSummary(
        val costs: Map<String, Long> = emptyMap()
)
