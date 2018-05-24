package net.corda.sandbox.execution

/**
 * The execution profile of a [SandboxedRunnable] when run in a sandbox.
 *
 * @property allocationCostThreshold The threshold placed on allocations.
 * @property invocationCostThreshold The threshold placed on invocations.
 * @property jumpCostThreshold The threshold placed on jumps.
 * @property throwCostThreshold The threshold placed on throw statements.
 */
enum class ExecutionProfile(
        val allocationCostThreshold: Long = Long.MAX_VALUE,
        val invocationCostThreshold: Long = Long.MAX_VALUE,
        val jumpCostThreshold: Long = Long.MAX_VALUE,
        val throwCostThreshold: Long = Long.MAX_VALUE
) {

    // TODO Define sensible runtime thresholds and make further improvements to instrumentation.

    /**
     * Profile with a set of default thresholds.
     */
    DEFAULT(
            allocationCostThreshold = 1024 * 1024 * 1024,
            invocationCostThreshold = 1_000_000,
            jumpCostThreshold = 1_000_000,
            throwCostThreshold = 1_000_000
    ),

    /**
     * Profile where no limitations have been imposed on the sandbox.
     */
    UNLIMITED(),

    /**
     * Profile where throw statements have been disallowed.
     */
    DISABLE_THROWS(throwCostThreshold = 0),

    /**
     * Profile where branching statements have been disallowed.
     */
    DISABLE_BRANCHING(jumpCostThreshold = 0)

}
