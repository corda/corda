package net.corda.sgx.bridge

object EnclaveConfiguration {

    private val dir: String = System.getProperty("corda.sgx.enclave.path")

    /**
     * The path of the signed attestation enclave binary.
     */
    val path: String = "$dir/corda_sgx_ra_enclave.so"

}
