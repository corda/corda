package net.corda.flows.djvm.crypto

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.djvm.crypto.DeterministicCryptoContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes

@InitiatingFlow
@StartableByRPC
class DeterministicCryptoFlow(
        private val command: CommandData,
        private val original: OpaqueBytes,
        private val signature: OpaqueBytes
) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                .addOutputState(DeterministicCryptoContract.CryptoState(ourIdentity, original, signature))
                .addCommand(Command(command, ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}
