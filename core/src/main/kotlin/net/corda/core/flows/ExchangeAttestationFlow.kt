package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.utilities.unwrap

class ExchangeAttestationFlow(private val session : FlowSession) : FlowLogic<ByteArray>() {

    @Suspendable
    override fun call() : ByteArray {

        val ourAttestationBytes = serviceHub.encryptedTransactionService.getEnclaveInstance()

        return session.sendAndReceive<ByteArray>(ourAttestationBytes).unwrap { it }
    }
}

class ExchangeAttestationFlowHandler(private val otherSideSession: FlowSession) : FlowLogic<ByteArray>() {

    @Suspendable
    override fun call(): ByteArray {

        val ourAttestationBytes = serviceHub.encryptedTransactionService.getEnclaveInstance()

        val theirAttestationBytes = otherSideSession.receive<ByteArray>().unwrap { it }

        otherSideSession.send(ourAttestationBytes)

        return theirAttestationBytes
    }
}
