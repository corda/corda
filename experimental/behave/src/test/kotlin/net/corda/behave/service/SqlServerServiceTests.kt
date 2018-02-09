package net.corda.behave.service

import net.corda.behave.service.database.SqlServerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test

class SqlServerServiceTests {

    @Ignore
    @Test
    fun `sql server can be started and stopped`() {
        val service = SqlServerService("test-mssql", 12345, "S0meS3cretW0rd")
        val didStart = service.start()
        service.stop()
        assertThat(didStart).isTrue()
    }

}