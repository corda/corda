@file:JvmName("NodeTestUtils")

package net.corda.testing

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.context.Actor
import net.corda.core.context.AuthServiceId
import net.corda.core.context.InvocationContext
import net.corda.core.context.Origin
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.FlowStateMachine
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.VerifierType
import net.corda.nodeapi.internal.config.User
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import java.nio.file.Path

/**
 * Creates and tests a ledger built by the passed in dsl. The provided services can be customised, otherwise a default
 * of a freshly built [MockServices] is used.
 */
@JvmOverloads
fun ledger(
        services: ServiceHub = MockServices(),
        dsl: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.() -> Unit
): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {
    return LedgerDSL(TestLedgerDSLInterpreter(services)).also { dsl(it) }
}

/**
 * Creates a ledger with a single transaction, built by the passed in dsl.
 *
 * @see LedgerDSLInterpreter._transaction
 */
@JvmOverloads
fun transaction(
        transactionBuilder: TransactionBuilder = TransactionBuilder(notary = DUMMY_NOTARY),
        cordappPackages: List<String> = emptyList(),
        dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail
) = ledger(services = MockServices(cordappPackages)) {
    dsl(TransactionDSL(TestTransactionDSLInterpreter(this.interpreter, transactionBuilder)))
}

fun testNodeConfiguration(
        baseDirectory: Path,
        myLegalName: CordaX500Name): NodeConfiguration {
    abstract class MockableNodeConfiguration : NodeConfiguration // Otherwise Mockito is defeated by val getters.
    return rigorousMock<MockableNodeConfiguration>().also {
        doReturn(baseDirectory).whenever(it).baseDirectory
        doReturn(myLegalName).whenever(it).myLegalName
        doReturn("cordacadevpass").whenever(it).keyStorePassword
        doReturn("trustpass").whenever(it).trustStorePassword
        doReturn(emptyList<User>()).whenever(it).rpcUsers
        doReturn(null).whenever(it).notary
        doReturn(makeTestDataSourceProperties(myLegalName.organisation)).whenever(it).dataSourceProperties
        doReturn(DatabaseConfig()).whenever(it).database
        doReturn("").whenever(it).emailAddress
        doReturn("").whenever(it).exportJMXto
        doReturn(true).whenever(it).devMode
        doReturn(null).whenever(it).compatibilityZoneURL
        doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
        doReturn(VerifierType.InMemory).whenever(it).verifierType
        doReturn(5).whenever(it).messageRedeliveryDelaySeconds
        doReturn(5.seconds.toMillis()).whenever(it).additionalNodeInfoPollingFrequencyMsec
        doReturn(null).whenever(it).devModeOptions
    }
}

fun testActor(owningLegalIdentity: CordaX500Name = CordaX500Name("Test Company Inc.", "London", "GB")) = Actor(Actor.Id("Only For Testing"), AuthServiceId("TEST"), owningLegalIdentity)

fun testContext(owningLegalIdentity: CordaX500Name = CordaX500Name("Test Company Inc.", "London", "GB")) = InvocationContext.rpc(testActor(owningLegalIdentity))

/**
 * Starts an already constructed flow. Note that you must be on the server thread to call this method. [InvocationContext]
 * has origin [Origin.RPC] and actor with id "Only For Testing".
 */
fun <T> StartedNodeServices.startFlow(logic: FlowLogic<T>): FlowStateMachine<T> = startFlow(logic, newContext()).getOrThrow()

/**
 * Creates a new [InvocationContext] for testing purposes.
 */
fun StartedNodeServices.newContext() = testContext(myInfo.chooseIdentity().name)
