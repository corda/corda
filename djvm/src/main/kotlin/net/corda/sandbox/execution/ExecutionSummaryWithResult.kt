package net.corda.sandbox.execution

/**
 * The summary of the execution of a [SandboxedRunnable] in a sandbox.
 *
 * @property result The outcome of the sandboxed operation.
 * @see ExecutionSummary
 */
class ExecutionSummaryWithResult<out TResult>(
        val result: TResult? = null,
        costs: Map<String, Long> = emptyMap()
) : ExecutionSummary(costs)
