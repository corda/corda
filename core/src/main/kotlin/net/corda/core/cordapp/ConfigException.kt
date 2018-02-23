package net.corda.core.cordapp

/**
 * Thrown if an exception occurs in accessing or parsing cordapp configuration
 */
class CordappConfigException(msg: String, e: Throwable) : Exception(msg, e)