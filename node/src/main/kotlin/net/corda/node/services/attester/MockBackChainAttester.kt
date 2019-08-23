package net.corda.node.services.attester

import net.corda.core.node.services.AttesterCertificate
import net.corda.core.node.services.AttesterRequest
import net.corda.core.node.services.AttesterServiceType
import net.corda.core.utilities.OpaqueBytes

class MockBackChainAttester: AttesterService {

    override val serviceType = AttesterServiceType.BACKCHAIN_VALIDATOR

    override fun certify(input: AttesterRequest): AttesterCertificate {
        return AttesterCertificate(
                service = serviceType,
                schemeId = 0,
                proof = OpaqueBytes("mock".toByteArray()),
                assumptions = OpaqueBytes("mock".toByteArray())
        )
    }
}