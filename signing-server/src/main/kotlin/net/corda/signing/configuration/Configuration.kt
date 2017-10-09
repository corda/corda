package net.corda.signing.configuration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import net.corda.core.internal.div
import net.corda.node.utilities.X509Utilities
import net.corda.nodeapi.config.parseAs
import net.corda.signing.authentication.AuthMode
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class ShowHelpException(val parser: OptionParser) : Exception()

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
        if (optionSpec is ArgumentAcceptingOptionSpec<*> && !optionSpec.requiresArgument() && optionSet.has(optionSpec)) true else optionSpec.value(optionSet)
    }.filterValues { it != null })
}

/**
 * Configuration parameters.
 */
data class Parameters(val basedir: Path = Paths.get("."),
                      val dataSourceProperties: Properties,
                      val databaseProperties: Properties? = null,
                      val device: String = DEFAULT_DEVICE,
                      val keyStorePass: String? = null,
                      val keyGroup: String = DEFAULT_KEY_GROUP,
                      val keySpecifier: Int = DEFAULT_KEY_SPECIFIER,
                      val rootPrivateKeyPass: String = "",
                      val privateKeyPass: String = "",
                      val certificateName: String = DEFAULT_CERTIFICATE_NAME,
                      val rootCertificateName: String = DEFAULT_ROOT_CERTIFICATE_NAME,
                      val validDays: Int = DEFAULT_VALID_DAYS,
                      val signAuthThreshold: Int = DEFAULT_SIGN_AUTH_THRESHOLD,
                      val keyGenAuthThreshold: Int = DEFAULT_KEY_GEN_AUTH_THRESHOLD,
                      val authMode: AuthMode = DEFAULT_AUTH_MODE,
                      val authKeyFilePath: Path? = DEFAULT_KEY_FILE_PATH,
                      val authKeyFilePass: String? = DEFAULT_KEY_FILE_PASS,
                      val autoUsername: String? = DEFAULT_AUTO_USERNAME) {
    companion object {
        val DEFAULT_DEVICE = "3001@127.0.0.1"
        val DEFAULT_AUTH_MODE = AuthMode.PASSWORD
        val DEFAULT_SIGN_AUTH_THRESHOLD = 2
        val DEFAULT_KEY_GEN_AUTH_THRESHOLD = 2
        val DEFAULT_CERTIFICATE_NAME = X509Utilities.CORDA_INTERMEDIATE_CA
        val DEFAULT_ROOT_CERTIFICATE_NAME = X509Utilities.CORDA_ROOT_CA
        val DEFAULT_VALID_DAYS = 3650
        val DEFAULT_KEY_GROUP = "DEV.DOORMAN"
        val DEFAULT_KEY_SPECIFIER = 1
        val DEFAULT_KEY_FILE_PATH: Path? = null //Paths.get("/Users/michalkit/WinDev1706Eval/Shared/TEST4.key")
        val DEFAULT_KEY_FILE_PASS: String? = null
        val DEFAULT_AUTO_USERNAME: String? = null
    }
}

/**
 * Parses the list of arguments and produces an instance of [Parameters].
 * @param args list of strings corresponding to program arguments
 * @return instance of Parameters produced from [args]
 */
fun parseParameters(vararg args: String): Parameters {
    val argConfig = args.toConfigWithOptions {
        accepts("basedir", "Overriding configuration filepath, default to current directory.").withRequiredArg().defaultsTo(".").describedAs("filepath")
        accepts("configFile", "Overriding configuration file. (default: <<current directory>>/node.conf)").withRequiredArg().describedAs("filepath")
        accepts("device", "CryptoServer device address (default: ${Parameters.DEFAULT_DEVICE})").withRequiredArg().defaultsTo(Parameters.DEFAULT_DEVICE)
        accepts("keyStorePass", "Password for the key store").withRequiredArg().describedAs("password")
        accepts("keyGroup", "CryptoServer key group (default: ${Parameters.DEFAULT_KEY_GROUP})").withRequiredArg().defaultsTo(Parameters.DEFAULT_KEY_GROUP)
        accepts("keySpecifier", "CryptoServer key specifier (default: ${Parameters.DEFAULT_KEY_SPECIFIER})").withRequiredArg().ofType(Int::class.java).defaultsTo(Parameters.DEFAULT_KEY_SPECIFIER)
        accepts("rootPrivateKeyPass", "Password for the root certificate private key").withRequiredArg().describedAs("password")
        accepts("privateKeyPass", "Password for the certificate private key").withRequiredArg().describedAs("password")
        accepts("keyGenAuthThreshold", "Authentication strength threshold for the HSM key generation (default: ${Parameters.DEFAULT_KEY_GEN_AUTH_THRESHOLD})").withRequiredArg().ofType(Int::class.java).defaultsTo(Parameters.DEFAULT_KEY_GEN_AUTH_THRESHOLD)
        accepts("signAuthThreshold", "Authentication strength threshold for the HSM CSR signing (default: ${Parameters.DEFAULT_SIGN_AUTH_THRESHOLD})").withRequiredArg().ofType(Int::class.java).defaultsTo(Parameters.DEFAULT_SIGN_AUTH_THRESHOLD)
        accepts("authMode", "Authentication mode. Allowed values: ${AuthMode.values()} (default: ${Parameters.DEFAULT_AUTH_MODE} )").withRequiredArg().defaultsTo(Parameters.DEFAULT_AUTH_MODE.name)
        accepts("authKeyFilePath", "Key file path when authentication is based on a key file (i.e. authMode=${AuthMode.KEY_FILE.name})").withRequiredArg().describedAs("filepath")
        accepts("authKeyFilePass", "Key file password when authentication is based on a key file (i.e. authMode=${AuthMode.KEY_FILE.name})").withRequiredArg()
        accepts("autoUsername", "Username to be used for certificate signing (if not specified it will be prompted for input)").withRequiredArg()
        accepts("certificateName", "Name of the certificate to be used by this CA (default: ${Parameters.DEFAULT_CERTIFICATE_NAME})").withRequiredArg().defaultsTo(Parameters.DEFAULT_CERTIFICATE_NAME)
        accepts("rootCertificateName", "Name of the root certificate to be used by this CA (default: ${Parameters.DEFAULT_ROOT_CERTIFICATE_NAME})").withRequiredArg().defaultsTo(Parameters.DEFAULT_ROOT_CERTIFICATE_NAME)
        accepts("validDays", "Validity duration in days (default: ${Parameters.DEFAULT_VALID_DAYS})").withRequiredArg().ofType(Int::class.java).defaultsTo(Parameters.DEFAULT_VALID_DAYS)
    }

    val configFile = if (argConfig.hasPath("configFile")) {
        Paths.get(argConfig.getString("configFile"))
    } else {
        Paths.get(argConfig.getString("basedir")) / "signing_service.conf"
    }

    val config = argConfig.withFallback(ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))).resolve()
    return config.parseAs<Parameters>()
}