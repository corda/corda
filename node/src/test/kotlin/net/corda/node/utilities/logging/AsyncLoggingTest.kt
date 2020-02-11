package net.corda.node.utilities.logging

import org.junit.Test
import kotlin.test.assertTrue


class AsyncLoggingTest {
    @Test(timeout=300_000)
	fun `async logging is configured`() {
        assertTrue(AsyncLoggerContextSelectorNoThreadLocal.isSelected())
    }
}