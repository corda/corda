package net.corda.djvm.costing

/**
 * Cost metric to be used in a sandbox environment. The metric has a threshold and a mechanism for reporting violations.
 * The implementation assumes that each metric is tracked on a per-thread basis, i.e., that each sandbox runs on its own
 * thread.
 *
 * @param threshold The threshold for this metric.
 * @param errorMessage A delegate for generating an error message based on the thread it was reported from.
 */
class RuntimeCost(
        threshold: Long,
        errorMessage: (Thread) -> String
) : TypedRuntimeCost<Long>(
        0,
        { value: Long -> value > threshold },
        errorMessage
) {

    /**
     * Increment the accumulated cost by an integer.
     */
    fun increment(incrementBy: Int) = increment(incrementBy.toLong())

    /**
     * Increment the accumulated cost by a long integer.
     */
    fun increment(incrementBy: Long = 1) = incrementAndCheck { value -> value + incrementBy }

}
