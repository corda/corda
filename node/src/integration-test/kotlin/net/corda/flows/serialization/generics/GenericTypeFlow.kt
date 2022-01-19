package net.corda.flows.serialization.generics

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.serialization.generics.DataObject
import net.corda.contracts.serialization.generics.GenericTypeContract.Purchase
import net.corda.contracts.serialization.generics.GenericTypeContract.State
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import java.util.Optional

@StartableByRPC
class GenericTypeFlow(private val purchase: DataObject?) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                .addOutputState(State(ourIdentity, purchase))
                .addCommand(Command(Purchase(Optional.ofNullable(purchase)), ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}
