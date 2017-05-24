@file:JvmName("Enclavelet")

package com.r3.enclaves.txverify

import com.esotericsoftware.minlog.Log
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.AttachmentsStorageService
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.createTestKryo
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.WireTransaction
import org.bouncycastle.asn1.x500.X500Name
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate

// This file implements the functionality of the SGX transaction verification enclave.

private class ServicesForVerification(dependenciesList: List<WireTransaction>, attachments: Array<ByteArray>) : ServicesForResolution, IdentityService, AttachmentsStorageService, AttachmentStorage {
    override val attachmentsClassLoaderEnabled: Boolean
        get() = TODO("not implemented")
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
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) = TODO("not implemented")

    override fun registerIdentity(party: net.corda.core.identity.Party) = TODO("not implemented")
    override fun registerPath(trustedRoot: X509Certificate, anonymousParty: AnonymousParty, path: CertPath) = TODO("not implemented")

    override fun partyFromKey(key: PublicKey): net.corda.core.identity.Party? = null
    override fun partyFromName(name: String): net.corda.core.identity.Party? = null
    override fun partyFromX500Name(principal: X500Name): net.corda.core.identity.Party? = null
    override fun partyFromAnonymous(party: AbstractParty): Party? = null
    override fun pathForAnonymous(anonymousParty: AnonymousParty): CertPath? = null

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