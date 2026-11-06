package net.corda.nodeapi.internal.persistence

import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.slf4j.Logger
import java.util.logging.Level

class Slf4jLiquibaseLoggerTest {
    private val slf4jLogger = mock<Logger>()
    private val liquibaseLogger = Slf4jLiquibaseLogger(slf4jLogger)

    @Test(timeout = 300_000)
    fun `messages logged at OFF are discarded`() {
        liquibaseLogger.log(Level.OFF, "Analytics is disabled, because it was turned off by Liquibase.", null)

        verifyNoInteractions(slf4jLogger)
    }

    @Test(timeout = 300_000)
    fun `levels are mapped to the equivalent slf4j level`() {
        val e = RuntimeException()

        liquibaseLogger.log(Level.FINEST, "finest", e)
        liquibaseLogger.log(Level.FINE, "fine", e)
        liquibaseLogger.log(Level.INFO, "info", e)
        liquibaseLogger.log(Level.WARNING, "warning", e)
        liquibaseLogger.log(Level.SEVERE, "severe", e)

        verify(slf4jLogger).trace("finest", e)
        verify(slf4jLogger).debug("fine", e)
        verify(slf4jLogger).info("info", e)
        verify(slf4jLogger).warn("warning", e)
        verify(slf4jLogger).error("severe", e)
        verify(slf4jLogger, never()).error("warning", e)
    }
}
