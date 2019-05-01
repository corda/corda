package net.corda.nodeapi.internal.protonwrapper.netty

import com.typesafe.config.Config
import net.corda.nodeapi.internal.config.ConfigParser
import net.corda.nodeapi.internal.config.CustomConfigParser

/**
 * Data structure for controlling the wy how Certificate Revocation Lists are handled.
 */
@CustomConfigParser(RevocationConfigParser::class)
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

class RevocationConfigParser : ConfigParser<RevocationConfig> {
    override fun parse(config: Config): RevocationConfig {
        val mode = config.getString("mode")
        return when (mode.toUpperCase()) {
            "SOFT_FAIL" -> RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL)
            "HARD_FAIL" -> RevocationConfigImpl(RevocationConfig.Mode.HARD_FAIL)
            "OFF" -> RevocationConfigImpl(RevocationConfig.Mode.OFF)
            else -> throw IllegalArgumentException("Unsupported mode : '$mode'")
        }
    }
}