package net.corda.djvm.utilities

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Get logger for provided class [T].
 */
inline fun <reified T : Any> loggerFor(): Logger =
        LoggerFactory.getLogger(T::class.java)
