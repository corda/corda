package net.corda.attestation.message.ias

enum class ManifestStatus {
    OK,
    UNKNOWN,
    INVALID,
    OUT_OF_DATE,
    REVOKED,
    RL_VERSION_MISMATCH
}