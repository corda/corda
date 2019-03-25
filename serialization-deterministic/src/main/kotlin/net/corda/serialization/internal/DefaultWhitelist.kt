package net.corda.serialization.internal

import net.corda.core.serialization.SerializationWhitelist

/**
 * The DJVM does not need whitelisting, by definition.
 */
object DefaultWhitelist : SerializationWhitelist {
    override val whitelist = listOf(
            java.lang.Comparable::class.java    // required for forwards compatibility with default whitelist
    )
}
