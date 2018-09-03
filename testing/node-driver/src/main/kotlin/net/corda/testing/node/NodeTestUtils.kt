@file:JvmName("NodeTestUtils")

package net.corda.testing.node

import net.corda.core.context.Actor
import net.corda.core.context.AuthServiceId
import net.corda.core.context.InvocationContext
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.EnforceVerifyOrFail
import net.corda.testing.dsl.LedgerDSL
import net.corda.testing.dsl.LedgerDSLInterpreter
import net.corda.testing.dsl.TestLedgerDSLInterpreter
import net.corda.testing.dsl.TestTransactionDSLInterpreter
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter

/**
 * Creates and tests a ledger built by the passed in dsl.
 */
@JvmOverloads
fun ServiceHub.ledger(
        notary: Party = TestIdentity.fresh("ledger notary").party,
        script: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.() -> Unit
): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {
    val serializationExists = try {
        effectiveSerializationEnv
        true
    } catch (e: IllegalStateException) {
        false
    }
    return LedgerDSL(TestLedgerDSLInterpreter(this), notary).apply {
        if (serializationExists) {
            script()
        } else {
            SerializationEnvironmentRule.run("ledger") { script() }
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
