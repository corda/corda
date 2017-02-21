@file:JvmName("Enclavelet")

package com.r3.enclaves.txverify

import com.esotericsoftware.minlog.Log
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.AttachmentsStorageService
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.*
import net.corda.core.transactions.WireTransaction
import java.io.File
import java.io.InputStream
import java.nio.file.Path

// This file implements the functionality of the SGX transaction verification enclave.

private class ServicesForVerification(dependenciesList: List<WireTransaction>, attachments: Array<ByteArray>) : ServicesForResolution, IdentityService, AttachmentsStorageService, AttachmentStorage {
    override var automaticallyExtractAttachments: Boolean
        get() = throw UnsupportedOperationException()
        set(value) = throw UnsupportedOperationException()
    override var storePath: Path
        get() = throw UnsupportedOperationException()
        set(value) = throw UnsupportedOperationException()

    override fun getAllIdentities() = emptyList<Party>()
    private val dependencies = dependenciesList.associateBy { it.id }

    override val identityService: IdentityService = this
    override val storageService: AttachmentsStorageService = this

    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val dep = dependencies[stateRef.txhash] ?: throw TransactionResolutionException(stateRef.txhash)
        return dep.outputs[stateRef.index]
    }

    // Identities: this stuff will all change in future so we don't bother implementing it now.
    override fun registerIdentity(party: Party) = throw UnsupportedOperationException()
    override fun partyFromKey(key: CompositeKey): Party? = null
    override fun partyFromName(name: String): Party? = null
    override fun partyFromAnonymous(party: AnonymousParty) = null

    // TODO: Implement attachments.
    override val attachments: AttachmentStorage = this
    override fun openAttachment(id: SecureHash): Attachment? = null
    override fun importAttachment(jar: InputStream): SecureHash = throw UnsupportedOperationException("not implemented")
}

/** This is just used to simplify marshalling across the enclave boundary (EDL is a bit awkward) */
@CordaSerializable
class TransactionVerificationRequest(val wtxToVerify: SerializedBytes<WireTransaction>,
                                     val dependencies: Array<SerializedBytes<WireTransaction>>,
                                     val attachments: Array<ByteArray>)

/**
 * Returns either null to indicate success when the transactions are validated, or a string with the
 * contents of the error. Invoked via JNI in response to an enclave RPC. The argument is a serialised
 * [TransactionVerificationRequest].
 *
 * Note that it is assumed the signatures were already checked outside the sandbox: the purpose of this code
 * is simply to check the sensitive, app specific parts of a transaction.
 *
 * TODO: Transaction data is meant to be encrypted under an enclave-private key.
 */
@Throws(Exception::class)
fun verifyInEnclave(reqBytes: ByteArray) {
    val kryo = createTestKryo()
    val req = reqBytes.deserialize<TransactionVerificationRequest>(kryo)
    val wtxToVerify = req.wtxToVerify.deserialize(kryo)
    val services = ServicesForVerification(req.dependencies.map { it.deserialize(kryo) }, req.attachments)
    val ltx = wtxToVerify.toLedgerTransaction(services)
    ltx.verify()
}

// Note: This is only here for debugging purposes
fun main(args: Array<String>) {
    Log.TRACE()
    val reqBytes = File(args[0]).readBytes()
    verifyInEnclave(reqBytes)
}