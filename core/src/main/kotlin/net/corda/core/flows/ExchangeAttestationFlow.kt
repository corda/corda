package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.internal.IdempotentFlow
import net.corda.core.utilities.unwrap

@InitiatingFlow
class ExchangeAttestationFlow(private val counterParty: Party) : FlowLogic<ByteArray>(), IdempotentFlow {

    @Suspendable
    override fun call() : ByteArray {

        val encSvc = serviceHub.encryptedTransactionService
        val ourAttestationBytes = encSvc.getEnclaveInstance()
        val session = initiateFlow(counterParty)

        return encSvc.getRemoteAttestationForParty(session.counterparty) ?: run {
            val fetchedAttestation = session.sendAndReceive<ByteArray>(ourAttestationBytes).unwrap { it }
            // this can throw if the enclave does not believe the attestation to be valid
            encSvc.registerRemoteEnclaveInstanceInfo(runId.uuid, fetchedAttestation, session.counterparty)
            fetchedAttestation
        }
    }
}

@InitiatedBy(ExchangeAttestationFlow::class)
class ExchangeAttestationFlowHandler(val otherSideSession: FlowSession) : FlowLogic<ByteArray>() {

    @Suspendable
    override fun call(): ByteArray {

        val encSvc = serviceHub.encryptedTransactionService
        val ourAttestationBytes = encSvc.getEnclaveInstance()

        val theirAttestationBytes = otherSideSession.receive<ByteArray>().unwrap { it }
        // this can throw if the enclave does not believe the attestation to be valid
        encSvc.getRemoteAttestationForParty(otherSideSession.counterparty)
                ?: encSvc.registerRemoteEnclaveInstanceInfo(runId.uuid, theirAttestationBytes, otherSideSession.counterparty)

        otherSideSession.send(ourAttestationBytes)

        return theirAttestationBytes
    }
}
