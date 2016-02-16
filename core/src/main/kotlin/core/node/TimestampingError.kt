package core.node

sealed class TimestampingError : Exception() {
    class RequiresExactlyOneCommand : TimestampingError()
    /**
     * Thrown if an attempt is made to timestamp a transaction using a trusted timestamper, but the time on the
     * transaction is too far in the past or future relative to the local clock and thus the timestamper would reject
     * it.
     */
    class NotOnTimeException : TimestampingError()

    /** Thrown if the command in the transaction doesn't list this timestamping authorities public key as a signer */
    class NotForMe : TimestampingError()
}

