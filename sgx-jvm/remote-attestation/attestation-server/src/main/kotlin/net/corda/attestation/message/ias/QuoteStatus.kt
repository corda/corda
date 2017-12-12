package net.corda.attestation.message.ias

enum class QuoteStatus {
    OK,
    SIGNATURE_INVALID,
    GROUP_REVOKED,
    SIGNATURE_REVOKED,
    KEY_REVOKED,
    SIGRL_VERSION_MISMATCH,
    GROUP_OUT_OF_DATE
}