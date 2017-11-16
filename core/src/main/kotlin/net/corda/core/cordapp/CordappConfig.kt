package net.corda.core.cordapp

class CordappConfigException(msg: String, e: Throwable) : Exception(msg, e)

/**
 * Provides access to cordapp configuration independent of the configuration provider.
 */
interface CordappConfig {
    /**
     * Get the value of the configuration at "path" with type T. This is provider dependent and the default provider is
     * typesafe configuration.
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    fun <T> get(path: String): T?
}