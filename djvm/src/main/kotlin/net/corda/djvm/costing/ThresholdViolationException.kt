package net.corda.djvm.costing

/**
 * Exception thrown when a sandbox threshold is violated. This will kill the current thread and consequently exit the
 * sandbox.
 *
 * @property message The description of the condition causing the problem.
 */
class ThresholdViolationException(
        override val message: String
) : ThreadDeath()
