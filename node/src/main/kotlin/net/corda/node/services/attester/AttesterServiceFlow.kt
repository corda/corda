package net.corda.node.services.attester

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.AttesterRequest
import net.corda.core.utilities.unwrap
import java.security.PublicKey

/**
 * SGX: the responding side of an [AttesterClientFlow] wrapping an instance of [AttesterService]
 */
class AttesterServiceFlow(val session: FlowSession,
                          val service: AttesterService,
                          val attesterKey: PublicKey
): FlowLogic<Void?>() {

    @Suspendable
    override fun call(): Void? {
        val request = session.receive<AttesterRequest>().unwrap { it }
        session.send(invokeAttester(request))
        return null
    }

    fun invokeAttester(request: AttesterRequest): TransactionSignature {
        val certificate = service.certify(request)
        val signableData = SignableData(
                request.txId,
                SignatureMetadata(
                        serviceHub.myInfo.platformVersion,
                        Crypto.findSignatureScheme(attesterKey).schemeNumberID,
                        ApplicationSignatureMetadata.AttesterCertificateHolder(certificate))
        )
        return serviceHub.keyManagementService.sign(signableData, attesterKey)
    }
}