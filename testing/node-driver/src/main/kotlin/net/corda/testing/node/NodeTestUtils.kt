@file:JvmName("NodeTestUtils")

package net.corda.testing.node

import net.corda.core.context.Actor
import net.corda.core.context.AuthServiceId
import net.corda.core.context.InvocationContext
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.common.internal.addNotary
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.*
import net.corda.testing.internal.withTestSerializationEnvIfNotSet
import net.corda.testing.node.internal.MockNetworkParametersStorage

/**
 * Creates and tests a ledger built by the passed in dsl.
 */
@JvmOverloads
fun ServiceHub.ledger(
        notary: Party = TestIdentity.fresh("ledger notary").party,
        script: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.() -> Unit
): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {
    val currentParameters = networkParametersService.run {
        lookup(currentHash) ?: throw IllegalStateException("Current network parameters not found, $currentHash")

    }
    if (currentParameters.notaries.none { it.identity == notary }) {
        // Add the notary to the whitelist. Otherwise no constructed transactions will verify.
        val newParameters = currentParameters.addNotary(notary)
        (networkParametersService as MockNetworkParametersStorage).setCurrentParametersUnverified(newParameters)
    }

    return withTestSerializationEnvIfNotSet {
        val interpreter = TestLedgerDSLInterpreter(this)
        LedgerDSL(interpreter, notary).apply {
            script()
        }
    }
}

/**
 * Creates a ledger with a single transaction, built by the passed in dsl.
 *
 * @see LedgerDSLInterpreter._transaction
 */
@JvmOverloads
fun ServiceHub.transaction(
        notary: Party = TestIdentity.fresh("transaction notary").party,
        script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail
) = ledger(notary) {
    TransactionDSL(TestTransactionDSLInterpreter(interpreter, TransactionBuilder(notary)), notary).script()
}

/** Creates a new [Actor] for use in testing with the given [owningLegalIdentity]. */
fun testActor(owningLegalIdentity: CordaX500Name = CordaX500Name("Test Company Inc.", "London", "GB")) = Actor(Actor.Id("Only For Testing"), AuthServiceId("TEST"), owningLegalIdentity)

/** Creates a new [InvocationContext] for use in testing with the given [owningLegalIdentity]. */
fun testContext(owningLegalIdentity: CordaX500Name = CordaX500Name("Test Company Inc.", "London", "GB")) = InvocationContext.rpc(testActor(owningLegalIdentity))
