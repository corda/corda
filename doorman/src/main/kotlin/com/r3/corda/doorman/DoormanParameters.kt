package com.r3.corda.doorman

import com.r3.corda.doorman.OptionParserHelper.toConfigWithOptions
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.internal.div
import net.corda.nodeapi.config.parseAs
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

data class DoormanParameters(val basedir: Path,
                             val keystorePassword: String?,
                             val caPrivateKeyPassword: String?,
                             val rootKeystorePassword: String?,
                             val rootPrivateKeyPassword: String?,
                             val host: String,
                             val port: Int,
                             val dataSourceProperties: Properties,
                             val mode: Mode,
                             val databaseProperties: Properties? = null,
                             val jiraConfig: JiraConfig? = null,
                             val keystorePath: Path? = null, // basedir / "certificates" / "caKeystore.jks",
                             val rootStorePath: Path? = null // basedir / "certificates" / "rootCAKeystore.jks"
) {

    enum class Mode {
        DOORMAN, CA_KEYGEN, ROOT_KEYGEN
    }

    data class JiraConfig(
            val address: String,
            val projectCode: String,
            val username: String,
            val password: String,
            val doneTransitionCode: Int
    )
}

fun parseParameters(vararg args: String): DoormanParameters {
    val argConfig = args.toConfigWithOptions {
        accepts("basedir", "Overriding configuration filepath, default to current directory.").withRequiredArg().defaultsTo(".").describedAs("filepath")
        accepts("configFile", "Overriding configuration file, default to <<current directory>>/node.conf.").withRequiredArg().describedAs("filepath")
        accepts("mode", "Execution mode. Allowed values: ${DoormanParameters.Mode.values()}").withRequiredArg().defaultsTo(DoormanParameters.Mode.DOORMAN.name)
        accepts("keystorePath", "CA keystore filepath").withRequiredArg().describedAs("filepath")
        accepts("rootStorePath", "Root CA keystore filepath").withRequiredArg().describedAs("filepath")
        accepts("keystorePassword", "CA keystore password.").withRequiredArg().describedAs("password")
        accepts("caPrivateKeyPassword", "CA private key password.").withRequiredArg().describedAs("password")
        accepts("rootKeystorePassword", "Root CA keystore password.").withRequiredArg().describedAs("password")
        accepts("rootPrivateKeyPassword", "Root private key password.").withRequiredArg().describedAs("password")
        accepts("host", "Doorman web service host override").withRequiredArg().describedAs("hostname")
        accepts("port", "Doorman web service port override").withRequiredArg().ofType(Int::class.java).describedAs("port number")
    }

    val configFile = if (argConfig.hasPath("configFile")) {
        Paths.get(argConfig.getString("configFile"))
    } else {
        Paths.get(argConfig.getString("basedir")) / "node.conf"
    }
    val config = argConfig.withFallback(ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))).resolve()
    return config.parseAs<DoormanParameters>()
}
