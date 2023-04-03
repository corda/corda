package net.corda.nodeapi.internal.protonwrapper.netty

import com.typesafe.config.Config
import net.corda.nodeapi.internal.config.ConfigParser
import net.corda.nodeapi.internal.config.CustomConfigParser
import net.corda.nodeapi.internal.revocation.CertDistPointCrlSource
import net.corda.nodeapi.internal.revocation.CordaRevocationChecker
import java.security.cert.PKIXRevocationChecker

/**
 * Data structure for controlling the way how Certificate Revocation Lists are handled.
 */
@CustomConfigParser(parser = RevocationConfigParser::class)
// TODO This and RevocationConfigImpl should really be a single sealed data type
interface RevocationConfig {

    enum class Mode {

        /**
         * @see java.security.cert.PKIXRevocationChecker.Option.SOFT_FAIL
         */
        SOFT_FAIL,

        /**
         * Opposite of SOFT_FAIL - i.e. most rigorous check.
         * Among other things, this check requires that CRL checking URL is available on every level of certificate chain.
         * This is also known as Strict mode.
         */
        HARD_FAIL,

        /**
         * CRLs are obtained from external source
         * @see CrlSource
         */
        EXTERNAL_SOURCE,

        /**
         * Switch CRL check off.
         */
        OFF
    }

    val mode: Mode

    /**
     * Optional [CrlSource] which only makes sense with `mode` = `EXTERNAL_SOURCE`
     */
    val externalCrlSource: CrlSource?

    /**
     * Creates a copy of [RevocationConfig] enriched by a [CrlSource].
     */
    fun enrichExternalCrlSource(sourceFunc: (() -> CrlSource)?): RevocationConfig

    fun createPKIXRevocationChecker(): PKIXRevocationChecker {
        return when (mode) {
            Mode.OFF -> AllowAllRevocationChecker
            Mode.EXTERNAL_SOURCE -> CordaRevocationChecker(externalCrlSource!!, softFail = true)
            Mode.SOFT_FAIL -> CordaRevocationChecker(CertDistPointCrlSource(), softFail = true)
            Mode.HARD_FAIL -> CordaRevocationChecker(CertDistPointCrlSource(), softFail = false)
        }
    }
}

/**
 * Maintained for legacy purposes to convert old style `crlCheckSoftFail`.
 */
fun Boolean.toRevocationConfig() = if(this) RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL) else RevocationConfigImpl(RevocationConfig.Mode.HARD_FAIL)

data class RevocationConfigImpl(override val mode: RevocationConfig.Mode, override val externalCrlSource: CrlSource? = null) : RevocationConfig {
    init {
        if (mode == RevocationConfig.Mode.EXTERNAL_SOURCE) {
            requireNotNull(externalCrlSource) { "externalCrlSource must not be null" }
        }
    }

    // TODO This doesn't really need to be a member method. All it does is change externalCrlSource if applicable, which is the same as
    //  just creating a new RevocationConfigImpl with that CrlSource.
    override fun enrichExternalCrlSource(sourceFunc: (() -> CrlSource)?): RevocationConfig {
        return if (mode != RevocationConfig.Mode.EXTERNAL_SOURCE) {
            this
        } else {
            val func = requireNotNull(sourceFunc) { "There should be a way to obtain CrlSource" }
            copy(externalCrlSource = func())
        }
    }
}

class RevocationConfigParser : ConfigParser<RevocationConfig> {
    override fun parse(config: Config): RevocationConfig {
        val oneAndTheOnly = "mode"
        val allKeys = config.entrySet().map { it.key }
        require(allKeys.size == 1 && allKeys.contains(oneAndTheOnly)) {"For RevocationConfig, it is expected to have '$oneAndTheOnly' property only. " +
                "Actual set of properties: $allKeys. Please check 'revocationConfig' section."}
        val mode = config.getString(oneAndTheOnly)
        return when (mode.toUpperCase()) {
            "SOFT_FAIL" -> RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL)
            "HARD_FAIL" -> RevocationConfigImpl(RevocationConfig.Mode.HARD_FAIL)
            "EXTERNAL_SOURCE" -> RevocationConfigImpl(RevocationConfig.Mode.EXTERNAL_SOURCE, null) // null for now till `enrichExternalCrlSource` is called
            "OFF" -> RevocationConfigImpl(RevocationConfig.Mode.OFF)
            else -> throw IllegalArgumentException("Unsupported mode : '$mode'")
        }
    }
}
