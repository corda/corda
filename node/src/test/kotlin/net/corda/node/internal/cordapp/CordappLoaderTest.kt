package net.corda.node.internal.cordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

@InitiatingFlow
class DummyFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

@InitiatedBy(DummyFlow::class)
class LoaderTestFlow(@Suppress("UNUSED_PARAMETER") unusedSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

@SchedulableFlow
class DummySchedulableFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

@StartableByRPC
class DummyRPCFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

class CordappLoaderTest {
    private companion object {
        const val testScanPackage = "net.corda.node.internal.cordapp"
        const val isolatedContractId = "net.corda.finance.contracts.isolated.AnotherDummyContract"
        const val isolatedFlowName = "net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Initiator"
    }

    @Test
    fun `test that classes that aren't in cordapps aren't loaded`() {
        // Basedir will not be a corda node directory so the dummy flow shouldn't be recognised as a part of a cordapp
        val loader = CordappLoader.createDefault(Paths.get("."))
        assertThat(loader.cordapps).containsOnly(CordappLoader.coreCordapp)
    }

    @Test
    fun `isolated JAR contains a CorDapp with a contract and plugin`() {
        val isolatedJAR = CordappLoaderTest::class.java.getResource("isolated.jar")!!
        val loader = CordappLoader.createDevMode(listOf(isolatedJAR))

        val actual = loader.cordapps.toTypedArray()
        assertThat(actual).hasSize(2)

        val actualCordapp = actual.single { it != CordappLoader.coreCordapp }
        assertThat(actualCordapp.contractClassNames).isEqualTo(listOf(isolatedContractId))
        assertThat(actualCordapp.initiatedFlows.single().name).isEqualTo("net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Acceptor")
        assertThat(actualCordapp.rpcFlows).isEmpty()
        assertThat(actualCordapp.schedulableFlows).isEmpty()
        assertThat(actualCordapp.services).isEmpty()
        assertThat(actualCordapp.serializationWhitelists).hasSize(1)
        assertThat(actualCordapp.serializationWhitelists.first().javaClass.name).isEqualTo("net.corda.serialization.internal.DefaultWhitelist")
        assertThat(actualCordapp.jarPath).isEqualTo(isolatedJAR)
    }

    @Test
    fun `flows are loaded by loader`() {
        val loader = CordappLoader.createWithTestPackages(listOf(testScanPackage))

        val actual = loader.cordapps.toTypedArray()
        // One core cordapp, one cordapp from this source tree, and two others due to identically named locations
        // in resources and the non-test part of node. This is okay due to this being test code. In production this
        // cannot happen. In gradle it will also pick up the node jar. 
        assertThat(actual.size == 4 || actual.size == 5).isTrue()

        val actualCordapp = actual.single { !it.initiatedFlows.isEmpty() }
        assertThat(actualCordapp.initiatedFlows).first().hasSameClassAs(DummyFlow::class.java)
        assertThat(actualCordapp.rpcFlows).first().hasSameClassAs(DummyRPCFlow::class.java)
        assertThat(actualCordapp.schedulableFlows).first().hasSameClassAs(DummySchedulableFlow::class.java)
    }

    @Test
    fun `duplicate packages are ignored`() {
        val loader = CordappLoader.createWithTestPackages(listOf(testScanPackage, testScanPackage))
        val cordapps = loader.cordapps.filter { LoaderTestFlow::class.java in it.initiatedFlows }
        assertThat(cordapps).hasSize(1)
    }

    @Test
    fun `sub-packages are ignored`() {
        val loader = CordappLoader.createWithTestPackages(listOf("net.corda", testScanPackage))
        val cordapps = loader.cordapps.filter { LoaderTestFlow::class.java in it.initiatedFlows }
        assertThat(cordapps).hasSize(1)
    }

    // This test exists because the appClassLoader is used by serialisation and we need to ensure it is the classloader
    // being used internally. Later iterations will use a classloader per cordapp and this test can be retired.
    @Test
    fun `cordapp classloader can load cordapp classes`() {
        val isolatedJAR = CordappLoaderTest::class.java.getResource("isolated.jar")!!
        val loader = CordappLoader.createDevMode(listOf(isolatedJAR))

        loader.appClassLoader.loadClass(isolatedContractId)
        loader.appClassLoader.loadClass(isolatedFlowName)
    }
}
