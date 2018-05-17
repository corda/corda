package net.corda.core.flows

/**
 * An exception that may be identified with an ID. If an exception originates in a counter-flow this ID will be
 * propagated. This allows correlation of error conditions across different flows.
 */
interface IdentifiableException : ContextAware {
    /**
     * @return the ID of the error, or null if the error doesn't have it set (yet).
     */
    fun getErrorId(): Long? = null

    override val additionalContext: Map<String, Any>
        get() {
            return getErrorId()?.let { mapOf("errorId" to it) } ?: emptyMap()
        }
}
