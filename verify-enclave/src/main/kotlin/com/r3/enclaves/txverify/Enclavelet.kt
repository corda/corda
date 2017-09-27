@file:JvmName("Enclavelet")

package com.r3.enclaves.txverify

import com.esotericsoftware.minlog.Log
import net.corda.core.contracts.Attachment
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.WireTransaction
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
    val attachments = req.attachments.map { it.deserialize<Attachment>() }
    val attachmentMap = attachments.associateBy(Attachment::id)
    val contractAttachmentMap = attachments.mapNotNull { it as? MockContractAttachment }.associateBy { it.contract }
    val ltx = wtxToVerify.toLedgerTransaction(
            resolveIdentity = { null },
            resolveAttachment = { attachmentMap[it] },
            resolveStateRef = { dependencies[it.txhash]?.outputs?.get(it.index) },
            resolveContractAttachment = { contractAttachmentMap[it.contract]?.id }
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