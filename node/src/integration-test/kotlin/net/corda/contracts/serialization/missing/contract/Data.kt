package net.corda.contracts.serialization.missing.contract

data class Data(val value: Long) : Comparable<Data> {
    override fun toString(): String {
        return "$value bobbins"
    }

    override fun compareTo(other: Data): Int {
        return value.compareTo(other.value)
    }
}
