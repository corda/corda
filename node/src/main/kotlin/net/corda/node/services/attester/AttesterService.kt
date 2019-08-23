package net.corda.node.services.attester

import net.corda.core.node.services.AttesterCertificate
import net.corda.core.node.services.AttesterRequest
import net.corda.core.node.services.AttesterServiceType

/**
 * SGX: Attester service plugin
 */
interface AttesterService {
    val serviceType: AttesterServiceType
    fun certify(input: AttesterRequest): AttesterCertificate
}