package net.corda.nodeapi.internal.persistence

import org.slf4j.Logger
import java.util.logging.Level

/**
 * Slf4j logger for Liquibase
 * Captures logging from Liquibase that would otherwise go to stderr.
 */
class Slf4jLiquibaseLogger(private val logger: Logger) : liquibase.logging.core.AbstractLogger() {

    companion object {
        private val TRACE_THRESHOLD = Level.FINEST.intValue()
        private val DEBUG_THRESHOLD = Level.FINE.intValue()
        private val INFO_THRESHOLD = Level.INFO.intValue()
        private val WARN_THRESHOLD = Level.WARNING.intValue()
    }

    override fun log(level: Level, message: String?, e: Throwable?) {
        val levelValue = level.intValue()
        if (levelValue <= TRACE_THRESHOLD) {
            logger.trace(message, e)
        } else if (levelValue <= DEBUG_THRESHOLD) {
            logger.debug(message, e)
        } else if (levelValue <= INFO_THRESHOLD) {
            logger.info(message, e)
        } else if (levelValue <= WARN_THRESHOLD) {
            logger.warn(message, e)
        } else {
            logger.error(message, e)
        }
    }

    override fun severe(message: String?) {
        logger.error(message)
    }

    override fun severe(message: String?, e: Throwable?) {
        logger.error(message, e)
    }

    override fun warning(message: String?) {
        logger.warn(message)
    }

    override fun warning(message: String?, e: Throwable?) {
        logger.warn(message, e)
    }

    override fun info(message: String?) {
        logger.info(message)
    }

    override fun info(message: String?, e: Throwable?) {
        logger.info(message, e)
    }

    override fun config(message: String?) {
        logger.info(message)
    }

    override fun config(message: String?, e: Throwable?) {
        logger.info(message, e)
    }

    override fun fine(message: String?) {
        logger.debug(message)
    }

    override fun fine(message: String?, e: Throwable?) {
        logger.debug(message, e)
    }

}