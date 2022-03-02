package net.corda.core.conclave.common

/**
 * An [EnclaveCommand] that instructs the enclave to initialise a post office to a remote enclave using the serialized
 * attestation contained in the mail body (requires an enclave host to be registered).
 */
class InitPostOfficeToRemoteEnclave : EnclaveCommand

class VerifyUnencryptedTx : EnclaveCommand