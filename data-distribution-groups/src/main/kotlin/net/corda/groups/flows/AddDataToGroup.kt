package net.corda.groups.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import java.security.PublicKey

class AddDataToGroup(val key: PublicKey, val transactions: Set<SignedTransaction>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // TODO: Pass in a set of hashes of transactions instead of a set of SignedTransactions.
        // Passing in SignedTransactions allows a party to easily send txs which were not actually committed to the
        // ledger. Instead pass in a Set of hashes, the corresponding transactions are then looked up. This flow will
        // throw an exception if any of the transactions could not be found in the vault. Alternatively, the flow can
        // return a report of which transaction hashes could be propagated to the group.
        // Question: How does this approach work from an SGX perspective?
        transactions.forEach { transaction ->
            logger.info("Adding transaction ${transaction.id} to group $key.")

            val signatureMetadata = SignatureMetadata(
                    platformVersion = serviceHub.myInfo.platformVersion,
                    schemeNumberID = Crypto.findSignatureScheme(key).schemeNumberID
            )

            val signableData = SignableData(transaction.id, signatureMetadata)
            val sig = serviceHub.keyManagementService.sign(signableData, key)

            sig.verify(transaction.id)

            // Add the signature and then send the transaction to the group.
            val transactionToAdd = transaction.withAdditionalSignature(sig)
            subFlow(SendDataToGroup(key, transactionToAdd))
        }
    }
}