package net.corda.flows.serialization.custom

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.serialization.custom.Currantsy
import net.corda.contracts.serialization.custom.CustomSerializerContract.CurrantsyState
import net.corda.contracts.serialization.custom.CustomSerializerContract.Purchase
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class CustomSerializerFlow(
    private val purchase: Currantsy
) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                .addOutputState(CurrantsyState(ourIdentity, purchase))
                .addCommand(Command(Purchase(), ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}
