package net.corda.attestation.challenger

class ChallengerException(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String) : this(message, null)
}