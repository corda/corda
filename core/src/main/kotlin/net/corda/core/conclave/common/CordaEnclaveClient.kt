package net.corda.core.conclave.common

import net.corda.core.conclave.common.dto.ConclaveLedgerTxModel
import net.corda.core.conclave.common.dto.EncryptedVerifiableTxAndDependencies
import net.corda.core.conclave.common.dto.InputsAndRefsForNode
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.EncryptedTransaction
import java.util.*

/**
 * A structure to wrap any payload with an associated flowID. This will be serialised and used to send data to an enclave as it expects all
 * data to arrive in a single ByteArray
 */

abstract class CordaEnclaveClient(val x500: CordaX500Name, val serviceHub: ServicesForResolution? = null): SingletonSerializeAsToken() {

    // Some exceptions we could throw [TBD - do we want this?]
    class RemoteAttestationException(description: String) : FlowException(description)
    class VerificationException(description: String) : FlowException(description)

    /**
     * Return the [EnclaveInstanceInfo] object serialized as a ByteArray.
     *
     * This will be sent to the remote node so that their enclave can check that it is happy that we are a legitimate Enclave
     *
     * Serializing as a byte array allows us to more easily write mock enclaves (with no actual Conclave), as we don't have to create
     * our own mock [EnclaveInstanceInfo] objects. In theory this could be generalised to exchange a generic set of 'handshake' bytes,
     * of which an enclave instance info is just one type of handshake
     */
    abstract fun getEnclaveInstanceInfo() : ByteArray

    /**
     * Register a remote enclave's [EnclaveInstanceInfo] with our own enclave. From this point on, our enclave will cache this information,
     * and use it whenever it needs it when dealing with requests from the same flowID
     *
     * @param invokeId flowId and the remote attestation as a [ByteArray] wrapped in a [FlowIdAndPayload] object
     *
     * @throws [RemoteAttestationException] if our enclave does not accept the attestation
     */
    @Throws(RemoteAttestationException::class)
    abstract fun registerRemoteEnclaveInstanceInfo(invokeId: UUID, payload: ByteArray)

    /**
     * Verify an encrypted transaction (supplied with its dependencies), without checking the signatures. This would be used during
     * [CollectSignaturesFlow], where we need to verify a transaction, but it is not fully signed (e.g. we haven't signed it yet, the
     * notary hasn't signed it yet, and possibly other parties).
     *
     * We do not return a signed and encrypted transaction from this call, as we will not be storing these transactions long term. They
     * are not fully signed at this stage therefore cannot be committed to the ledger.
     *
     * @param encryptedTxAndDependencies the encrypted transaction to verify
     *
     * @throws [VerificationException] if verification failed
     */
    @Throws(VerificationException::class)
    abstract fun enclaveVerifyWithoutSignatures(invokeId: UUID, encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies)

    /**
     * Verify an encrypted transaction (supplied with its dependencies) and also check the signatures. This would be used during
     * backchain resolution, where we need to verify a transaction fully, but only have it in an encrypted form.
     *
     * We return a signed and encrypted transaction from this call, as we can store that and supply it to the enclave whenever we need it
     * as proof that we have verified a transaction previously. I.e. our own signature over the id means that we (as an enclave) have
     * previously verified this transaction. The encryptedBytes in the returned [EncryptedTransaction] will remain unchanged as the
     * transaction was already encrypted, however, the signatures will now contain the signature we created.
     *
     * @param encryptedTxAndDependencies the encrypted transaction to verify
     *
     * @return an [EncryptedTransaction] which will be an encrypted version of the transaction, along with our enclave's signature
     * over the transaction id.
     *
     * @throws [VerificationException] if verification failed
     */
    @Throws(VerificationException::class)
    abstract fun enclaveVerifyWithSignatures(invokeId: UUID, encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies): EncryptedTransaction

    /**
     * When we receive an encrypted transaction from another node, before we store it we will want to encrypt it with our long term
     * storage key. This function provides the encrypted transaction supplied by the remote node, and re-encrypts it with whatever key
     * our remote enclave wants to use for long term storage.
     *
     * @param remoteEncryptedTransaction the remotely encrypted transaction
     *
     * @return an [EncryptedTransaction] the transaction encrypted with our enclave's long term storage key
     */
    abstract fun encryptTransactionForLocal(invokeId: UUID, remoteEncryptedTransaction: EncryptedTransaction): EncryptedTransaction

    /**
     * During backchain resolution, when we send an transaction to another node, we need to encrypt it with a post office related to their
     * enclave's remote attestation. This function takes an unencrypted transaction (as a [ConclaveLedgerTxModel]) and returns
     * an [EncryptedTransaction] which contains that transaction, but encrypted for the remote enclave.
     *
     * @param conclaveLedgerTx our local unencrypted transaction wrapped in a [FlowIdAndPayload] class so that the remote
     * enclave can identify which cached remote attestation to use.
     *
     * @return an [EncryptedTransaction] the transaction encrypted according to the remote enclave's remote attestation. Note that we do
     * not need our enclave to sign this encrypted transaction, as our signature is only relevant to our own enclave.
     */
    abstract fun encryptConclaveLedgerTxForRemote(invokeId: UUID, conclaveLedgerTx: ConclaveLedgerTxModel, theirAttestationBytes: ByteArray): EncryptedTransaction

    /**
     * During backchain resolution, when we send an transaction to another node, we need to encrypt it with a post office related to their
     * enclave's remote attestation. This function takes an encrypted transaction (as a [EncryptedTransaction]) and returns
     * an [EncryptedTransaction] which contains that transaction, but encrypted for the remote enclave.
     *
     * @param locallyEncryptedTx our local encrypted transaction wrapped in a [FlowIdAndPayload] class so that the remote
     * enclave can identify which cached remote attestation to use.
     *
     * @return an [EncryptedTransaction] the transaction re-encrypted according to the remote enclave's remote attestation. Note that we do
     * not need our enclave to sign this encrypted transaction, as our signature is only relevant to our own enclave.
     */
    abstract fun encryptEncryptedTransactionForRemote(invokeId: UUID, locallyEncryptedTx: EncryptedTransaction, theirAttestationBytes: ByteArray): EncryptedTransaction

    /**
     * Decrypts inputs and reference states from transaction and returns them in clear text. Only input states where registered node is participant
     * will be returned while all the reference states will be returned.
     *
     * @param encryptedTransaction The [EncryptedTransaction] for which registered node requests decryption from enclave.
     *
     * @return [InputsAndRefsForNode] as arrays of input states and reference states. Input states are filtered by the list of registered participants.
     */
    abstract fun decryptInputAndRefsForNode(encryptedTransaction: EncryptedTransaction): InputsAndRefsForNode
}

class DummyCordaEnclaveClient(x500: CordaX500Name, serviceHub: ServicesForResolution? = null): CordaEnclaveClient(x500, serviceHub) {

    override fun getEnclaveInstanceInfo(): ByteArray {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun registerRemoteEnclaveInstanceInfo(invokeId: UUID, payload: ByteArray) {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun enclaveVerifyWithoutSignatures(invokeId: UUID, encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies) {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun enclaveVerifyWithSignatures(invokeId: UUID, encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies): EncryptedTransaction {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun encryptTransactionForLocal(invokeId: UUID, remoteEncryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun encryptConclaveLedgerTxForRemote(invokeId: UUID, conclaveLedgerTx: ConclaveLedgerTxModel, theirAttestationBytes: ByteArray): EncryptedTransaction {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun encryptEncryptedTransactionForRemote(invokeId: UUID, locallyEncryptedTx: EncryptedTransaction, theirAttestationBytes: ByteArray): EncryptedTransaction {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun decryptInputAndRefsForNode(encryptedTransaction: EncryptedTransaction): InputsAndRefsForNode {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }
}