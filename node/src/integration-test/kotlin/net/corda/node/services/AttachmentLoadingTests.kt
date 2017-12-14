package net.corda.node.services

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.Contract
import net.corda.core.contracts.PartyAndReference
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.toLedgerTransaction
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SerializationFactory
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.testing.*
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.internal.withoutTestSerialization
import net.corda.testing.services.MockAttachmentStorage
import net.corda.testing.rigorousMock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.assertFailsWith

class AttachmentLoadingTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val attachments = MockAttachmentStorage()
    private val provider = CordappProviderImpl(CordappLoader.createDevMode(listOf(isolatedJAR)), attachments)
    private val cordapp get() = provider.cordapps.first()
    private val attachmentId get() = provider.getCordappAttachmentId(cordapp)!!
    private val appContext get() = provider.getAppContext(cordapp)

    private companion object {
        private val logger = contextLogger()
        val isolatedJAR = AttachmentLoadingTests::class.java.getResource("isolated.jar")!!
        val ISOLATED_CONTRACT_ID = "net.corda.finance.contracts.isolated.AnotherDummyContract"

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
            isolatedJAR.openStream().buffered().use { input ->
                Files.newOutputStream(path).buffered().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private val services = rigorousMock<ServicesForResolution>().also {
        doReturn(attachments).whenever(it).attachments
        doReturn(provider).whenever(it).cordappProvider
        doReturn(rigorousMock<IdentityService>().also {
            doReturn(null).whenever(it).partyFromKey(DUMMY_BANK_A.owningKey)
        }).whenever(it).identityService
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
    fun `test that attachments retrieved over the network are not used for code`() = withoutTestSerialization {
        driver {
            installIsolatedCordappTo(bankAName)
            val (bankA, bankB) = createTwoNodes()
            assertFailsWith<UnexpectedFlowEndException>("Party C=CH,L=Zurich,O=BankB rejected session request: Don't know net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Initiator") {
                bankA.rpc.startFlowDynamic(flowInitiatorClass, bankB.nodeInfo.legalIdentities.first()).returnValue.getOrThrow()
            }
        }
        Unit
    }

    @Test
    fun `tests that if the attachment is loaded on both sides already that a flow can run`() = withoutTestSerialization {
        driver {
            installIsolatedCordappTo(bankAName)
            installIsolatedCordappTo(bankBName)
            val (bankA, bankB) = createTwoNodes()
            bankA.rpc.startFlowDynamic(flowInitiatorClass, bankB.nodeInfo.legalIdentities.first()).returnValue.getOrThrow()
        }
        Unit
    }
}
