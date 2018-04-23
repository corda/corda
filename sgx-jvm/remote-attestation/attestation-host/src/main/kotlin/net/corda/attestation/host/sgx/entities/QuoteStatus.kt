package net.corda.attestation.host.sgx.entities

/**
 * The status of an enclave's quote as it has been processed by the Intel
 * Attestation service.
 *
 * @property description A human-readable description of the status code.
 */
enum class QuoteStatus(val description: String) {

    /**
     * EPID signature of the ISV enclave quote was verified correctly and the
     * TCB level of the SGX platform is up-to- date.
     */
    OK("EPID signature of the ISV enclave quote was verified correctly and " +
            "the TCB level of the SGX platform is up-to-date."),

    /**
     * EPID signature of the ISV enclave quote was invalid. The content of the
     * quote is not trustworthy.
     */
    SIGNATURE_INVALID("EPID signature of the ISV enclave quote was invalid."),

    /**
     * The EPID group has been revoked. When this value is returned, the
     * revocation reason field of the Attestation Verification Report will
     * contain a revocation reason code for this EPID group as reported in the
     * EPID Group CRL. The content of the quote is not trustworthy.
     */
    GROUP_REVOKED("The EPID group has been revoked."),

    /**
     * The EPID private key used to sign the quote has been revoked by
     * signature. The content of the quote is not trustworthy.
     */
    SIGNATURE_REVOKED("The EPID private key used to sign the quote has been " +
            "revoked by signature."),

    /**
     * The EPID private key used to sign the quote has been directly revoked
     * (not by signature). The content of the quote is not trustworthy.
     */
    KEY_REVOKED("The EPID private key used to sign the quote has been " +
            "directly revoked (not by signature)."),

    /**
     * SigRL version in ISV enclave quote does not match the most recent
     * version of the SigRL. In rare situations, after SP retrieved the SigRL
     * from IAS and provided it to the platform, a newer version of the SigRL
     * is made available. As a result, the Attestation Verification Report will
     * indicate SIGRL_VERSION_MISMATCH. SP can retrieve the most recent version
     * of SigRL from the IAS and request the platform to perform remote
     * attestation again with the most recent version of SigRL. If the platform
     * keeps failing to provide a valid quote matching with the most recent
     * version of the SigRL, the content of the quote is not trustworthy.
     */
    SIGRL_VERSION_MISMATCH("SigRL version in ISV enclave quote does not " +
            "match the most recent version of the SigRL."),

    /**
     * The EPID signature of the ISV enclave quote has been verified correctly,
     * but the TCB level of SGX platform is outdated. The platform has not been
     * identified as compromised and thus it is not revoked.  It is up to the
     * Service Provider to decide whether or not to trust the content of the
     * quote.
     */
    GROUP_OUT_OF_DATE("The EPID signature of the ISV enclave quote has " +
            "been verified correctly, but the TCB level of SGX platform " +
            "is outdated.")

}
