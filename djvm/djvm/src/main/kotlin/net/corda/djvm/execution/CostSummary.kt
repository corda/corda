package net.corda.djvm.execution

import net.corda.djvm.costing.RuntimeCostSummary

/**
 * A read-only copy of a the costs accumulated in an [IsolatedTask].
 *
 * @property allocations Number of bytes allocated.
 * @property invocations Number of invocations made.
 * @property jumps Number of jumps made (includes conditional branches that might not have been taken).
 * @property throws Number of throws made.
 */
data class CostSummary(
        val allocations: Long,
        val invocations: Long,
        val jumps: Long,
        val throws: Long
) {

    /**
     * Create a read-only cost summary object from an instance of [RuntimeCostSummary].
     */
    constructor(costs: RuntimeCostSummary) : this(
        costs.allocationCost.value,
        costs.invocationCost.value,
        costs.jumpCost.value,
        costs.throwCost.value
    )

    companion object {
        /**
         * A blank summary of costs.
         */
        val empty = CostSummary(0, 0, 0, 0)
    }

}