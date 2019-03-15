package net.corda.djvm.execution

/**
 * The summary of the execution of a [java.util.function.Function] in a sandbox.
 *
 * @property result The outcome of the sandboxed operation.
 * @see ExecutionSummary
 */
class ExecutionSummaryWithResult<out TResult>(
        val result: TResult? = null,
        costs: CostSummary = CostSummary.empty
) : ExecutionSummary(costs)
