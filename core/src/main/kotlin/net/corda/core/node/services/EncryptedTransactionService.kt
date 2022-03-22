package net.corda.core.node.services

import co.paralleluniverse.fibers.Fiber
import net.corda.core.conclave.common.DummyCordaEnclaveClient
import net.corda.core.conclave.common.CordaEnclaveClient
import net.corda.core.conclave.common.dto.ConclaveLedgerTxModel
import net.corda.core.conclave.common.dto.EncryptedVerifiableTxAndDependencies
import net.corda.core.internal.FlowStateMachine
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.EncryptedTransaction
import java.util.*

class EncryptedTransactionService(val enclaveClient: CordaEnclaveClient = DummyCordaEnclaveClient()) : SingletonSerializeAsToken() {

    private fun getCurrentFlowIdOrGenerateNewInvokeId(): UUID {
        val currentFiber = Fiber.currentFiber() as? FlowStateMachine<*>
        return currentFiber?.id?.uuid ?: UUID.randomUUID()
    }

    fun getEnclaveInstance(): ByteArray {
        return enclaveClient.getEnclaveInstanceInfo()
    }

    fun registerRemoteEnclaveInstanceInfo(flowId: UUID, remoteAttestation: ByteArray) {
        enclaveClient.registerRemoteEnclaveInstanceInfo(flowId, remoteAttestation)
    }

    fun enclaveVerifyWithoutSignatures(encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies) {
        return enclaveClient.enclaveVerifyWithoutSignatures(getCurrentFlowIdOrGenerateNewInvokeId(), encryptedTxAndDependencies)
    }

    fun enclaveVerifyWithSignatures(encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies): EncryptedTransaction {
        return enclaveClient.enclaveVerifyWithSignatures(getCurrentFlowIdOrGenerateNewInvokeId(), encryptedTxAndDependencies)
    }

    fun encryptTransactionForLocal(encryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        return enclaveClient.encryptTransactionForLocal(getCurrentFlowIdOrGenerateNewInvokeId(), encryptedTransaction)
    }

    fun encryptTransactionForRemote(flowId: UUID, conclaveLedgerTxModel: ConclaveLedgerTxModel): EncryptedTransaction {
        return enclaveClient.encryptConclaveLedgerTxForRemote(flowId, conclaveLedgerTxModel)
    }

    fun encryptTransactionForRemote(flowId: UUID, encryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        return enclaveClient.encryptEncryptedTransactionForRemote(flowId, encryptedTransaction)
    }
}