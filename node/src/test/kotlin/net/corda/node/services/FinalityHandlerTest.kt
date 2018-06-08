package net.corda.node.services

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.StateMachineRunId
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.node.internal.StartedNode
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StaffedFlowHospital.MedicalRecord.KeptInForObservation
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class FinalityHandlerTest {
    private lateinit var mockNet: InternalMockNetwork

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `sent to flow hospital on error and attempted retry on node restart`() {
        // Setup a network where only Alice has the finance CorDapp and it sends a cash tx to Bob who doesn't have the
        // CorDapp. Bob's FinalityHandler will error when validating the tx.
        mockNet = InternalMockNetwork(cordappPackages = emptyList())

        val alice = mockNet.createNode(InternalMockNodeParameters(
                legalName = ALICE_NAME,
                extraCordappPackages = listOf("net.corda.finance.contracts.asset")
        ))

        var bob = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME))

        val stx = TransactionBuilder(mockNet.defaultNotaryIdentity).let {
            Cash().generateIssue(
                    it,
                    1000.POUNDS.issuedBy(alice.info.singleIdentity().ref(0)),
                    bob.info.singleIdentity(),
                    mockNet.defaultNotaryIdentity
            )
            alice.services.signInitialTransaction(it)
        }

        val finalityHandlerIdFuture = bob.smm.track()
                .updates
                .filter { it.logic is FinalityHandler }
                .map { it.logic.runId }
                .toFuture()

        val finalisedTx = alice.services.startFlow(FinalityFlow(stx)).run {
            mockNet.runNetwork()
            resultFuture.getOrThrow()
        }
        val finalityHandlerId = finalityHandlerIdFuture.getOrThrow()

        bob.assertFlowSentForObservation(finalityHandlerId)
        assertThat(bob.getTransaction(finalisedTx.id)).isNull()

        bob = mockNet.restartNode(bob)
        // Since we've not done anything to fix the orignal error, we expect the finality handler to be sent to the hospital
        // again on restart
        bob.assertFlowSentForObservation(finalityHandlerId)
        assertThat(bob.getTransaction(finalisedTx.id)).isNull()
    }

    private fun StartedNode<*>.assertFlowSentForObservation(runId: StateMachineRunId) {
        val keptInForObservation = smm.flowHospital
                .track()
                .let { it.updates.startWith(it.snapshot) }
                .filter { it.flowId == runId }
                .ofType(KeptInForObservation::class.java)
                .toBlocking()
                .first()
        assertThat(keptInForObservation.by).contains(StaffedFlowHospital.FinalityDoctor)
    }

    private fun StartedNode<*>.getTransaction(id: SecureHash): SignedTransaction? {
        return database.transaction {
            services.validatedTransactions.getTransaction(id)
        }
    }
}
