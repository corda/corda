package net.corda.sandbox.costing

import net.corda.sandbox.execution.ExecutionProfile

/**
 * This class provides a summary of the accumulated costs for the runtime metrics that are being tracked. It also keeps
 * track of applicable thresholds and will terminate sandbox execution if any of them are breached.
 *
 * The costs are tracked on a per-thread basis, and thus, are isolated for each sandbox. Each sandbox live on its own
 * thread.
 */
class RuntimeCostSummary private constructor(
        allocationCostThreshold: Long,
        jumpCostThreshold: Long,
        invocationCostThreshold: Long,
        throwCostThreshold: Long
) {

    /**
     * Create a new runtime cost tracker based on an execution profile.
     */
    constructor(profile: ExecutionProfile) : this(
            allocationCostThreshold = profile.allocationCostThreshold,
            jumpCostThreshold = profile.jumpCostThreshold,
            invocationCostThreshold = profile.invocationCostThreshold,
            throwCostThreshold = profile.throwCostThreshold
    )

    /**
     * Accumulated cost of memory allocations.
     */
    val allocationCost = RuntimeCost(allocationCostThreshold) {
        "Sandbox [${it.name}] terminated due to over-allocation"
    }

    /**
     * Accumulated cost of jump operations.
     */
    val jumpCost = RuntimeCost(jumpCostThreshold) {
        "Sandbox [${it.name}] terminated due to excessive use of looping"
    }

    /**
     * Accumulated cost of method invocations.
     */
    val invocationCost = RuntimeCost(invocationCostThreshold) {
        "Sandbox [${it.name}] terminated due to excessive method calling"
    }

    /**
     * Accumulated cost of throw operations.
     */
    val throwCost = RuntimeCost(throwCostThreshold) {
        "Sandbox [${it.name}] terminated due to excessive exception throwing"
    }

    /**
     * Get a summary of the accumulated costs.
     */
    val summary: Map<String, Long>
        get() = mapOf(
                "allocations" to allocationCost.value,
                "invocations" to invocationCost.value,
                "jumps" to jumpCost.value,
                "throws" to throwCost.value
        )

}
