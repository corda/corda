package net.corda.core.node.services

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import net.corda.core.conclave.common.DummyCordaEnclaveClient
import net.corda.core.conclave.common.CordaEnclaveClient
import net.corda.core.conclave.common.dto.ConclaveLedgerTxModel
import net.corda.core.conclave.common.dto.EncryptedVerifiableTxAndDependencies
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.FlowStateMachine
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.EncryptedTransaction
import java.util.*

class EncryptedTransactionService(val enclaveClient: CordaEnclaveClient = DummyCordaEnclaveClient(CordaX500Name("PartyDummy", "London", "GB" ))) : SingletonSerializeAsToken() {

    @Suspendable
    private fun getCurrentFlowIdOrGenerateNewInvokeId(): UUID {
        val currentFiber = Fiber.currentFiber() as? FlowStateMachine<*>
        return currentFiber?.id?.uuid ?: UUID.randomUUID()
    }

    @Suspendable
    fun getEnclaveInstance(): ByteArray {
        return enclaveClient.getEnclaveInstanceInfo()
    }

    @Suspendable
    fun registerRemoteEnclaveInstanceInfo(flowId: UUID, remoteAttestation: ByteArray) {
        enclaveClient.registerRemoteEnclaveInstanceInfo(flowId, remoteAttestation)
    }

    @Suspendable
    fun enclaveVerifyWithoutSignatures(encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies) {
        return enclaveClient.enclaveVerifyWithoutSignatures(getCurrentFlowIdOrGenerateNewInvokeId(), encryptedTxAndDependencies)
    }

    @Suspendable
    fun enclaveVerifyWithSignatures(encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies): EncryptedTransaction {
        return enclaveClient.enclaveVerifyWithSignatures(getCurrentFlowIdOrGenerateNewInvokeId(), encryptedTxAndDependencies)
    }

    @Suspendable
    fun encryptTransactionForLocal(encryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        return enclaveClient.encryptTransactionForLocal(getCurrentFlowIdOrGenerateNewInvokeId(), encryptedTransaction)
    }

    @Suspendable
    fun encryptTransactionForRemote(flowId: UUID, conclaveLedgerTxModel: ConclaveLedgerTxModel, theirAttestationBytes: ByteArray): EncryptedTransaction {
        return enclaveClient.encryptConclaveLedgerTxForRemote(flowId, conclaveLedgerTxModel, theirAttestationBytes)
    }

    @Suspendable
    fun encryptTransactionForRemote(flowId: UUID, encryptedTransaction: EncryptedTransaction, theirAttestationBytes: ByteArray): EncryptedTransaction {
        return enclaveClient.encryptEncryptedTransactionForRemote(flowId, encryptedTransaction, theirAttestationBytes)
    }
}