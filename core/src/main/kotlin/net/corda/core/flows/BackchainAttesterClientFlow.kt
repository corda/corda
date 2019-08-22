package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.DoNotImplement
import net.corda.core.crypto.ApplicationSignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.Party
import net.corda.core.node.services.AttesterServiceType
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.lang.IllegalStateException

/**
 * SGX: platform [Flow] for interrogating a backchain validity oracle and retrieve
 * an attestation signature.
 *
 * Note: the responding flow will be internally registered in the node running
 * the attestation server
 *
 * Note: this should ideally produce a composite signature from multiple attester
 */
@DoNotImplement
@InitiatingFlow
class BackchainAttesterClientFlow(
        val transaction: SignedTransaction,
        override val progressTracker: ProgressTracker
) : FlowLogic<TransactionSignature>() {

    constructor(tx: SignedTransaction): this(tx, tracker())

    companion object {
        object REQUESTING : ProgressTracker.Step("Requesting attester signature")
        object VALIDATING : ProgressTracker.Step("Validating attester signature")

        private val attesterServiceType = AttesterServiceType.BACKCHAIN_VALIDATOR

        fun tracker() = ProgressTracker(REQUESTING, VALIDATING)
    }

    @Suspendable
    override fun call(): TransactionSignature {
        val connector = serviceHub.getAttesterClient(attesterServiceType)
                ?: throw RuntimeException("Cannot instantiate attestation client")
        val attester = pickSgxAttesterNode()
        logger.info("Requesting signature from attester: ${attester.name}")
        val request = connector.generateAttestationRequest(transaction)
        val session = initiateFlow(attester)
        val response = session.sendAndReceive<TransactionSignature>(request)
        logger.info("Validating attester response")
        val attesterSignature = response.unwrap {
            it.verify(transaction.id)
            it
        }
        val attestationCert = (attesterSignature.signatureMetadata.applicationMetadata as? ApplicationSignatureMetadata.AttesterCertificateHolder)
                ?: throw IllegalStateException("Invalid attester metadata")
        connector.verify(transaction, attestationCert.data)
        return attesterSignature
    }

    private fun pickSgxAttesterNode(): Party {
        val enclaveHosts = serviceHub.networkParameters.enclaveHosts
                ?: throw IllegalStateException("Missing enclave hosts info in network parameters")
        return enclaveHosts.hosts.getValue(attesterServiceType).first()
    }
}