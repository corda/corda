package com.r3.corda.doorman

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import org.bouncycastle.cert.X509CertificateHolder
import java.io.ByteArrayInputStream
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Convert commandline arguments to [Config] object will allow us to use kotlin delegate with [ConfigHelper].
 */
object OptionParserHelper {
    fun Array<out String>.toConfigWithOptions(registerOptions: OptionParser.() -> Unit): Config {
        val parser = OptionParser()
        val helpOption = parser.acceptsAll(listOf("h", "?", "help"), "show help").forHelp();
        registerOptions(parser)
        val optionSet = parser.parse(*this)
        // Print help and exit on help option.
        if (optionSet.has(helpOption)) {
            throw ShowHelpException(parser)
        }
        // Convert all command line options to Config.
        return ConfigFactory.parseMap(parser.recognizedOptions().mapValues {
            val optionSpec = it.value
            if (optionSpec is ArgumentAcceptingOptionSpec<*> && !optionSpec.requiresArgument() && optionSet.has(optionSpec)) null else optionSpec.value(optionSet)
        }.filterValues { it != null })
    }
}

class ShowHelpException(val parser: OptionParser) : Exception()

object CertificateUtilities {
    fun toX509Certificate(byteArray: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X509").generateCertificate(ByteArrayInputStream(byteArray)) as X509Certificate
    }
}

fun X509CertificateHolder.toX509Certificate(): Certificate = CertificateUtilities.toX509Certificate(encoded)

fun buildCertPath(vararg certificates: Certificate): CertPath {
    return CertificateFactory.getInstance("X509").generateCertPath(certificates.asList())
}