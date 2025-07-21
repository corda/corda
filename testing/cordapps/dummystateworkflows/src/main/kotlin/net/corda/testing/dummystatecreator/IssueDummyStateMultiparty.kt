package net.corda.testing.dummystatecreator

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.singleIdentity
import kotlin.random.Random

@InitiatingFlow
class IssueDummyStateMultiparty (val counterParty: Party, val notary: Party, val useConfidentialIdenities: Confidenitality) : FlowLogic<StateAndRef<DummyContract.MultiOwnerState>>() {

    @CordaSerializable
    enum class Confidenitality {
        NONE,
        OLD,
        NEW
    }

    @Suspendable
    override fun call(): StateAndRef<DummyContract.MultiOwnerState> {
        val session = initiateFlow(counterParty)

        session.send(useConfidentialIdenities)

        val myKeys = mutableListOf(serviceHub.myInfo.singleIdentity().owningKey)

        val participants = when (useConfidentialIdenities){
            Confidenitality.OLD -> subFlow(SwapIdentitiesFlow(session))
            Confidenitality.NEW -> subFlow(AgreeConfidentialKeysFlow(session))
            else -> emptyMap<Party, AnonymousParty>()
        }.let {
            if (it.isEmpty())
                listOf(serviceHub.myInfo.singleIdentity(), counterParty)
            else {
                myKeys.add(it[serviceHub.myInfo.singleIdentity()]!!.owningKey)
                it.values.toList()
            }
        }

        val tx = TransactionBuilder(notary)
                .addOutputState(DummyContract.MultiOwnerState(Random.nextInt(), participants))
                .addCommand(DummyContract.Commands.Create(), participants.map{it.owningKey})
        val stx = serviceHub.signInitialTransaction(tx, myKeys.last())

        val counterSigned = subFlow(CollectSignaturesFlow(stx, listOf(session), myKeys))
        val final = subFlow(FinalityFlow(counterSigned, session ))
        return final.tx.outRef(0)
    }
}

@InitiatedBy(IssueDummyStateMultiparty::class)
class IssueDummyStateResponder(val otherSideSession: FlowSession) : FlowLogic<Unit>(){
    @Suspendable
    override fun call() {
        val useConfidentialIdenities = otherSideSession.receive<IssueDummyStateMultiparty.Confidenitality>().unwrap{it}
        when (useConfidentialIdenities){
            IssueDummyStateMultiparty.Confidenitality.OLD -> subFlow(SwapIdentitiesFlow(otherSideSession))
            IssueDummyStateMultiparty.Confidenitality.NEW -> subFlow(AgreeConfidentialKeysFlow(otherSideSession))
            else -> {}
        }
        subFlow(object: SignTransactionFlow(otherSideSession){
            override fun checkTransaction(stx: SignedTransaction) {
            }
        })
        subFlow(ReceiveFinalityFlow(otherSideSession))
    }
}