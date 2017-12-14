@file:JvmName("NodeTestUtils")

package net.corda.testing.node

import net.corda.core.context.Actor
import net.corda.core.context.AuthServiceId
import net.corda.core.context.InvocationContext
import net.corda.core.context.Origin
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.api.StartedNodeServices
import net.corda.testing.*

/**
 * Creates and tests a ledger built by the passed in dsl.
 */
fun ServiceHub.ledger(
        notary: Party,
        dsl: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.() -> Unit
): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {
    return LedgerDSL(TestLedgerDSLInterpreter(this), notary).apply(dsl)
}

/**
 * Creates a ledger with a single transaction, built by the passed in dsl.
 *
 * @see LedgerDSLInterpreter._transaction
 */
fun ServiceHub.transaction(
        notary: Party,
        dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail
) = ledger(notary) {
    dsl(TransactionDSL(TestTransactionDSLInterpreter(interpreter, TransactionBuilder(notary)), notary))
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
