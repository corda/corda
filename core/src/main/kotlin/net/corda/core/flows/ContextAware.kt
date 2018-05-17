package net.corda.core.flows

/**
 * Allows types to specify additional context. Useful for exceptions or events.
 */
interface ContextAware {
    val additionalContext: Map<String, Any>
        get() = emptyMap()
}