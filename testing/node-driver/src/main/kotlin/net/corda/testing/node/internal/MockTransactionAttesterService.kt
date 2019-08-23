package net.corda.testing.node.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes

class MockAttesterClient(val serviceId: AttesterServiceType): AttesterClient() {

    override fun generateAttestationRequest(input: SignedTransaction): AttesterRequest {
        return AttesterRequest(
                requestType = serviceId,
                tx = input,
                schemeId = AttesterScheme.MOCK,
                payload = OpaqueBytes(bytes = "AttesterRequest-${serviceId.id}".toByteArray())
        )
    }

    override fun verify(txId: SecureHash, certificate: AttesterCertificate) { }
}