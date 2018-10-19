package sandbox.net.corda.djvm.rules

/**
 * Exception thrown when a sandbox rule is violated at runtime.
 * This will kill the current thread and consequently exit the
 * sandbox.
 *
 * @property message The description of the condition causing the problem.
 */
class RuleViolationError(override val message: String?) : ThreadDeath()