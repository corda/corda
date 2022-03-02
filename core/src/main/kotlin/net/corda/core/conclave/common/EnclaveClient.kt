package net.corda.core.conclave.common

import net.corda.core.conclave.common.dto.WireTxAdditionalInfo
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.EncryptedTransaction
import java.lang.UnsupportedOperationException

@CordaService
interface EnclaveClient {

    fun enclaveVerifyAndEncrypt(wireTxAdditionalInfo: WireTxAdditionalInfo): ByteArray

    fun enclaveVerifyAndEncrypt(encryptedTransaction: EncryptedTransaction): ByteArray

}

class DummyEnclaveClient: EnclaveClient, SingletonSerializeAsToken() {
    override fun enclaveVerifyAndEncrypt(wireTxAdditionalInfo: WireTxAdditionalInfo): ByteArray {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }

    override fun enclaveVerifyAndEncrypt(encryptedTransaction: EncryptedTransaction): ByteArray {
        throw UnsupportedOperationException("Add your custom enclave client implementation")
    }
}