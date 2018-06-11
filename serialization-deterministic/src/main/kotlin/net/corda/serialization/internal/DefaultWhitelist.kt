package net.corda.serialization.internal

import net.corda.core.serialization.SerializationWhitelist

/**
 * The DJVM does not need whitelisting, by definition.
 */
object DefaultWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<Any>> get() = emptyList()
}
