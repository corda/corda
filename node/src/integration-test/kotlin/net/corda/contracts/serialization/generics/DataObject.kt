package net.corda.contracts.serialization.generics

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class DataObject(val value: Long) : Comparable<DataObject> {
    override fun toString(): String {
        return "$value data points"
    }

    override fun compareTo(other: DataObject): Int {
        return value.compareTo(other.value)
    }
}
