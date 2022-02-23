package com.r3.conclave.cordapp.sample.enclave

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.cordapp.common.*
import com.r3.conclave.enclave.EnclavePostOffice
import com.r3.conclave.mail.EnclaveMail
import java.lang.IllegalStateException
import java.util.*

/**
 * Provide commands to exchange a randomly generated message and hash with another instance of this enclave.
 */
class EncryptedTxEnclave : CommandEnclave() {

    private lateinit var remoteEnclavePostOffice: EnclavePostOffice

    private val commandToCallableMap = mapOf(
        InitPostOfficeToRemoteEnclave::class.java.name to ::initPostOfficeToRemoteEnclave
    )

    override fun receiveMail(mail: EnclaveMail, flowId: String, route: EnclaveCommand) {
        if (commandToCallableMap[route.javaClass.name] != null) {
            commandToCallableMap[route.javaClass.name]!!.invoke(mail, flowId)
        } else {
            throw IllegalArgumentException(
                "No callable registered for command $route in enclave: ${this.javaClass.simpleName}")
        }
    }

    /**
     * Deserialize the enclave attestation and create a post office to the remote enclave.
     * @param mail an incoming [EnclaveMail] containing the attestation to deserialize.
     * @param flowId the ID of the flow executing on our host.
     * @throws [IllegalStateException] if the remote enclave has already been initialised, or if the host identity has
     *  not been registered.
     * @throws [IllegalArgumentException] if the mail sender is not the authenticated host key.
     */
    private fun initPostOfficeToRemoteEnclave(mail: EnclaveMail, flowId: String) {
        if (this::remoteEnclavePostOffice.isInitialized) {
            throw IllegalStateException("Post office for remote enclave has already been initialized")
        } else if (mail.authenticatedSender != authenticatedHostKey) {
            throw IllegalArgumentException("Mail sender does not match the authenticated host key")
        }

        val attestation = EnclaveInstanceInfo.deserialize(mail.bodyAsBytes)
        // TODO: should we check an enclave constraint?

        remoteEnclavePostOffice = postOffice(attestation, flowId)
    }
}