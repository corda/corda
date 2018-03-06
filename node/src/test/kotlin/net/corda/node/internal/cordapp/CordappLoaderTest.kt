/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.cordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

@InitiatingFlow
class DummyFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
    }
}

@InitiatedBy(DummyFlow::class)
class LoaderTestFlow(unusedSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
    }
}

@SchedulableFlow
class DummySchedulableFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
    }
}

@StartableByRPC
class DummyRPCFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
    }
}

class CordappLoaderTest {
    private companion object {
        val testScanPackages = listOf("net.corda.node.internal.cordapp")
        val isolatedContractId = "net.corda.finance.contracts.isolated.AnotherDummyContract"
        val isolatedFlowName = "net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Initiator"
    }

    @Test
    fun `test that classes that aren't in cordapps aren't loaded`() {
        // Basedir will not be a corda node directory so the dummy flow shouldn't be recognised as a part of a cordapp
        val loader = CordappLoader.createDefault(Paths.get("."))
        assertThat(loader.cordapps)
                .hasSize(1)
                .contains(CordappLoader.coreCordapp)
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
        assertThat(actualCordapp.serializationWhitelists.first().javaClass.name).isEqualTo("net.corda.nodeapi.internal.serialization.DefaultWhitelist")
        assertThat(actualCordapp.jarPath).isEqualTo(isolatedJAR)
    }

    @Test
    fun `flows are loaded by loader`() {
        val loader = CordappLoader.createWithTestPackages(testScanPackages)

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
