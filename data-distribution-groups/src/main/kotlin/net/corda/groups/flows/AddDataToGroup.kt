package net.corda.groups.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import java.security.PublicKey

class AddDataToGroup(val key: PublicKey, val transactions: Set<SignedTransaction>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        transactions.forEach { transaction ->
            // Sign the transaction with the group key.
            val signature = serviceHub.keyManagementService.sign(
                    bytes = transaction.serialize().bytes,
                    publicKey = key
            )

            val transactionSignature = TransactionSignature(
                    bytes = signature.bytes,
                    by = key,
                    signatureMetadata = SignatureMetadata(  // What's this for?
                            platformVersion = 1,
                            schemeNumberID = 1
                    )
            )

            // Add the signature and then send the transaction to the group.
            val transactionToAdd = transaction.withAdditionalSignature(transactionSignature)
            subFlow(SendDataToGroup(key, transactionToAdd))
        }
    }
}