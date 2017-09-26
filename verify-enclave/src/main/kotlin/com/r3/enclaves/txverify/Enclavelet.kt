@file:JvmName("Enclavelet")

package com.r3.enclaves.txverify

import com.esotericsoftware.minlog.Log
import net.corda.core.contracts.HashAttachmentConstraint
import net.corda.core.crypto.sha256
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.WireTransaction
import net.corda.nodeapi.internal.serialization.GeneratedAttachment
import java.io.File

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
    val req = reqBytes.deserialize<TransactionVerificationRequest>()
    val wtxToVerify = req.wtxToVerify.deserialize()
    val dependencies = req.dependencies.map { it.deserialize() }.associateBy { it.id }
    val ltx = wtxToVerify.toLedgerTransaction(
            resolveIdentity = { null },
            resolveAttachment = { secureHash -> req.attachments.filter { it.sha256() == secureHash }.map { GeneratedAttachment(it) }.singleOrNull() },
            resolveStateRef = { dependencies[it.txhash]?.outputs?.get(it.index) },
            resolveContractAttachment = { (it.constraint as HashAttachmentConstraint).attachmentId }
    )
    ltx.verify()
}

// Note: This is only here for debugging purposes
fun main(args: Array<String>) {
    Log.TRACE()
    Class.forName("com.r3.enclaves.txverify.KryoVerifierSerializationScheme")
    val reqBytes = File(args[0]).readBytes()
    verifyInEnclave(reqBytes)
}