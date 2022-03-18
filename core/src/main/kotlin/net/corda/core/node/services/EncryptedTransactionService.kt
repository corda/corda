package net.corda.core.node.services

import net.corda.core.conclave.common.DummyEnclaveClient
import net.corda.core.conclave.common.EnclaveClient
import net.corda.core.conclave.common.FlowIdAndPayload
import net.corda.core.conclave.common.dto.ConclaveLedgerTxModel
import net.corda.core.conclave.common.dto.EncryptedVerifiableTxAndDependencies
import net.corda.core.conclave.common.dto.VerifiableTxAndDependencies
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.EncryptedTransaction
import java.util.*

class EncryptedTransactionService(val enclaveClient: EnclaveClient = DummyEnclaveClient()) : SingletonSerializeAsToken() {

    fun getEnclaveInstance() : ByteArray {
        return enclaveClient.getEnclaveInstanceInfo()
    }

    fun registerRemoteEnclaveInstanceInfo(flowId : UUID, remoteAttestation: ByteArray) {
        enclaveClient.registerRemoteEnclaveInstanceInfo(FlowIdAndPayload(flowId, remoteAttestation))
    }

    fun enclaveVerifyWithoutSignatures(txAndDependencies : VerifiableTxAndDependencies) {
        return enclaveClient.enclaveVerifyWithoutSignatures(txAndDependencies)
    }

    fun enclaveVerifyWithSignatures(txAndDependencies : VerifiableTxAndDependencies) : EncryptedTransaction {
        return enclaveClient.enclaveVerifyWithSignatures(txAndDependencies)
    }

    fun enclaveVerifyWithSignatures(encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies) : EncryptedTransaction {
        return enclaveClient.enclaveVerifyWithSignatures(encryptedTxAndDependencies)
    }

    fun encryptTransactionForLocal(encryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        return enclaveClient.encryptTransactionForLocal(encryptedTransaction)
    }

    fun encryptTransactionForRemote(flowId: UUID, conclaveLedgerTxModel: ConclaveLedgerTxModel): EncryptedTransaction {
        return enclaveClient.encryptConclaveLedgerTxForRemote(FlowIdAndPayload(flowId,conclaveLedgerTxModel))
    }

    fun encryptTransactionForRemote(flowId: UUID, encryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        return enclaveClient.encryptEncryptedTransactionForRemote(FlowIdAndPayload(flowId, encryptedTransaction))
    }
}