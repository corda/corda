package net.corda.contracts.multiple.vulnerable

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class MutableDataObject(var value: Long) : Comparable<MutableDataObject> {
    override fun toString(): String {
        return "$value data points"
    }

    override fun compareTo(other: MutableDataObject): Int {
        return value.compareTo(other.value)
    }
}
