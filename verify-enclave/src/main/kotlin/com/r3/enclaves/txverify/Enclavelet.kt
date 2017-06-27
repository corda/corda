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
import net.corda.core.identity.PartyAndCertificate
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
    val dependencies = req.dependencies.map { it.deserialize(kryo) }.associateBy { it.id }
    val ltx = wtxToVerify.toLedgerTransaction(
            resolveIdentity = { null },
            resolveAttachment = { null },
            resolveStateRef = { dependencies[it.txhash]?.outputs?.get(it.index) }
    )
    ltx.verify()
}

// Note: This is only here for debugging purposes
fun main(args: Array<String>) {
    Log.TRACE()
    val reqBytes = File(args[0]).readBytes()
    verifyInEnclave(reqBytes)
}