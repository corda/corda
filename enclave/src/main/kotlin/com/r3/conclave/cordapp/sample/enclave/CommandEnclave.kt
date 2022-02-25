package com.r3.conclave.cordapp.sample.enclave

import com.r3.conclave.cordapp.common.EnclaveCommand
import com.r3.conclave.cordapp.common.RegisterHostIdentity
import com.r3.conclave.cordapp.common.getRoutingInfo
import com.r3.conclave.cordapp.common.internal.SenderIdentityImpl
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.enclave.EnclavePostOffice
import java.lang.IllegalStateException
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.logging.Logger

abstract class CommandEnclave : Enclave() {

    // the public key of the authenticated and registered host
    protected lateinit var authenticatedHostKey: PublicKey

    // Post office to communicate with our host
    // To be initialised when registerHostIdentity is called
    protected lateinit var hostPostOffice: EnclavePostOffice

    companion object {
        private const val trustedRootCertificateResourcePath = "/trustedroot.cer"

        @JvmStatic
        val trustedRootCertificate: X509Certificate = run {
            try {
                EncryptedTxEnclave::class.java.getResourceAsStream(trustedRootCertificateResourcePath).use {
                    val certificateFactory = CertificateFactory.getInstance("X509")
                    certificateFactory.generateCertificate(it) as X509Certificate
                }
            } catch (exception: Exception) {
                Logger.getGlobal().severe(
                    "Failed to load trusted root certificate. Please ensure the resource exists and it is not " +
                            "corrupted. Resource path: $trustedRootCertificateResourcePath"
                )
                throw exception
            }
        }
    }

    /**
     * Receive incoming mail and route according to the [EnclaveCommand] class contained in the [routingHint].
     * @param mail the received [EnclaveMail].
     * @param routingHint a [String] containing the executing flowId and enclave command to execute.
     * @throws [IllegalArgumentException] if [routingHint] is null, or cannot be parsed.
     * @throws [IllegalStateException] if attempting to execute a command when the host has not been registered.
     */
    override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
        routingHint ?: throw IllegalArgumentException(
            "routingHint must be set for this enclave: ${this.javaClass.simpleName}")

        val (flowId, route) = getRoutingInfo(routingHint)

        val registeredHost = if (isHostRegistered()) authenticatedHostKey else null
        println("Received route: $route, registered host: $registeredHost")

        if (route is RegisterHostIdentity) {
            registerHostIdentity(mail, flowId)
        } else if (isHostRegistered()) {
            receiveMail(mail, flowId, route)
        } else {
            throw IllegalStateException(
                "Host must be registered before executing enclave commands"
            )
        }
    }

    abstract fun receiveMail(mail: EnclaveMail, flowId: String, route: EnclaveCommand)

    /**
     * Deserialize the [SenderIdentityImpl] from the mail body and authenticate the contained identity.
     * If the sender is trusted and is the signer of the shared secret, register the host's public key and initialise
     * the [hostPostOffice] property. Respond with "ack" if the registration was successful or "nak" otherwise.
     * @param mail an incoming [EnclaveMail] containing a serialized [SenderIdentityImpl] instance.
     * @param flowId the ID of the flow executing on our host.
     */
    protected fun registerHostIdentity(mail: EnclaveMail, flowId: String) {
        val identity = SenderIdentityImpl.deserialize(mail.bodyAsBytes)
        val sharedSecret = mail.authenticatedSender.encoded

        val authenticated = authenticateIdentity(sharedSecret, identity)
        if (authenticated) {
            authenticatedHostKey = mail.authenticatedSender
            hostPostOffice = postOffice(mail)
        }

        val responseString = if (authenticated) "ack" else "nak"

        val reply = postOffice(mail).encryptMail(responseString.toByteArray(Charsets.UTF_8))
        postMail(reply, "$flowId:${RegisterHostIdentity().serialize()}")
    }

    /**
     * Return true if a host has successfully registered and authenticated their identity.
     * @return [Boolean] indicating if a host has registered with this enclave.
     */
    private fun isHostRegistered() = this::authenticatedHostKey.isInitialized

    /**
     * Return true if [identity] is trusted and is the signer of [sharedSecret].
     * @param sharedSecret check identity signature against this shared secret.
     * @param identity the [SenderIdentityImpl] instance to authenticate.
     */
    private fun authenticateIdentity(sharedSecret: ByteArray, identity: SenderIdentityImpl): Boolean {
        val isTrusted = identity.isTrusted(trustedRootCertificate)
        val didSign = identity.didSign(sharedSecret)

        return isTrusted && didSign
    }
}