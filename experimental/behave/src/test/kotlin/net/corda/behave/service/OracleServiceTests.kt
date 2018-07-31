package net.corda.behave.service

import net.corda.behave.service.database.Oracle11gService
import net.corda.behave.service.database.Oracle12cService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test

class OracleServiceTests {

    @Test
    fun `Oracle 11g can be started and stopped`() {
        val service = Oracle11gService("test-oracle-11g", 1521, "oracle")
        val didStart = service.start()
        service.stop()
        assertThat(didStart).isTrue()
    }

    @Ignore
    @Test
    fun `Oracle 12c can be started and stopped`() {
        val service = Oracle12cService("test-oracle-12c", 1521, "oracle")
        val didStart = service.start()
        service.stop()
        assertThat(didStart).isTrue()
    }
}