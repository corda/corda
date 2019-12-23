package net.corda.contracts.djvm.whitelist

import net.corda.core.serialization.SerializationWhitelist

data class WhitelistData(val value: Long) : Comparable<WhitelistData> {
    override fun compareTo(other: WhitelistData): Int {
        return value.compareTo(other.value)
    }

    override fun toString(): String = "$value things"
}

class Whitelist : SerializationWhitelist {
    override val whitelist = listOf(WhitelistData::class.java)
}
