package com.r3.transactionfailure.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.dbfailure.contracts.DbFailureContract
import com.r3.dbfailure.workflows.CreateStateFlow
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.seconds

object ErrorHandling {
    var hookBeforeFirstCheckpoint: () -> Unit = {}
    var hookAfterFirstCheckpoint: () -> Unit = {}
    var hookAfterSecondCheckpoint: () -> Unit = {}

    @StartableByRPC
    class CheckpointAfterErrorFlow(private val errorTarget: Int) : FlowLogic<Unit>() {
        // We cannot allow this:
        //      recordTransactions -> throws HospitalizeException
        //      flow suppress the HospitalizeException
        //      flow checkpoints
        @Suspendable
        override fun call() {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            hookBeforeFirstCheckpoint.invoke() // should be executed once
            sleep(1.seconds) // checkpoint - flow should retry from this one
            hookAfterFirstCheckpoint.invoke() // should be executed twice
            val txTarget = CreateStateFlow.getTxTarget(errorTarget)
            val state = DbFailureContract.TestState(
                UniqueIdentifier(),
                listOf(ourIdentity),
                if (txTarget == CreateStateFlow.ErrorTarget.TxInvalidState) null else "valid hibernate value",
                errorTarget,
                ourIdentity)
            val txCommand = Command(DbFailureContract.Commands.Create(), ourIdentity.owningKey)
            val txBuilder = TransactionBuilder(notary).addOutputState(state).addCommand(txCommand)
            val signedTx = serviceHub.signInitialTransaction(txBuilder)
            try {
                serviceHub.recordTransactions(signedTx)
            } catch(t: Throwable) {
                if (CreateStateFlow.getFlowTarget(errorTarget) == CreateStateFlow.ErrorTarget.FlowSwallowErrors) {
                    logger.info("Test flow: Swallowing all exception! Muahahaha!", t)
                } else {
                    logger.info("Test flow: caught exception - rethrowing")
                    throw t
                }
            }
            sleep(1.seconds) // checkpoint - this checkpoint should fail
            hookAfterSecondCheckpoint.invoke() // should be never executed
        }
    }
}