@file:JvmName("NodeTestUtils")

package net.corda.testing

import com.nhaarman.mockito_kotlin.doCallRealMethod
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.VerifierType
import net.corda.nodeapi.User
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseProperties
import java.net.URL
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
        doReturn(makeTestDatabaseProperties()).whenever(it).database
        doReturn("").whenever(it).emailAddress
        doReturn("").whenever(it).exportJMXto
        doReturn(true).whenever(it).devMode
        doReturn(URL("http://localhost")).whenever(it).certificateSigningService
        doReturn(emptyList<CertChainPolicyConfig>()).whenever(it).certificateChainCheckPolicies
        doReturn(VerifierType.InMemory).whenever(it).verifierType
        doReturn(5).whenever(it).messageRedeliveryDelaySeconds
        doReturn(0L).whenever(it).additionalNodeInfoPollingFrequencyMsec
        doReturn(null).whenever(it).devModeOptions
        doCallRealMethod().whenever(it).certificatesDirectory
        doCallRealMethod().whenever(it).trustStoreFile
        doCallRealMethod().whenever(it).sslKeystore
        doCallRealMethod().whenever(it).nodeKeystore
    }
}
