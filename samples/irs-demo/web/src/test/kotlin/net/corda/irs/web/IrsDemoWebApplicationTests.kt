package net.corda.irs.web

import net.corda.core.messaging.CordaRPCOps
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest(properties = ["corda.host=localhost:12345", "corda.user=user", "corda.password=password", "liquibase.enabled=false"])
class IrsDemoWebApplicationTests {
    @MockBean
    lateinit var rpc: CordaRPCOps

    @Test
    fun contextLoads() {
    }
}
