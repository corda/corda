package net.corda.core.node.services

import net.corda.core.conclave.common.DummyEnclaveClient
import net.corda.core.conclave.common.EnclaveClient
import net.corda.core.conclave.common.dto.ConclaveLedgerTxModel
import net.corda.core.conclave.common.dto.EncryptedVerifiableTxAndDependencies
import net.corda.core.conclave.common.dto.VerifiableTxAndDependencies
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.EncryptedTransaction

class EncryptedTransactionService(val enclaveClient: EnclaveClient = DummyEnclaveClient()) : SingletonSerializeAsToken() {

    fun getEnclaveInstance() : ByteArray {
        return enclaveClient.getEnclaveInstanceInfo()
    }

    fun enclaveVerifyAndEncrypt(txAndDependencies : VerifiableTxAndDependencies, checkSufficientSignatures: Boolean = true): EncryptedTransaction {
        return enclaveClient.enclaveVerifyAndEncrypt(txAndDependencies, checkSufficientSignatures)
    }

    fun enclaveVerifyAndEncrypt(encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies) : EncryptedTransaction {
        return enclaveClient.enclaveVerify(encryptedTxAndDependencies)
    }

    fun encryptTransactionForLocal(encryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        return enclaveClient.encryptTransactionForLocal(encryptedTransaction)
    }

    fun encryptTransactionForRemote(conclaveLedgerTxModel: ConclaveLedgerTxModel,
                                    remoteAttestation: ByteArray): EncryptedTransaction {
        return enclaveClient.encryptTransactionForRemote(conclaveLedgerTxModel, remoteAttestation)
    }

    fun encryptTransactionForRemote(encryptedTransaction: EncryptedTransaction,
                                    remoteAttestation: ByteArray): EncryptedTransaction {
        return enclaveClient.encryptTransactionForRemote(encryptedTransaction, remoteAttestation)
    }
}