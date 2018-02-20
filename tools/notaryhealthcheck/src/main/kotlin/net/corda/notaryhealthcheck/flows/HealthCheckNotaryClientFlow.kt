package net.corda.notaryhealthcheck.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.NotarisationRequest
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.internal.generateSignature
import net.corda.core.transactions.SignedTransaction

/**
 * Notarises the provided transaction. If [checkEntireCluster] is set to *true*, will repeat the notarisation request
 * to each member of the notary cluster.
 */
class HealthCheckNotaryClientFlow(
        stx: SignedTransaction,
        /**
         * If set to *true*, will issue a notarisation request to each replica in the notary cluster,
         * rather than sending the request to one replica only.
         */
        private val checkEntireCluster: Boolean = false
) : NotaryFlow.Client(stx) {
    @Suspendable
    @Throws(NotaryException::class)
    override fun call(): List<TransactionSignature> {
        progressTracker.currentStep = REQUESTING
        val notaryParty = checkTransaction()

        val parties = if (checkEntireCluster) {
            serviceHub.networkMapCache
                    .getNodesByLegalIdentityKey(notaryParty.owningKey)
                    .map { it.legalIdentities.first() }
        } else {
            listOf(notaryParty)
        }
        var signatures: List<TransactionSignature> = emptyList()
        parties.forEach { nodeLegalIdentity ->
            logger.info("Sending notarisation request to: $nodeLegalIdentity")
            val response = notarise(nodeLegalIdentity)
            signatures = validateResponse(response, notaryParty)
            logger.info("Received a valid signature from $nodeLegalIdentity, signed by $notaryParty")
        }
        return signatures
    }
}