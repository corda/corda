/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node

import com.typesafe.config.ConfigFactory
import joptsimple.OptionParser
import joptsimple.util.EnumConverter
import joptsimple.util.PathConverter
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import org.slf4j.event.Level
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

// NOTE: Do not use any logger in this class as args parsing is done before the logger is setup.
class ArgsParser {
    private val optionParser = OptionParser()
    // The intent of allowing a command line configurable directory and config path is to allow deployment flexibility.
    // Other general configuration should live inside the config file unless we regularly need temporary overrides on the command line
    private val baseDirectoryArg = optionParser
            .accepts("base-directory", "The node working directory where all the files are kept")
            .withRequiredArg()
            .defaultsTo(".")
    private val configFileArg = optionParser
            .accepts("config-file", "The path to the config file")
            .withRequiredArg()
            .defaultsTo("node.conf")
    private val loggerLevel = optionParser
            .accepts("logging-level", "Enable logging at this level and higher")
            .withRequiredArg()
            .withValuesConvertedBy(object : EnumConverter<Level>(Level::class.java) {})
            .defaultsTo(Level.INFO)
    private val logToConsoleArg = optionParser.accepts("log-to-console", "If set, prints logging to the console as well as to a file.")
    private val sshdServerArg = optionParser.accepts("sshd", "Enables SSHD server for node administration.")
    private val noLocalShellArg = optionParser.accepts("no-local-shell", "Do not start the embedded shell locally.")
    private val isRegistrationArg = optionParser.accepts("initial-registration", "Start initial node registration with Corda network to obtain certificate from the permissioning server.")
    private val networkRootTrustStorePathArg = optionParser.accepts("network-root-truststore", "Network root trust store obtained from network operator.")
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter())
            .defaultsTo((Paths.get("certificates") / "network-root-truststore.jks"))
    private val networkRootTrustStorePasswordArg = optionParser.accepts("network-root-truststore-password", "Network root trust store password obtained from network operator.")
            .withRequiredArg()
    private val unknownConfigKeysPolicy = optionParser.accepts("on-unknown-config-keys", "How to behave on unknown node configuration property keys: [WARN, FAIL, IGNORE].")
            .withRequiredArg()
            .withValuesConvertedBy(object : EnumConverter<UnknownConfigKeysPolicy>(UnknownConfigKeysPolicy::class.java) {})
            .defaultsTo(UnknownConfigKeysPolicy.FAIL)

    private val isVersionArg = optionParser.accepts("version", "Print the version and exit")
    private val justGenerateNodeInfoArg = optionParser.accepts("just-generate-node-info",
            "Perform the node start-up task necessary to generate its nodeInfo, save it to disk, then quit")
    private val bootstrapRaftClusterArg = optionParser.accepts("bootstrap-raft-cluster", "Bootstraps Raft cluster. The node forms a single node cluster (ignoring otherwise configured peer addresses), acting as a seed for other nodes to join the cluster.")
    private val helpArg = optionParser.accepts("help").forHelp()

    fun parse(vararg args: String): CmdLineOptions {
        val optionSet = optionParser.parse(*args)
        require(!optionSet.has(baseDirectoryArg) || !optionSet.has(configFileArg)) {
            "${baseDirectoryArg.options()[0]} and ${configFileArg.options()[0]} cannot be specified together"
        }
        val baseDirectory = Paths.get(optionSet.valueOf(baseDirectoryArg)).normalize().toAbsolutePath()
        val configFile = baseDirectory / optionSet.valueOf(configFileArg)
        val help = optionSet.has(helpArg)
        val loggingLevel = optionSet.valueOf(loggerLevel)
        val logToConsole = optionSet.has(logToConsoleArg)
        val isRegistration = optionSet.has(isRegistrationArg)
        val isVersion = optionSet.has(isVersionArg)
        val noLocalShell = optionSet.has(noLocalShellArg)
        val sshdServer = optionSet.has(sshdServerArg)
        val justGenerateNodeInfo = optionSet.has(justGenerateNodeInfoArg)
        val bootstrapRaftCluster = optionSet.has(bootstrapRaftClusterArg)
        val networkRootTrustStorePath = optionSet.valueOf(networkRootTrustStorePathArg)
        val networkRootTrustStorePassword = optionSet.valueOf(networkRootTrustStorePasswordArg)
        val unknownConfigKeysPolicy = optionSet.valueOf(unknownConfigKeysPolicy)

        val registrationConfig = if (isRegistration) {
            requireNotNull(networkRootTrustStorePassword) { "Network root trust store password must be provided in registration mode using --network-root-truststore-password." }
            require(networkRootTrustStorePath.exists()) { "Network root trust store path: '$networkRootTrustStorePath' doesn't exist" }
            NodeRegistrationOption(networkRootTrustStorePath, networkRootTrustStorePassword)
        } else {
            null
        }

        return CmdLineOptions(baseDirectory,
                configFile,
                help,
                loggingLevel,
                logToConsole,
                registrationConfig,
                isVersion,
                noLocalShell,
                sshdServer,
                justGenerateNodeInfo,
                bootstrapRaftCluster,
                unknownConfigKeysPolicy)
    }

    fun printHelp(sink: PrintStream) = optionParser.printHelpOn(sink)
}

data class NodeRegistrationOption(val networkRootTrustStorePath: Path, val networkRootTrustStorePassword: String)

data class CmdLineOptions(val baseDirectory: Path,
                          val configFile: Path,
                          val help: Boolean,
                          val loggingLevel: Level,
                          val logToConsole: Boolean,
                          val nodeRegistrationOption: NodeRegistrationOption?,
                          val isVersion: Boolean,
                          val noLocalShell: Boolean,
                          val sshdServer: Boolean,
                          val justGenerateNodeInfo: Boolean,
                          val bootstrapRaftCluster: Boolean,
                          val unknownConfigKeysPolicy: UnknownConfigKeysPolicy) {
    fun loadConfig(): NodeConfiguration {
        val config = ConfigHelper.loadConfig(
                baseDirectory,
                configFile,
                configOverrides = ConfigFactory.parseMap(mapOf("noLocalShell" to this.noLocalShell))
        ).parseAsNodeConfiguration(unknownConfigKeysPolicy::handle)
        if (nodeRegistrationOption != null) {
            require(!config.devMode) { "registration cannot occur in devMode" }
            requireNotNull(config.compatibilityZoneURL) {
                "compatibilityZoneURL must be present in node configuration file in registration mode."
            }
        }
        return config
    }
}
