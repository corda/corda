package net.corda.node.cordapp

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.node.internal.cordapp.CordappLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

@InitiatingFlow
class DummyFlow : FlowLogic<Unit>() {
    override fun call() { }
}

@InitiatedBy(DummyFlow::class)
class LoaderTestFlow(unusedSession: FlowSession) : FlowLogic<Unit>() {
    override fun call() { }
}

class CordappLoaderTest {
    @Test
    fun `test that classes that aren't in cordapps aren't loaded`() {
        // Basedir will not be a corda node directory so the dummy flow shouldn't be recognised as a part of a cordapp
        val loader = CordappLoader.createDefault(Paths.get("."))
        assertThat(loader.cordapps).isEmpty()
    }

    @Test
    fun `test that classes that are in a cordapp are loaded`() {
        val loader = CordappLoader.createDevMode("net.corda.node.cordapp")
        val initiatedFlows = loader.cordapps.first().initiatedFlows
        val expectedClass = loader.appClassLoader.loadClass("net.corda.node.cordapp.LoaderTestFlow").asSubclass(FlowLogic::class.java)
        assertThat(initiatedFlows).contains(expectedClass)
    }

    @Test
    fun `isolated JAR contains a CorDapp with a contract and plugin`() {
        val isolatedJAR = CordappLoaderTest::class.java.getResource("isolated.jar")!!
        val loader = CordappLoader.createDevMode(listOf(isolatedJAR))

        val actual = loader.cordapps.toTypedArray()
        assertThat(actual).hasSize(1)

        val actualCordapp = actual.first()
        assertThat(actualCordapp.contractClassNames).isEqualTo(listOf("net.corda.finance.contracts.isolated.AnotherDummyContract"))
        assertThat(actualCordapp.initiatedFlows).isEmpty()
        assertThat(actualCordapp.rpcFlows).contains(loader.appClassLoader.loadClass("net.corda.core.flows.ContractUpgradeFlow\$Initiate").asSubclass(FlowLogic::class.java))
        assertThat(actualCordapp.services).isEmpty()
        assertThat(actualCordapp.plugins).hasSize(1)
        assertThat(actualCordapp.plugins.first().javaClass.name).isEqualTo("net.corda.finance.contracts.isolated.DummyPlugin")
        assertThat(actualCordapp.jarPath).isEqualTo(isolatedJAR)
    }
}