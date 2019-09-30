package net.corda.node.utilities

import org.slf4j.LoggerFactory

/**
 * Log error message and terminate the process. This might not clean up resources and could leave
 * the system in a messy state.
 */
@Synchronized
fun errorAndTerminate(message: String, e: Throwable?) {
    try {
        val log = LoggerFactory.getLogger("errorAndTerminate")
        log.error(message, e)
    }
    finally {
        Runtime.getRuntime().halt(1)
    }
}