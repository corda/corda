/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.copyTo
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.toLedgerTransaction
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SerializationFactory
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.internal.*
import net.corda.testing.services.MockAttachmentStorage
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.net.URLClassLoader
import kotlin.test.assertFailsWith

class AttachmentLoadingTests : IntegrationTest() {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val attachments = MockAttachmentStorage()
    private val provider = CordappProviderImpl(CordappLoader.createDevMode(listOf(isolatedJAR)), MockCordappConfigProvider(), attachments, testNetworkParameters().whitelistedContractImplementations)
    private val cordapp get() = provider.cordapps.first()
    private val attachmentId get() = provider.getCordappAttachmentId(cordapp)!!
    private val appContext get() = provider.getAppContext(cordapp)

    private companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(DUMMY_BANK_A_NAME.toDatabaseSchemaName(), DUMMY_BANK_B_NAME.toDatabaseSchemaName(),
                DUMMY_NOTARY_NAME.toDatabaseSchemaName())

        private val logger = contextLogger()
        val isolatedJAR = AttachmentLoadingTests::class.java.getResource("isolated.jar")!!
        const val ISOLATED_CONTRACT_ID = "net.corda.finance.contracts.isolated.AnotherDummyContract"

        val bankAName = CordaX500Name("BankA", "Zurich", "CH")
        val bankBName = CordaX500Name("BankB", "Zurich", "CH")
        val flowInitiatorClass: Class<out FlowLogic<*>> =
                Class.forName("net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Initiator", true, URLClassLoader(arrayOf(isolatedJAR)))
                        .asSubclass(FlowLogic::class.java)
        val DUMMY_BANK_A = TestIdentity(DUMMY_BANK_A_NAME, 40).party
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        private fun DriverDSL.createTwoNodes(): List<NodeHandle> {
            return listOf(
                    startNode(providedName = bankAName),
                    startNode(providedName = bankBName)
            ).transpose().getOrThrow()
        }

        private fun DriverDSL.installIsolatedCordappTo(nodeName: CordaX500Name) {
            // Copy the app jar to the first node. The second won't have it.
            val path = (baseDirectory(nodeName) / "cordapps").createDirectories() / "isolated.jar"
            logger.info("Installing isolated jar to $path")
            isolatedJAR.openStream().use { it.copyTo(path) }
        }
    }

    private val services = object : ServicesForResolution {
        override fun loadState(stateRef: StateRef): TransactionState<*> = throw NotImplementedError()
        override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> = throw NotImplementedError()
        override val identityService = rigorousMock<IdentityService>().apply {
            doReturn(null).whenever(this).partyFromKey(DUMMY_BANK_A.owningKey)
        }
        override val attachments: AttachmentStorage get() = this@AttachmentLoadingTests.attachments
        override val cordappProvider: CordappProvider get() = this@AttachmentLoadingTests.provider
        override val networkParameters: NetworkParameters = testNetworkParameters()
    }

    @Test
    fun `test a wire transaction has loaded the correct attachment`() {
        val appClassLoader = appContext.classLoader
        val contractClass = appClassLoader.loadClass(ISOLATED_CONTRACT_ID).asSubclass(Contract::class.java)
        val generateInitialMethod = contractClass.getDeclaredMethod("generateInitial", PartyAndReference::class.java, Integer.TYPE, Party::class.java)
        val contract = contractClass.newInstance()
        val txBuilder = generateInitialMethod.invoke(contract, DUMMY_BANK_A.ref(1), 1, DUMMY_NOTARY) as TransactionBuilder
        val context = SerializationFactory.defaultFactory.defaultContext.withClassLoader(appClassLoader)
        val ledgerTx = txBuilder.toLedgerTransaction(services, context)
        contract.verify(ledgerTx)

        val actual = ledgerTx.attachments.first()
        val expected = attachments.openAttachment(attachmentId)!!
        assertEquals(expected, actual)
    }

    @Test
    fun `test that attachments retrieved over the network are not used for code`() {
        withoutTestSerialization {
            driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
                installIsolatedCordappTo(bankAName)
                val (bankA, bankB) = createTwoNodes()
                assertFailsWith<CordaRuntimeException>("Party C=CH,L=Zurich,O=BankB rejected session request: Don't know net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Initiator") {
                    bankA.rpc.startFlowDynamic(flowInitiatorClass, bankB.nodeInfo.legalIdentities.first()).returnValue.getOrThrow()
                }
            }
            Unit
        }
    }

    @Test
    fun `tests that if the attachment is loaded on both sides already that a flow can run`() {
        withoutTestSerialization {
            driver {
                installIsolatedCordappTo(bankAName)
                installIsolatedCordappTo(bankBName)
                val (bankA, bankB) = createTwoNodes()
                bankA.rpc.startFlowDynamic(flowInitiatorClass, bankB.nodeInfo.legalIdentities.first()).returnValue.getOrThrow()
            }
        }
    }
}
