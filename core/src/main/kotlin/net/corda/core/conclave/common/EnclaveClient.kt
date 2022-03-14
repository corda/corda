package net.corda.core.conclave.common

import net.corda.core.conclave.common.dto.ConclaveLedgerTxModel
import net.corda.core.conclave.common.dto.VerifiableTxAndDependencies
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.EncryptedTransaction

@CordaService
interface EnclaveClient {

    fun getEnclaveInstanceInfo() : ByteArray

    fun enclaveVerifyAndEncrypt(txAndDependencies : VerifiableTxAndDependencies): EncryptedTransaction

    fun enclaveVerify(encryptedTransaction : EncryptedTransaction): EncryptedTransaction

    fun encryptTransactionForLocal(encryptedTransaction: EncryptedTransaction): EncryptedTransaction


    fun encryptTransactionForRemote(conclaveLedgerTxModel: ConclaveLedgerTxModel,
                                    remoteAttestation: ByteArray): EncryptedTransaction

    fun encryptTransactionForRemote(encryptedTransaction: EncryptedTransaction,
                                    remoteAttestation: ByteArray): EncryptedTransaction
}

class DummyEnclaveClient: EnclaveClient, SingletonSerializeAsToken() {

    override fun getEnclaveInstanceInfo(): ByteArray {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun enclaveVerifyAndEncrypt(txAndDependencies: VerifiableTxAndDependencies): EncryptedTransaction {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun enclaveVerify(encryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun encryptTransactionForLocal(encryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun encryptTransactionForRemote(conclaveLedgerTxModel: ConclaveLedgerTxModel,
                                             remoteAttestation: ByteArray): EncryptedTransaction {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun encryptTransactionForRemote(encryptedTransaction: EncryptedTransaction,
                                             remoteAttestation: ByteArray): EncryptedTransaction {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }
}