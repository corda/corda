package net.corda.node.services.messaging

import net.corda.core.utilities.seconds

enum class LongPropertiesRegistry(private val defaultValue: Long) {
    BRIDGE_RETRY_INTERVAL_MS(5.seconds.toMillis()),
    ;

    fun getOrDefault(): Long {
        val systemValue = System.getProperty(name)
        return systemValue?.toLong() ?: defaultValue
    }

    /**
     * @return previous value if any
     */
    fun set(newValue: Long?): Long? {
        val systemValue: String? = System.getProperty(name)
        if(newValue == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, newValue.toString())
        }
        return systemValue?.toLong()
    }
}