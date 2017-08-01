package net.corda.node

import net.corda.core.copyToDirectory
import net.corda.core.createDirectories
import net.corda.core.div
import net.corda.nodeapi.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

class CordappScanningNodeProcessTest {
    private companion object {
        val user = User("user1", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15100)
    }

    private val factory = NodeProcess.Factory()

    private val aliceConfig = NodeConfig(
            legalName = X500Name("CN=Alice Corp,O=Alice Corp,L=Madrid,C=ES"),
            p2pPort = port.andIncrement,
            rpcPort = port.andIncrement,
            webPort = port.andIncrement,
            extraServices = emptyList(),
            users = listOf(user)
    )

    @Test
    fun `CorDapp jar in plugins directory is scanned`() {
        // If the CorDapp jar does't exist then run the smokeTestClasses gradle task
        val cordappJar = Paths.get(javaClass.getResource("/trader-demo.jar").toURI())
        val pluginsDir = (factory.baseDirectory(aliceConfig) / "plugins").createDirectories()
        cordappJar.copyToDirectory(pluginsDir)

        factory.create(aliceConfig).use {
            it.connect().use {
                // If the CorDapp wasn't scanned then SellerFlow won't have been picked up as an RPC flow
                assertThat(it.proxy.registeredFlows()).contains("net.corda.traderdemo.flow.SellerFlow")
            }
        }
    }

    @Test
    fun `empty plugins directory`() {
        (factory.baseDirectory(aliceConfig) / "plugins").createDirectories()
        factory.create(aliceConfig).close()
    }
}
