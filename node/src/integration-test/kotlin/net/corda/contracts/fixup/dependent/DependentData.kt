package net.corda.contracts.fixup.dependent

data class DependentData(val value: Long) : Comparable<DependentData> {
    override fun toString(): String {
        return "$value beans"
    }

    override fun compareTo(other: DependentData): Int {
        return value.compareTo(other.value)
    }
}
