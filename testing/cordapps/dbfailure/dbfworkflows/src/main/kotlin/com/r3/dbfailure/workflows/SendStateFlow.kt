package com.r3.dbfailure.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.dbfailure.contracts.DbFailureContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

object SendStateFlow {

    /**
     * Creates a [DbFailureContract.TestState], signs it, collects a signature from a separate node and then calls [FinalityFlow] flow.
     * Can throw in various stages
     */
    @StartableByRPC
    @InitiatingFlow
    class PassErroneousOwnableState(private val stateId: UniqueIdentifier, private val errorTarget: Int, private val counterParty: Party) :
        FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            logger.info("Test flow: starting")
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            logger.info("Test flow: create counterparty session")
            val recipientSession = initiateFlow(counterParty)

            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(stateId), status = Vault.StateStatus.UNCONSUMED)
            val inputState = serviceHub.vaultService.queryBy(DbFailureContract.TestState::class.java, queryCriteria).states.singleOrNull()
                ?: throw FlowException("Failed to find single state for linear id $stateId")

            logger.info("Test flow: tx builder")
            val commandAndState = inputState.state.data.withNewOwnerAndErrorTarget(counterParty, errorTarget)
            val txBuilder = TransactionBuilder(notary)
                .addInputState(inputState)
                .addOutputState(commandAndState.ownableState)
                .addCommand(commandAndState.command, listOf(ourIdentity.owningKey, counterParty.owningKey))


            logger.info("Test flow: verify")
            txBuilder.verify(serviceHub)

            val signedTx = serviceHub.signInitialTransaction(txBuilder)

            logger.info("Test flow: send for counterparty signing")
            recipientSession.send(signedTx)
            logger.info("Test flow: Waiting to receive counter signed transaction")
            val counterSignedTx = recipientSession.receive<SignedTransaction>().unwrap { it }
            logger.info("Test flow: Received counter sigend transaction, invoking finality")
            subFlow(FinalityFlow(counterSignedTx, recipientSession))

            logger.info("Test flow: Finishing")
        }
    }

    @InitiatedBy(PassErroneousOwnableState::class)
    class PassErroneousOwnableStateReceiver(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("Test flow counterparty: starting")
            val signedTx = otherSide.receive<SignedTransaction>().unwrap { it }
            logger.info("Test flow counterparty: received TX, signing")
            val counterSignedTx = serviceHub.addSignature(signedTx)
            logger.info("Test flow counterparty: calling hookBeforeCounterPartyAnswers")
            logger.info("Test flow counterparty: Answer with countersigned transaction")
            otherSide.send(counterSignedTx)
            logger.info("Test flow counterparty: calling hookAfterCounterPartyAnswers")
            // Not ideal that we have to do this check, but we must as FinalityFlow does not send locally
            if (!serviceHub.myInfo.isLegalIdentity(otherSide.counterparty)) {
                logger.info("Test flow counterparty: Waiting for finality")
                subFlow(ReceiveFinalityFlow(otherSide))
            }
            logger.info("Test flow counterparty: Finishing")
        }
    }

}