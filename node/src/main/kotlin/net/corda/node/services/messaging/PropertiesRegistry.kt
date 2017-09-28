package net.corda.node.services.messaging

import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds


private interface PropertiesRegistry<T> {
    val name: String

    val defaultValue: T

    fun fromString(value: String?): T?

    fun formatAsString(value: T): String

    fun getOrDefault(): T {
        val systemValue = System.getProperty(name)
        return fromString(systemValue) ?: defaultValue
    }

    /**
     * @return previous value if any
     */
    fun set(newValue: T?): T? {
        val systemValue: String? = System.getProperty(name)
        if(newValue == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, formatAsString(newValue))
        }
        return fromString(systemValue)
    }
}

enum class LongPropertiesRegistry(override val defaultValue: Long) : PropertiesRegistry<Long> {
    BRIDGE_RETRY_INTERVAL_MS(5.seconds.toMillis()),
    BRIDGE_MAX_RETRY_INTERVAL_MS(3.minutes.toMillis()),
    ;

    override fun fromString(value: String?): Long? = value?.toLong()

    override fun formatAsString(value: Long): String = value.toString()
}

enum class DoublePropertiesRegistry(override val defaultValue: Double) : PropertiesRegistry<Double> {
    BRIDGE_RETRY_INTERVAL_MULTIPLIER(1.5),
    ;

    override fun fromString(value: String?): Double? = value?.toDouble()

    override fun formatAsString(value: Double): String = value.toString()
}