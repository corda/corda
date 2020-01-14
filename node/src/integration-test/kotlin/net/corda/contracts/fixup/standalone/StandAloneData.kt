package net.corda.contracts.fixup.standalone

data class StandAloneData(val value: Long) : Comparable<StandAloneData> {
    override fun toString(): String {
        return "$value pods"
    }

    override fun compareTo(other: StandAloneData): Int {
        return value.compareTo(other.value)
    }
}
