package net.corda.flows.djvm.attachment

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.djvm.attachment.SandboxAttachmentContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class SandboxAttachmentFlow(private val command: CommandData) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                .addOutputState(SandboxAttachmentContract.State(ourIdentity))
                .addCommand(Command(command, ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}
