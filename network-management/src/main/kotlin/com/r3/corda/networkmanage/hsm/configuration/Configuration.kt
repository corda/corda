package com.r3.corda.networkmanage.hsm.configuration

import com.r3.corda.networkmanage.common.utils.toConfigWithOptions
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_AUTH_MODE
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_CSR_CERTIFICATE_NAME
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_DEVICE
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_KEY_GEN_AUTH_THRESHOLD
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_KEY_SPECIFIER
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_ROOT_CERTIFICATE_NAME
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_SIGN_AUTH_THRESHOLD
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_SIGN_INTERVAL
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.internal.div
import net.corda.node.utilities.X509Utilities
import net.corda.nodeapi.config.parseAs
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Configuration parameters.
 */
data class Parameters(val basedir: Path = Paths.get("."),
                      val dataSourceProperties: Properties,
                      val databaseProperties: Properties? = null,
                      val device: String = DEFAULT_DEVICE,
                      // TODO this needs cleaning up after the config-file-only support is implemented
                      val keyGroup: String,
                      val keySpecifier: Int = DEFAULT_KEY_SPECIFIER,
                      val rootPrivateKeyPassword: String,
                      val csrPrivateKeyPassword: String,
                      val csrCertificateName: String = DEFAULT_CSR_CERTIFICATE_NAME,
                      val networkMapCertificateName: String = DEFAULT_NETWORK_MAP_CERTIFICATE_NAME,
                      val networkMapPrivateKeyPassword: String,
                      val rootCertificateName: String = DEFAULT_ROOT_CERTIFICATE_NAME,
                      val validDays: Int,
                      val signAuthThreshold: Int = DEFAULT_SIGN_AUTH_THRESHOLD,
                      val keyGenAuthThreshold: Int = DEFAULT_KEY_GEN_AUTH_THRESHOLD,
                      val authMode: AuthMode = DEFAULT_AUTH_MODE,
                      val authKeyFilePath: Path? = DEFAULT_KEY_FILE_PATH,
                      val authKeyFilePassword: String? = DEFAULT_KEY_FILE_PASSWORD,
                      val autoUsername: String? = DEFAULT_AUTO_USERNAME,
                      // TODO Change this to Duration in the future.
                      val signInterval: Long = DEFAULT_SIGN_INTERVAL) {
    companion object {
        val DEFAULT_DEVICE = "3001@127.0.0.1"
        val DEFAULT_AUTH_MODE = AuthMode.PASSWORD
        val DEFAULT_SIGN_AUTH_THRESHOLD = 2
        val DEFAULT_KEY_GEN_AUTH_THRESHOLD = 2
        val DEFAULT_CSR_CERTIFICATE_NAME = X509Utilities.CORDA_INTERMEDIATE_CA
        val DEFAULT_ROOT_CERTIFICATE_NAME = X509Utilities.CORDA_ROOT_CA
        val DEFAULT_KEY_SPECIFIER = 1
        val DEFAULT_KEY_FILE_PATH: Path? = null //Paths.get("/Users/michalkit/WinDev1706Eval/Shared/TEST4.key")
        val DEFAULT_KEY_FILE_PASSWORD: String? = null
        val DEFAULT_AUTO_USERNAME: String? = null
        val DEFAULT_NETWORK_MAP_CERTIFICATE_NAME = "cordaintermediateca_nm"
        val DEFAULT_SIGN_INTERVAL = 600L // in seconds (10 minutes)
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
        accepts("device", "CryptoServer device address (default: $DEFAULT_DEVICE)").withRequiredArg()
        accepts("keyGroup", "CryptoServer key group").withRequiredArg()
        accepts("keySpecifier", "CryptoServer key specifier (default: $DEFAULT_KEY_SPECIFIER)").withRequiredArg().ofType(Int::class.java).defaultsTo(DEFAULT_KEY_SPECIFIER)
        accepts("rootPrivateKeyPassword", "Password for the root certificate private key").withRequiredArg().describedAs("password")
        accepts("csrPrivateKeyPassword", "Password for the CSR signing certificate private key").withRequiredArg().describedAs("password")
        accepts("keyGenAuthThreshold", "Authentication strength threshold for the HSM key generation (default: $DEFAULT_KEY_GEN_AUTH_THRESHOLD)").withRequiredArg().ofType(Int::class.java).defaultsTo(DEFAULT_KEY_GEN_AUTH_THRESHOLD)
        accepts("signAuthThreshold", "Authentication strength threshold for the HSM CSR signing (default: $DEFAULT_SIGN_AUTH_THRESHOLD)").withRequiredArg().ofType(Int::class.java).defaultsTo(DEFAULT_SIGN_AUTH_THRESHOLD)
        accepts("authMode", "Authentication mode. Allowed values: ${AuthMode.values()} (default: $DEFAULT_AUTH_MODE)").withRequiredArg().defaultsTo(DEFAULT_AUTH_MODE.name)
        accepts("authKeyFilePath", "Key file path when authentication is based on a key file (i.e. authMode=${AuthMode.KEY_FILE.name})").withRequiredArg().describedAs("filepath")
        accepts("authKeyFilePassword", "Key file password when authentication is based on a key file (i.e. authMode=${AuthMode.KEY_FILE.name})").withRequiredArg()
        accepts("autoUsername", "Username to be used for certificate signing (if not specified it will be prompted for input)").withRequiredArg()
        accepts("csrCertificateName", "Name of the certificate to be used by this CA to sign CSR (default: $DEFAULT_CSR_CERTIFICATE_NAME)").withRequiredArg().defaultsTo(DEFAULT_CSR_CERTIFICATE_NAME)
        accepts("rootCertificateName", "Name of the root certificate to be used by this CA (default: $DEFAULT_ROOT_CERTIFICATE_NAME)").withRequiredArg().defaultsTo(DEFAULT_ROOT_CERTIFICATE_NAME)
        accepts("validDays", "Validity duration in days").withRequiredArg().ofType(Int::class.java)
        accepts("signInterval", "Time interval (in seconds) in which network map is signed (default: $DEFAULT_SIGN_INTERVAL)").withRequiredArg().ofType(Long::class.java).defaultsTo(DEFAULT_SIGN_INTERVAL)
    }

    val configFile = if (argConfig.hasPath("configFile")) {
        Paths.get(argConfig.getString("configFile"))
    } else {
        Paths.get(argConfig.getString("basedir")) / "signing_service.conf"
    }

    val config = argConfig.withFallback(ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))).resolve()
    return config.parseAs<Parameters>()
}