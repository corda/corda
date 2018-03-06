package net.corda.webserver

import org.jolokia.util.LogHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class JolokiaSlf4Adapter : LogHandler {
    val log: Logger = LoggerFactory.getLogger("org.jolokia")

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
