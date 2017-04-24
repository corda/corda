package net.corda.irs.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Party
import net.corda.core.node.PluginServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.AbstractCollectSignaturesFlowResponder
import net.corda.flows.CollectSignaturesFlow

object CollectSignatureFlowImpl {
    class Service(services: PluginServiceHub) {
        init {
            services.registerFlowInitiator(CollectSignaturesFlow::class.java, ::Responder)
        }
    }

    class Responder(otherParty: Party) : AbstractCollectSignaturesFlowResponder(otherParty) {
        @Suspendable override fun checkTransaction(stx: SignedTransaction) = Unit
    }
}

