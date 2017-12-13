package net.corda.sgx.bridge.attestation

import net.corda.sgx.bridge.EnclaveConfiguration
import net.corda.sgx.enclave.ECKey
import net.corda.sgx.enclave.ecKeyComponent
import org.junit.Ignore
import org.junit.Test

@Suppress("KDocMissingDocumentation")
class NativeAttestationEnclaveTests {

    // Dummy key used to initialize the key exchange
    private val challengerKey: ECKey = ECKey(
            ecKeyComponent(
                    0xC0, 0x8C, 0x9F, 0x45, 0x59, 0x1A, 0x9F, 0xAE,
                    0xC5, 0x1F, 0xBC, 0x3E, 0xFB, 0x4F, 0x67, 0xB1,
                    0x93, 0x61, 0x45, 0x9E, 0x30, 0x27, 0x10, 0xC4,
                    0x92, 0x0F, 0xBB, 0xB2, 0x69, 0xB0, 0x16, 0x39
            ),
            ecKeyComponent(
                    0x5D, 0x98, 0x6B, 0x24, 0x2B, 0x52, 0x46, 0x72,
                    0x2A, 0x35, 0xCA, 0xE0, 0xA9, 0x1A, 0x6A, 0xDC,
                    0xB8, 0xEB, 0x32, 0xC8, 0x1C, 0x2B, 0x5A, 0xF1,
                    0x23, 0x1F, 0x6C, 0x6E, 0x30, 0x00, 0x96, 0x4F
            )
    )

    @Test
    fun `can initialize and finalize key exchange without platform services`() {
        val enclave = NativeAttestationEnclave(EnclaveConfiguration.path, false)
        enclave.activate()
        enclave.initializeKeyExchange(challengerKey)
        enclave.finalizeKeyExchange()
        enclave.destroy()
    }

    // TODO Use PSE to protect against replay attacks
    // We currently don't leverage the PSE to protect against replay attacks,
    // etc., and it seems like the box doesn't communicate properly with it.
    // Longer term, we should get this enabled, which will require further
    // changes to the enclave code as well.

    @Ignore("Requires platform services")
    @Test
    fun `can initialize and finalize key exchange with platform services`() {
        val enclave = NativeAttestationEnclave(EnclaveConfiguration.path, true)
        enclave.activate()
        enclave.initializeKeyExchange(challengerKey)
        enclave.finalizeKeyExchange()
        enclave.destroy()
    }

}
