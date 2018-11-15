package net.corda.node.services

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.InputStreamAndHash
import net.corda.core.internal.toLedgerTransaction
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.ColumnPredicate
import net.corda.core.node.services.vault.EqualityComparisonOperator
import net.corda.core.serialization.SerializationFactory
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.VersionInfo
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.services.config.MB
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.MockCordappConfigProvider
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.withoutTestSerialization
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.services.MockAttachmentStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.net.URLClassLoader
import kotlin.test.assertFailsWith

class AttachmentLoadingTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val attachments = MockAttachmentStorage()
    private val provider = CordappProviderImpl(JarScanningCordappLoader.fromJarUrls(listOf(isolatedJAR), VersionInfo.UNKNOWN), MockCordappConfigProvider(), attachments).apply {
        start(testNetworkParameters().whitelistedContractImplementations)
    }
    private val cordapp get() = provider.cordapps.first()
    private val attachmentId get() = provider.getCordappAttachmentId(cordapp)!!
    private val appContext get() = provider.getAppContext(cordapp)

    private companion object {
        val isolatedJAR = AttachmentLoadingTests::class.java.getResource("isolated.jar")!!
        const val ISOLATED_CONTRACT_ID = "net.corda.finance.contracts.isolated.AnotherDummyContract"

        val bankAName = CordaX500Name("BankA", "Zurich", "CH")
        val bankBName = CordaX500Name("BankB", "Zurich", "CH")
        val flowInitiatorClass: Class<out FlowLogic<*>> =
                Class.forName("net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Initiator", true, URLClassLoader(arrayOf(isolatedJAR)))
                        .asSubclass(FlowLogic::class.java)
        val DUMMY_BANK_A = TestIdentity(DUMMY_BANK_A_NAME, 40).party
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
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
            driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = emptySet())) {
                val bankA = startNode(providedName = bankAName, additionalCordapps = cordappsForPackages("net.corda.finance.contracts.isolated")).getOrThrow()
                val bankB = startNode(providedName = bankBName, additionalCordapps = cordappsForPackages("net.corda.finance.contracts.isolated")).getOrThrow()
                assertFailsWith<CordaRuntimeException>("Party C=CH,L=Zurich,O=BankB rejected session request: Don't know net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Initiator") {
                    bankA.rpc.startFlowDynamic(flowInitiatorClass, bankB.nodeInfo.legalIdentities.first()).returnValue.getOrThrow()
                }
            }
            Unit
        }
    }

    @Test
    fun upload_large_attachment() {
        val largeAttachment = InputStreamAndHash.createInMemoryTestZip(15.MB.toInt(), 0)
        withoutTestSerialization {
            driver(DriverParameters(startNodesInProcess = true)) {
                val node = startNode().getOrThrow()
                val hash = node.rpc.uploadAttachment(largeAttachment.inputStream)
                assertThat(hash).isEqualTo(largeAttachment.sha256)
            }
        }
    }

    @Test
    fun upload_large_attachment_with_metadata() {
        val largeAttachment = InputStreamAndHash.createInMemoryTestZip(15.MB.toInt(), 0)
        val uploader = "Light"
        val fileName = "death_note.txt"
        withoutTestSerialization {
            driver(DriverParameters(startNodesInProcess = true)) {
                val node = startNode().getOrThrow()
                val hash = node.rpc.uploadAttachmentWithMetadata(largeAttachment.inputStream, uploader, fileName)
                assertThat(hash).isEqualTo(largeAttachment.sha256)

                val hashesForCriteria = node.rpc.queryAttachments(criteriaFor(uploader, fileName), null)
                assertThat(hashesForCriteria).hasSize(1)
                assertThat(hashesForCriteria.single()).isEqualTo(largeAttachment.sha256)
            }
        }
    }

    private fun criteriaFor(uploader: String, fileName: String): AttachmentQueryCriteria {
        return AttachmentQueryCriteria.AttachmentsQueryCriteria(uploaderCondition = ColumnPredicate.EqualityComparison(EqualityComparisonOperator.EQUAL, uploader), filenameCondition = ColumnPredicate.EqualityComparison(EqualityComparisonOperator.EQUAL, fileName))
    }
}