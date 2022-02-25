package com.r3.conclave.cordapp.sample.enclave

import com.github.benmanes.caffeine.cache.Caffeine
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.cordapp.common.*
import com.r3.conclave.cordapp.common.dto.ConclaveVerificationResponse
import com.r3.conclave.cordapp.common.dto.VerificationStatus
import com.r3.conclave.cordapp.common.dto.WireTxAdditionalInfo
import com.r3.conclave.enclave.EnclavePostOffice
import com.r3.conclave.mail.EnclaveMail
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.lang.IllegalStateException
import java.security.Security
import java.util.*

/**
 * Provide commands to exchange a randomly generated message and hash with another instance of this enclave.
 */
class EncryptedTxEnclave : CommandEnclave() {

    private lateinit var remoteEnclavePostOffice: EnclavePostOffice

    private val commandToCallableMap = mapOf(
        InitPostOfficeToRemoteEnclave::class.java.name to ::initPostOfficeToRemoteEnclave,
        VerifyUnencryptedTx::class.java.name to ::verifyUnencryptedTransaction
    )

    private val serializerFactoriesForContexts = Caffeine.newBuilder()
            .maximumSize(128)
            .build<SerializationFactoryCacheKey, SerializerFactory>()
            .asMap()

    private var serializationFactoryImpl: SerializationFactoryImpl

    init {
        Security.addProvider(BouncyCastleProvider())

        val serverScheme = AMQPServerSerializationScheme(emptyList(), serializerFactoriesForContexts)
        val clientScheme = AMQPServerSerializationScheme(emptyList(), serializerFactoriesForContexts)

        serializationFactoryImpl = SerializationFactoryImpl().apply {
            registerScheme(serverScheme)
            registerScheme(clientScheme)
        }
    }

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

    /**
     * Convert the serialized [WireTxAdditionalInfo] in the message body to a [LedgerTransaction] and verify.
     * This command replies with a serialized [ConclaveVerificationResponse] indicating if the verification was a
     * success or threw an error.
     * @param mail an incoming [EnclaveMail] containing a serialized [WireTxAdditionalInfo].
     * @param flowId the ID of the flow executing on our host.
     */
    private fun verifyUnencryptedTransaction(mail: EnclaveMail, flowId: String) {
        val txBody = mail.bodyAsBytes

        val wireTx = serializationFactoryImpl.deserialize(
                byteSequence = ByteSequence.of(txBody, 0, txBody.size),
                clazz = WireTxAdditionalInfo::class.java,
                context = AMQP_P2P_CONTEXT)

        val ledgerTx = LedgerTxHelper.toLedgerTxInternal(wireTx)

        val response = serializationFactoryImpl.asCurrent {
            this.withCurrentContext(AMQP_P2P_CONTEXT) {
                try {
                    ledgerTx.verify()
                    ConclaveVerificationResponse(VerificationStatus.SUCCESS, null)
                } catch (e: Exception) {
                    println("Exception while verifying transaction $e")
                    ConclaveVerificationResponse(VerificationStatus.VERIFICATION_ERROR, e.message)
                }
            }
        }

        val serializedResponse = serializationFactoryImpl.serialize(response, AMQP_P2P_CONTEXT).bytes

        val responseBytes = postOffice(mail).encryptMail(serializedResponse)
        postMail(responseBytes, "$flowId:${VerifyUnencryptedTx().serialize()}")
    }
}