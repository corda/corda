package net.corda.flows.serialization.missing

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.serialization.missing.CustomData
import net.corda.contracts.serialization.missing.MissingSerializerContract.CustomDataState
import net.corda.contracts.serialization.missing.MissingSerializerContract.Operate
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class MissingSerializerBuilderFlow(private val value: Long) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                .addOutputState(CustomDataState(ourIdentity, CustomData(value)))
                .addCommand(Command(Operate(), ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}
