package net.corda.contracts.serialization.missing

/**
 * This class REQUIRES a custom serializer because its
 * constructor parameter cannot be mapped to a property
 * automatically. THIS IS DELIBERATE!
 */
class CustomData(initialValue: Long) : Comparable<CustomData> {
    // DO NOT MOVE THIS PROPERTY INTO THE CONSTRUCTOR!
    val value: Long = initialValue

    override fun toString(): String {
        return "$value bobbins"
    }

    override fun compareTo(other: CustomData): Int {
        return value.compareTo(other.value)
    }
}
