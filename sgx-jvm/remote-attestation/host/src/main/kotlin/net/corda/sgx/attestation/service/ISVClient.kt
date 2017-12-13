package net.corda.sgx.attestation.service

import net.corda.sgx.attestation.entities.AttestationResult
import net.corda.sgx.attestation.entities.Challenge
import net.corda.sgx.attestation.entities.Quote
import net.corda.sgx.enclave.ECKey
import net.corda.sgx.system.ExtendedGroupIdentifier
import net.corda.sgx.system.GroupIdentifier

/**
 * A proxy providing an interface for calling into required functionality of
 * the remote attestation service.  Communication with the service takes place
 * over HTTPS.
 */
interface ISVClient {

    /**
     * Send request to the remote attestation service asking to get provisioned
     * a challenge.
     *
     * @return A challenge from the attestation service.
     */
    fun requestChallenge(): Challenge

    /**
     * Send request to the remote attestation service to validate the extended
     * group identifier. This corresponds to message 0 in Intel's remote
     * attestation flow.
     *
     * @param extendedGroupId The extended group identifier obtained from
     * calling into the architectural enclave.
     *
     * @return True if the service successfully validated the identifier and is
     * happy to proceed. False otherwise.
     */
    fun validateExtendedGroupIdentifier(
            extendedGroupId: ExtendedGroupIdentifier
    ): Boolean

    /**
     * Send request containing the application enclave's public key and the
     * platform's EPID group identifier. This corresponds to message 1 in
     * Intel's remote attestation flow.
     *
     * @param publicKey The application enclave's public key.
     * @param groupIdentifier The platform's EPID group identifier.
     *
     * @return Details about the service provider, such as its public key, SPID
     * and quote type.
     */
    fun sendPublicKeyAndGroupIdentifier(
            publicKey: ECKey,
            groupIdentifier: GroupIdentifier
    ): ChallengerDetails

    /**
     * Send request containing the generated quote to the service provider so
     * that they can verify the attestation evidence with Intel's attestation
     * service.
     *
     * @param challenge The challenge received in the first step.
     * @param quote The generated quote.
     *
     * @return The outcome of the remote attestation.
     */
    fun submitQuote(
            challenge: Challenge,
            quote: Quote
    ): AttestationResult

}
