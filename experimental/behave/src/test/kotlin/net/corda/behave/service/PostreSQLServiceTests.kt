package net.corda.behave.service

import net.corda.behave.service.database.PostgreSQLService
import net.corda.behave.service.database.SqlServerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test

class PostreSQLServiceTests {

    @Ignore
    @Test
    fun `postgres can be started and stopped`() {
        val service = PostgreSQLService("test-postgres", 12345, "postgres")
        val didStart = service.start()
        service.stop()
        assertThat(didStart).isTrue()
    }

}