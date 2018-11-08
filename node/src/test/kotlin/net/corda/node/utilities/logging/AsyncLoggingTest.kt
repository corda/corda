package net.corda.node.utilities.logging

import org.junit.Test
import kotlin.test.assertTrue


class AsyncLoggingTest {
    @Test
    fun `async logging is configured`() {
        assertTrue(AsyncLoggerContextSelectorNoThreadLocal.isSelected())
    }
}