/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("Enclavelet")

package com.r3.enclaves.txverify

import net.corda.core.contracts.Attachment
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction
import java.io.File

// This file implements the functionality of the SGX transaction verification enclave.

/** This is just used to simplify marshalling across the enclave boundary (EDL is a bit awkward) */
@CordaSerializable
class TransactionVerificationRequest(private val wtxToVerify: SerializedBytes<WireTransaction>,
                                     private val dependencies: Array<SerializedBytes<WireTransaction>>,
                                     val attachments: Array<ByteArray>) {
    fun toLedgerTransaction(): LedgerTransaction {
        val deps = dependencies.map { it.deserialize() }.associateBy(WireTransaction::id)
        val attachments = attachments.map { it.deserialize<Attachment>() }
        val attachmentMap = attachments.associateBy(Attachment::id)
        val contractAttachmentMap = attachments.mapNotNull { it as? MockContractAttachment }.associateBy(MockContractAttachment::contract)
        return wtxToVerify.deserialize().toLedgerTransaction(
            resolveIdentity = { null },
            resolveAttachment = { attachmentMap[it] },
            resolveStateRef = { deps[it.txhash]?.outputs?.get(it.index) },
            resolveContractAttachment = { contractAttachmentMap[it.contract]?.id }
        )
    }
}

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
    var ex: Throwable? = null
    val ltx = deserialise(reqBytes)
    // Prevent this thread from linking new classes against any
    // blacklisted classes, e.g. ones needed by Kryo or by the
    // JVM itself. Note that java.lang.Thread is also blacklisted.
    Thread {
        ltx.verify()
    }.apply {
        startClassBlacklisting(this)
        setUncaughtExceptionHandler { _, e -> ex = e }
        start()
        join()
    }
    throw ex ?: return
}

private fun startClassBlacklisting(t: Thread) {
    val systemClassLoader = ClassLoader.getSystemClassLoader()
    systemClassLoader.javaClass.getMethod("startBlacklisting", t.javaClass).apply {
        invoke(systemClassLoader, t)
    }
}

private fun deserialise(reqBytes: ByteArray): LedgerTransaction {
    return reqBytes.deserialize<TransactionVerificationRequest>()
                .toLedgerTransaction()
}

// Note: This is only here for debugging purposes
fun main(args: Array<String>) {
    Class.forName("com.r3.enclaves.txverify.EnclaveletSerializationScheme")
    val reqBytes = File(args[0]).readBytes()
    verifyInEnclave(reqBytes)
}