package net.corda.testing.node.internal

import net.corda.core.node.services.AttesterCertificate
import net.corda.core.node.services.AttesterRequest
import net.corda.core.node.services.AttesterServiceType
import net.corda.core.node.services.AttesterClient
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes

class MockAttesterClient(val serviceId: AttesterServiceType): AttesterClient() {
    override fun generateAttestationRequest(input: SignedTransaction): AttesterRequest {
        return AttesterRequest(
                requestType = serviceId,
                txId = input.id,
                schemeId = 0,
                encoded =OpaqueBytes(bytes = "AttesterRequest-${serviceId.id}".toByteArray())
        )
    }

    override fun verify(input: SignedTransaction, certificate: AttesterCertificate) { }
}