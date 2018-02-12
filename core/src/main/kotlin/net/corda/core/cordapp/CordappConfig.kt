package net.corda.core.cordapp

import net.corda.core.DoNotImplement

/**
 * Provides access to cordapp configuration independent of the configuration provider.
 */
@DoNotImplement
interface CordappConfig {
    /**
     * Check if a config exists at path
     */
    fun exists(path: String): Boolean

    /**
     * Get the value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    fun get(path: String): Any

    /**
     * Get the int value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    fun getInt(path: String): Int

    /**
     * Get the long value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    fun getLong(path: String): Long

    /**
     * Get the float value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    fun getFloat(path: String): Float

    /**
     * Get the double value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    fun getDouble(path: String): Double

    /**
     * Get the number value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    fun getNumber(path: String): Number

    /**
     * Get the string value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    fun getString(path: String): String

    /**
     * Get the boolean value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    fun getBoolean(path: String): Boolean
}