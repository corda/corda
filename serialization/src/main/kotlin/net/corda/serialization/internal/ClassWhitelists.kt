package net.corda.serialization.internal

import net.corda.core.serialization.ClassWhitelist

interface MutableClassWhitelist : ClassWhitelist {
    fun add(entry: Class<*>)
}

object AllWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = true
}
