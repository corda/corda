package com.r3.corda.doorman

import com.r3.corda.doorman.OptionParserHelper.toConfigWithOptions
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.div
import net.corda.nodeapi.config.getOrElse
import net.corda.nodeapi.config.getValue
import java.nio.file.Path
import java.util.*

class DoormanParameters(vararg args: String) {
    private val argConfig = args.toConfigWithOptions {
        accepts("basedir", "Overriding configuration filepath, default to current directory.").withRequiredArg().defaultsTo(".").describedAs("filepath")
        accepts("configFile", "Overriding configuration file, default to <<current directory>>/node.conf.").withRequiredArg().describedAs("filepath")
        accepts("keygen", "Generate CA keypair and certificate using provide Root CA key.").withOptionalArg()
        accepts("rootKeygen", "Generate Root CA keypair and certificate.").withOptionalArg()
        accepts("keystorePath", "CA keystore filepath, default to [basedir]/certificates/caKeystore.jks.").withRequiredArg().describedAs("filepath")
        accepts("rootStorePath", "Root CA keystore filepath, default to [basedir]/certificates/rootCAKeystore.jks.").withRequiredArg().describedAs("filepath")
        accepts("keystorePassword", "CA keystore password.").withRequiredArg().describedAs("password")
        accepts("caPrivateKeyPassword", "CA private key password.").withRequiredArg().describedAs("password")
        accepts("rootKeystorePassword", "Root CA keystore password.").withRequiredArg().describedAs("password")
        accepts("rootPrivateKeyPassword", "Root private key password.").withRequiredArg().describedAs("password")
        accepts("host", "Doorman web service host override").withRequiredArg().describedAs("hostname")
        accepts("port", "Doorman web service port override").withRequiredArg().ofType(Int::class.java).describedAs("port number")
    }
    private val basedir: Path by argConfig
    private val configFile by argConfig.getOrElse { basedir / "node.conf" }
    private val config = argConfig.withFallback(ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))).resolve()
    val keystorePath: Path by config.getOrElse { basedir / "certificates" / "caKeystore.jks" }
    val rootStorePath: Path by config.getOrElse { basedir / "certificates" / "rootCAKeystore.jks" }
    val keystorePassword: String? by config
    val caPrivateKeyPassword: String? by config
    val rootKeystorePassword: String? by config
    val rootPrivateKeyPassword: String? by config
    val host: String by config
    val port: Int by config
    val dataSourceProperties: Properties by config
    val jiraConfig = if (config.hasPath("jiraConfig")) JiraConfig(config.getConfig("jiraConfig")) else null
    private val keygen: Boolean by config.getOrElse { false }
    private val rootKeygen: Boolean by config.getOrElse { false }

    val mode = if (rootKeygen) Mode.ROOT_KEYGEN else if (keygen) Mode.CA_KEYGEN else Mode.DOORMAN

    enum class Mode {
        DOORMAN, CA_KEYGEN, ROOT_KEYGEN
    }

    class JiraConfig(config: Config) {
        val address: String by config
        val projectCode: String by config
        val username: String by config
        val password: String by config
        val doneTransitionCode: Int by config
    }
}
