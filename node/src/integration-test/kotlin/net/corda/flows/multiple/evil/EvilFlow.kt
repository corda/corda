package net.corda.flows.multiple.evil

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.multiple.evil.EvilContract.EvilState
import net.corda.contracts.multiple.evil.EvilContract.AddExtra
import net.corda.contracts.multiple.vulnerable.MutableDataObject
import net.corda.contracts.multiple.vulnerable.VulnerablePaymentContract.VulnerablePurchase
import net.corda.contracts.multiple.vulnerable.VulnerablePaymentContract.VulnerableState
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class EvilFlow(
    private val purchase: MutableDataObject
) : FlowLogic<SecureHash>() {
    private companion object {
        private val NOTHING = MutableDataObject(0)
    }

    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                // Add Evil objects first, so that Corda will verify EvilContract first.
                .addCommand(Command(AddExtra(purchase), ourIdentity.owningKey))
                .addOutputState(EvilState(ourIdentity))

                // Now add the VulnerablePaymentContract objects with NO PAYMENT!
                .addCommand(Command(VulnerablePurchase(NOTHING), ourIdentity.owningKey))
                .addOutputState(VulnerableState(ourIdentity, NOTHING))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}
