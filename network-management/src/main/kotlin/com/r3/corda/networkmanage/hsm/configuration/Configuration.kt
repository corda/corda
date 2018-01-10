package com.r3.corda.networkmanage.hsm.configuration

import com.r3.corda.networkmanage.common.utils.toConfigWithOptions
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.internal.div
import net.corda.core.internal.isRegularFile
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Configuration parameters.
 */
data class Parameters(val dataSourceProperties: Properties,
                      val databaseConfig: DatabaseConfig = DatabaseConfig(),
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
        accepts("config-file", "Overriding configuration file.").withRequiredArg().describedAs("filepath")
    }

    // The config-file option is changed to configFile
    val configFile = if (argConfig.hasPath("configFile")) {
        Paths.get(argConfig.getString("configFile"))
    } else {
        Paths.get(argConfig.getString("basedir")) / "signing_service.conf"
    }
    require(configFile.isRegularFile()) { "Config file $configFile does not exist" }

    val config = argConfig.withFallback(ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))).resolve()
    return config.parseAs()
}