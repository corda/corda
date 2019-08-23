package net.corda.node.services.attester

import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttesterCertificate
import net.corda.core.node.services.AttesterClient
import net.corda.core.node.services.AttesterRequest
import net.corda.core.node.services.AttesterServiceType
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes

object MockBackChainAttesterClient: AttesterClient() {

    val serviceId = AttesterServiceType.BACKCHAIN_VALIDATOR

    override fun generateAttestationRequest(input: SignedTransaction): AttesterRequest {
        return AttesterRequest(
                requestType = serviceId,
                txId = input.id,
                schemeId = 0,
                encoded = OpaqueBytes(bytes = "AttesterRequest-${serviceId.id}".toByteArray())
        )
    }

    override fun verify(txId: SecureHash, certificate: AttesterCertificate) { }
}