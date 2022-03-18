package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.utilities.unwrap

class ExchangeAttestationFlow(private val session : FlowSession) : FlowLogic<ByteArray>() {

    @Suspendable
    override fun call() : ByteArray {

        val encSvc = serviceHub.encryptedTransactionService
        val ourAttestationBytes = encSvc.getEnclaveInstance()

        val theirAttestationBytes = session.sendAndReceive<ByteArray>(ourAttestationBytes).unwrap { it }
        // this can throw if the enclave does not believe the attestation to be valid
        encSvc.registerRemoteEnclaveInstanceInfo(runId.uuid, theirAttestationBytes)

        return theirAttestationBytes
    }
}

class ExchangeAttestationFlowHandler(private val otherSideSession: FlowSession) : FlowLogic<ByteArray>() {

    @Suspendable
    override fun call(): ByteArray {

        val encSvc = serviceHub.encryptedTransactionService
        val ourAttestationBytes = encSvc.getEnclaveInstance()

        val theirAttestationBytes = otherSideSession.receive<ByteArray>().unwrap { it }
        // this can throw if the enclave does not believe the attestation to be valid
        encSvc.registerRemoteEnclaveInstanceInfo(runId.uuid, theirAttestationBytes)

        otherSideSession.send(ourAttestationBytes)

        return theirAttestationBytes
    }
}
