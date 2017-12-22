package net.corda.attestation.host.sgx.bridge.enclave

import net.corda.attestation.host.sgx.bridge.system.NativeSgxSystem
import net.corda.attestation.host.sgx.bridge.wrapper.LaunchToken
import net.corda.attestation.host.sgx.bridge.wrapper.NativeWrapper
import net.corda.attestation.host.sgx.bridge.wrapper.newLaunchToken
import net.corda.attestation.host.sgx.enclave.Enclave
import net.corda.attestation.host.sgx.enclave.EnclaveIdentifier
import net.corda.attestation.host.sgx.enclave.SgxStatus
import net.corda.attestation.host.sgx.system.SgxSystem
import java.nio.file.Path
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Representation of an enclave on an SGX-enabled system.
 *
 * @param enclavePath The path to the signed enclave binary.
 * @param usePlatformServices Whether or not to leverage Intel's platform
 * services (for replay protection in nonce generation, etc.).
 */
open class NativeEnclave @JvmOverloads constructor(
        private val enclavePath: Path,
        private val usePlatformServices: Boolean = false
) : Enclave {

    /**
     * Lock for ensuring single entry of enclave calls.
     */
    protected val lock: Lock = ReentrantLock()

    private var enclaveId: EnclaveIdentifier = 0

    private var launchToken: LaunchToken = newLaunchToken()

    /**
     * The SGX-enabled system on which this enclave is running.
     */
    override val system: SgxSystem
        get() = NativeSgxSystem()

    /**
     * The enclave identifier.
     */
    override val identifier: EnclaveIdentifier
        get() = enclaveId

    /**
     * Create enclave used for remote attestation, and consequently for secret
     * sealing and unsealing.
     */
    override fun create(): SgxStatus {
        lock.withLock {
            val result = NativeWrapper.createEnclave(
                enclavePath.toString(), // The path to the signed enclave binary
                usePlatformServices, // Whether to use Intel's services
                launchToken // New or pre-existing launch token.
            )
            val status = SgxSystem.statusFromCode(result.result)
            if (status == SgxStatus.ERROR_ENCLAVE_LOST) {
                // If the enclave was lost, we need to destroy it. Not doing so
                // will result in EPC memory leakage that could prevent
                // subsequent enclaves from loading.
                destroy()
            }
            enclaveId = result.identifier
            launchToken = result.token
            return status
        }
    }

    /**
     * Destroy enclave if running.
     */
    override fun destroy(): Boolean {
        lock.withLock {
            if (enclaveId != 0L) {
                // Only attempt to destroy enclave if one has been created
                val result = NativeWrapper.destroyEnclave(enclaveId)
                enclaveId = 0L
                launchToken = newLaunchToken()
                return result
            }
            return true
        }
    }

    /**
     * Check whether the enclave has been run before or not.
     */
    override fun isFresh(): Boolean {
        lock.withLock {
            val nullByte = 0.toByte()
            return identifier == 0L &&
                    (0 until launchToken.size).all { launchToken[it] == nullByte }
        }
    }

}
