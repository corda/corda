package net.corda.core.cordapp

/**
 * Provides access to cordapp configuration independent of the configuration provider.
 */
interface CordappConfig {
    /**
     * Get the value of the configuration at "path" with type T. This is provider dependent and the default provider is
     * typesafe configuration.
     */
    fun <T> get(path: String): T?
}