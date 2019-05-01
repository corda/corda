package net.corda.nodeapi.internal.protonwrapper.netty

/**
 * Data structure for controlling the wy how Certificate Revocation Lists are handled.
 */
interface RevocationConfig {

    enum class Mode {

        /**
         * @see java.security.cert.PKIXRevocationChecker.Option.SOFT_FAIL
         */
        SOFT_FAIL,

        /**
         * Opposite of SOFT_FAIL - i.e. most rigorous check.
         */
        HARD_FAIL,

        /**
         * Switch CRL check off.
         */
        OFF
    }

    val mode : Mode
}

/**
 * Maintained for legacy purposes to convert old style `crlCheckSoftFail`.
 */
fun Boolean.toRevocationConfig() = if(this) RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL) else RevocationConfigImpl(RevocationConfig.Mode.HARD_FAIL)

data class RevocationConfigImpl(override val mode: RevocationConfig.Mode) : RevocationConfig