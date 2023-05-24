package net.corda.finance.test.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryException
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashException
import net.corda.finance.workflows.asset.CashUtils
import java.util.Currency

@StartableByRPC
@InitiatingFlow
open class CashPaymentWithObserversFlow(
        val amount: Amount<Currency>,
        val recipient: Party,
        val observers: Set<Party>,
        private val useObserverSessions: Boolean = false,
        private val anonymous: Boolean = false
) : AbstractCashFlow<SignedTransaction>(tracker()) {

    @Suspendable
    override fun call(): SignedTransaction {
        val recipientSession = initiateFlow(recipient)
//        recipientSession.send(anonymous)
//        val anonymousRecipient: AbstractParty = if (anonymous) {
//            subFlow(SwapIdentitiesFlow(recipientSession))[recipient]!!
//        } else {
//            recipient
//        }
        val observerSessions = observers.map { initiateFlow(it) }
        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        logger.info("Generating spend for: ${builder.lockId}")
        val (spendTX, keysForSigning) = try {
            CashUtils.generateSpend(
                    serviceHub,
                    builder,
                    amount,
                    ourIdentityAndCert,
                    recipient
            )
        } catch (e: InsufficientBalanceException) {
            throw CashException("Insufficient cash for spend: ${e.message}", e)
        }

        logger.info("Signing transaction for: ${spendTX.lockId}")
        val tx = serviceHub.signInitialTransaction(spendTX, keysForSigning)

        logger.info("Finalising transaction for: ${tx.id}")
        val sessionsForFinality = if (serviceHub.myInfo.isLegalIdentity(recipient)) emptyList() else listOf(recipientSession)
        val notarised = finalise(tx, sessionsForFinality, observerSessions)
        logger.info("Finalised transaction for: ${notarised.id}")
        return notarised
    }

    @Suspendable
    private fun finalise(tx: SignedTransaction,
                         sessions: Collection<FlowSession>,
                         observerSessions: Collection<FlowSession>): SignedTransaction {
        try {
            return if (useObserverSessions)
                subFlow(FinalityFlow(tx, sessions, observerSessions = observerSessions))
            else
                subFlow(FinalityFlow(tx, sessions + observerSessions))
        } catch (e: NotaryException) {
            throw CashException("Unable to notarise spend", e)
        }
    }
}

@InitiatedBy(CashPaymentWithObserversFlow::class)
class CashPaymentReceiverWithObserversFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
//        val anonymous = otherSide.receive<Boolean>().unwrap { it }
//        if (anonymous) {
//            subFlow(SwapIdentitiesFlow(otherSide))
//        }
        if (!serviceHub.myInfo.isLegalIdentity(otherSide.counterparty)) {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }
}
