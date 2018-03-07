package net.corda.webserver

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.jolokia.util.LogHandler

/**
 * This also exists in node, because depends on jolokia
 **/
class JolokiaSlf4jAdapter : LogHandler {
    companion object {
        val log: Logger = LoggerFactory.getLogger("org.jolokia")
    }

    override fun error(message: String?, t: Throwable?) {
        if (message != null) {
            if (t != null) {
                log.error(message, t)
            } else {
                log.error(message)
            }
        } else if (t != null) {
            log.error("Exception without a comment", t)
        }
    }

    override fun debug(message: String?) {
        if (message != null) {
            log.debug(message)
        }
    }

    override fun info(message: String?) {
        if (message != null) {
            log.info(message)
        }
    }
}
