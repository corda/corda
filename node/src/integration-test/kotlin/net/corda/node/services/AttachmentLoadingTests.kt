package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.common.internal.checkNotOnClasspath
import net.corda.testing.core.*
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.cordappsForPackages
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import java.net.URL
import java.net.URLClassLoader

@Ignore("Temporarily ignored as it fails with: java.lang.SecurityException: sealing violation: can't seal package net.corda.nodeapi: already loaded")
class AttachmentLoadingTests : IntegrationTest() {
    private companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME, BOB_NAME, DUMMY_NOTARY_NAME)

        val isolatedJar: URL = AttachmentLoadingTests::class.java.getResource("/isolated.jar")
        val isolatedClassLoader = URLClassLoader(arrayOf(isolatedJar))
        val issuanceFlowClass: Class<FlowLogic<StateRef>> = uncheckedCast(loadFromIsolated("net.corda.isolated.workflows.IsolatedIssuanceFlow"))

        init {
            checkNotOnClasspath("net.corda.isolated.contracts.AnotherDummyContract") {
                "isolated module cannot be on the classpath as otherwise it will be pulled into the nodes the driver creates and " +
                        "contaminate the tests. This is a known issue with the driver and we must work around it until it's fixed."
            }
        }

        fun loadFromIsolated(className: String): Class<*> = Class.forName(className, false, isolatedClassLoader)
    }

    @Test
    fun `contracts downloaded from the network are not executed without the DJVM`() {
        driver(DriverParameters(
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = false)),
                cordappsForAllNodes = cordappsForPackages(javaClass.packageName)
        )) {
            installIsolatedCordapp(ALICE_NAME)

            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME),
                    startNode(providedName = BOB_NAME)
            ).transpose().getOrThrow()

            val stateRef = alice.rpc.startFlowDynamic(issuanceFlowClass, 1234).returnValue.getOrThrow()

            // The exception that we actually want is MissingAttachmentsException, but this is thrown in a responder flow on Bob. To work
            // around that it's re-thrown as a FlowException so that it can be propagated to Alice where we pick it here.
            assertThatThrownBy {
                alice.rpc.startFlow(::ConsumeAndBroadcastFlow, stateRef, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow()
            }.hasMessage("Attempting to load Contract Attachments downloaded from the network")
        }
    }

    @Test
    fun `contract is executed if installed locally`() {
        driver(DriverParameters(
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = false)),
                cordappsForAllNodes = cordappsForPackages(javaClass.packageName)
        )) {
            installIsolatedCordapp(ALICE_NAME)
            installIsolatedCordapp(BOB_NAME)

            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME),
                    startNode(providedName = BOB_NAME)
            ).transpose().getOrThrow()

            val stateRef = alice.rpc.startFlowDynamic(issuanceFlowClass, 1234).returnValue.getOrThrow()
            alice.rpc.startFlow(::ConsumeAndBroadcastFlow, stateRef, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        }
    }

    private fun DriverDSL.installIsolatedCordapp(name: CordaX500Name) {
        val cordappsDir = (baseDirectory(name) / "cordapps").createDirectories()
        isolatedJar.toPath().copyToDirectory(cordappsDir)
    }

    @InitiatingFlow
    @StartableByRPC
    class ConsumeAndBroadcastFlow(private val stateRef: StateRef, private val otherSide: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val stateAndRef = serviceHub.toStateAndRef<ContractState>(stateRef)
            val stx = serviceHub.signInitialTransaction(
                    TransactionBuilder(notary).addInputState(stateAndRef).addCommand(dummyCommand(ourIdentity.owningKey))
            )
            stx.verify(serviceHub, checkSufficientSignatures = false)
            val session = initiateFlow(otherSide)
            subFlow(FinalityFlow(stx, session))
            // It's important we wait on this dummy receive, as otherwise it's possible we miss any errors the other side throws
            session.receive<String>().unwrap { require(it == "OK") { "Not OK: $it"} }
        }
    }

    @InitiatedBy(ConsumeAndBroadcastFlow::class)
    class ConsumeAndBroadcastResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            try {
                subFlow(ReceiveFinalityFlow(otherSide))
            } catch (e: MissingAttachmentsException) {
                throw FlowException(e.message)
            }
            otherSide.send("OK")
        }
    }
}
