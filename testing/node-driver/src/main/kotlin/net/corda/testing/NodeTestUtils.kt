@file:JvmName("NodeTestUtils")

package net.corda.testing

import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.commonName
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.VerifierType
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseProperties
import org.bouncycastle.asn1.x500.X500Name
import java.net.URL
import java.nio.file.Path

/**
 * Creates and tests a ledger built by the passed in dsl. The provided services can be customised, otherwise a default
 * of a freshly built [MockServices] is used.
 */
@JvmOverloads fun ledger(
        services: ServiceHub = MockServices(),
        initialiseSerialization: Boolean = true,
        dsl: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.() -> Unit
): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {
    if (initialiseSerialization) initialiseTestSerialization()
    try {
        val ledgerDsl = LedgerDSL(TestLedgerDSLInterpreter(services))
        dsl(ledgerDsl)
        return ledgerDsl
    } finally {
        if (initialiseSerialization) resetTestSerialization()
    }
}

/**
 * Creates a ledger with a single transaction, built by the passed in dsl.
 *
 * @see LedgerDSLInterpreter._transaction
 */
@JvmOverloads fun transaction(
        transactionLabel: String? = null,
        transactionBuilder: TransactionBuilder = TransactionBuilder(notary = DUMMY_NOTARY),
        initialiseSerialization: Boolean = true,
        dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail
) = ledger(initialiseSerialization = initialiseSerialization) {
    dsl(TransactionDSL(TestTransactionDSLInterpreter(this.interpreter, transactionBuilder)))
}

fun testNodeConfiguration(
        baseDirectory: Path,
        myLegalName: X500Name): NodeConfiguration {
    abstract class MockableNodeConfiguration : NodeConfiguration // Otherwise Mockito is defeated by val getters.
    val nc = spy<MockableNodeConfiguration>()
    whenever(nc.baseDirectory).thenReturn(baseDirectory)
    whenever(nc.myLegalName).thenReturn(myLegalName)
    whenever(nc.minimumPlatformVersion).thenReturn(1)
    whenever(nc.keyStorePassword).thenReturn("cordacadevpass")
    whenever(nc.trustStorePassword).thenReturn("trustpass")
    whenever(nc.rpcUsers).thenReturn(emptyList())
    whenever(nc.dataSourceProperties).thenReturn(makeTestDataSourceProperties(myLegalName.commonName))
    whenever(nc.database).thenReturn(makeTestDatabaseProperties())
    whenever(nc.emailAddress).thenReturn("")
    whenever(nc.exportJMXto).thenReturn("")
    whenever(nc.devMode).thenReturn(true)
    whenever(nc.certificateSigningService).thenReturn(URL("http://localhost"))
    whenever(nc.certificateChainCheckPolicies).thenReturn(emptyList())
    whenever(nc.verifierType).thenReturn(VerifierType.InMemory)
    whenever(nc.messageRedeliveryDelaySeconds).thenReturn(5)
    return nc
}
