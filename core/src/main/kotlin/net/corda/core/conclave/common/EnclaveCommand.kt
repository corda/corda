package net.corda.core.conclave.common

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class EnclaveCommand {
    IsNodeKeyRegisteredWithEnclave,
    RequestChallenge,
    VerifyChallenge,
    DecryptInputsAndRefsForNode,
    InitPostOfficeToRemoteEnclave,
    VerifyTransactionWithSignatures,
    VerifyTransactionWithoutSignatures,
    EncryptTransactionForLocal,
    EncryptLedgerTransactionForRemote,
    EncryptVerifiedTransactionForRemote,
    DeserializeTransactionReturnHash,
    GetLocalEncryptedTransaction;

    fun serialize(): String {
        return this.name
    }
}

fun String.toEnclaveCommand(): EnclaveCommand {
    return EnclaveCommand.valueOf(this)
}
