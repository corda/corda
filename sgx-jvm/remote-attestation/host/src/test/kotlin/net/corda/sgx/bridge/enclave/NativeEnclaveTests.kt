package net.corda.sgx.bridge.enclave

import net.corda.sgx.bridge.EnclaveConfiguration
import net.corda.sgx.bridge.attestation.NativeAttestationEnclave
import net.corda.sgx.enclave.SgxStatus
import org.junit.Assert.*
import org.junit.Test

@Suppress("KDocMissingDocumentation")
class NativeEnclaveTests {

    @Test
    fun `cannot create enclave without valid executable`() {
        val enclave = NativeAttestationEnclave("/somewhere/over/the/rainbow")
        assertTrue(enclave.isFresh())
        assertEquals(SgxStatus.ERROR_ENCLAVE_FILE_ACCESS, enclave.create())
    }

    @Test
    fun `can create enclave without platform services`() {
        val enclave = NativeAttestationEnclave(EnclaveConfiguration.path, false)
        assertTrue(enclave.isFresh())
        assertEquals(SgxStatus.SUCCESS, enclave.create())
        assertFalse(enclave.isFresh())
        assertTrue(enclave.destroy())
    }

    @Test
    fun `can create enclave with platform services`() {
        val enclave = NativeAttestationEnclave(EnclaveConfiguration.path, true)
        assertTrue(enclave.isFresh())
        assertEquals(SgxStatus.SUCCESS, enclave.create())
        assertFalse(enclave.isFresh())
        assertTrue(enclave.destroy())
    }

    @Test
    fun `can destroy existing enclave`() {
        val enclave = NativeAttestationEnclave(EnclaveConfiguration.path)
        assertEquals(SgxStatus.SUCCESS, enclave.create())
        assertFalse(enclave.isFresh())
        assertTrue(enclave.destroy())
        assertTrue(enclave.isFresh())
    }

    @Test
    fun `can detect destruction of non-existent enclave`() {
        val enclave = NativeAttestationEnclave(EnclaveConfiguration.path)
        assertTrue(enclave.isFresh())
        assertTrue(enclave.destroy())
    }

    @Test
    fun `can enter enclave that has already been created`() {
        val enclave = NativeAttestationEnclave(EnclaveConfiguration.path)
        assertTrue(enclave.isFresh())

        assertEquals(SgxStatus.SUCCESS, enclave.create())
        assertFalse(enclave.isFresh())

        assertEquals(SgxStatus.SUCCESS, enclave.create())
        assertFalse(enclave.isFresh())

        assertTrue(enclave.destroy())
    }

    @Test
    fun `can create same enclave twice`() {
        val enclaveA = NativeAttestationEnclave(EnclaveConfiguration.path)
        assertTrue(enclaveA.isFresh())
        assertEquals(SgxStatus.SUCCESS, enclaveA.create())
        assertFalse(enclaveA.isFresh())
        val identifierA = enclaveA.identifier

        val enclaveB = NativeAttestationEnclave(EnclaveConfiguration.path)
        assertTrue(enclaveB.isFresh())
        assertEquals(SgxStatus.SUCCESS, enclaveB.create())
        assertFalse(enclaveB.isFresh())
        val identifierB = enclaveB.identifier

        assertNotEquals(identifierA, identifierB)
        assertTrue(enclaveA.destroy())
        assertTrue(enclaveB.destroy())
    }

    @Test
    fun `can activate enclave`() {
        val enclave = NativeAttestationEnclave(EnclaveConfiguration.path)
        enclave.activate() // throws on failure
    }

}