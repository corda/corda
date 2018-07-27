/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal

import com.codahale.metrics.MetricFilter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.graphite.GraphiteReporter
import com.codahale.metrics.graphite.PickledGraphite
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.Emoji
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.loggerFor
import net.corda.node.VersionInfo
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.RelayConfiguration
import net.corda.node.services.statemachine.MultiThreadedStateMachineExecutor
import net.corda.node.services.statemachine.MultiThreadedStateMachineManager
import net.corda.node.services.statemachine.StateMachineManager
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import java.io.IOException
import java.net.Inet6Address
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

open class EnterpriseNode(configuration: NodeConfiguration,
                          versionInfo: VersionInfo,
                          initialiseSerialization: Boolean = true
) : Node(configuration, versionInfo, initialiseSerialization) {
    companion object {
        private val logger by lazy { loggerFor<EnterpriseNode>() }

        private fun defaultGraphitePrefix(legalName: CordaX500Name): String {
            return (legalName.organisation + "_" + legalName.locality + "_" + legalName.country + "_" + Inet6Address.getLocalHost().hostAddress)
        }

        fun getGraphitePrefix(configuration: NodeConfiguration): String {
            val customPrefix = configuration.graphiteOptions!!.prefix
            // Create a graphite prefix stripping all non-allowed characteres
            val graphiteName = (customPrefix ?: defaultGraphitePrefix(configuration.myLegalName))
                    .trim().replace(Regex("[^0-9a-zA-Z_]"), "_")
            if (customPrefix != null && graphiteName != customPrefix) {
                logger.warn("Invalid graphite prefix ${customPrefix} specified in config - got mangled to ${graphiteName}. Only letters, numbers and underscores are allowed")
            }
            return graphiteName
        }
    }

    class Startup(args: Array<String>) : NodeStartup(args) {
        override fun preNetworkRegistration(conf: NodeConfiguration) {
            super.preNetworkRegistration(conf)
            conf.relay?.let { connectToRelay(it, conf.p2pAddress.port) }
        }

        override fun drawBanner(versionInfo: VersionInfo) {
            // This line makes sure ANSI escapes work on Windows, where they aren't supported out of the box.
            AnsiConsole.systemInstall()

            val logo = """
R   ______               __        B
R  / ____/     _________/ /___ _   B_______ __   _ _______ _______  ______  _____   ______ _____ _______ _______
R / /     __  / ___/ __  / __ `/   B|______ | \  |    |    |______ |_____/ |_____] |_____/   |   |______ |______
R/ /___  /_/ / /  / /_/ / /_/ /    B|______ |  \_|    |    |______ |    \_ |       |    \_ __|__ ______| |______
R\____/     /_/   \__,_/\__,_/     W____________________________________________________________________________
D""".trimStart()

            val license = """
*************************************************************************************************************************************
*  All rights reserved.                                                                                                             *
*  This software is proprietary to and embodies the confidential technology of R3 LLC ("R3").                                       *
*  Possession, use, duplication or dissemination of the software is authorized only pursuant to a valid written license from R3.    *
*  IF YOU DO NOT HAVE A VALID WRITTEN LICENSE WITH R3, DO NOT USE THIS SOFTWARE.                                                    *
*************************************************************************************************************************************
"""

            // Now replace the R, B and W letters with their colour code escapes to make the banner prettier.
            if (Ansi.isEnabled()) {
                val red = Ansi.ansi().fgBrightRed().toString()
                val blue = Ansi.ansi().fgBrightBlue().toString()
                val white = Ansi.ansi().reset().fgBrightDefault().toString()
                val default = Ansi.ansi().reset().toString()
                val colourLogo = logo.replace("R", red).replace("B", blue).replace("W", white).replace("D", default)
                val banner =
                        colourLogo +
                                System.lineSeparator() +
                                (
                                        if (Emoji.hasEmojiTerminal)
                                            "${Emoji.CODE_LIGHTBULB}  "
                                        else
                                            "Tip: "
                                        ) +
                                Ansi.ansi().bold().a(tip).reset() +
                                System.lineSeparator()

                println(banner)
                println(license)
            } else {
                println(logo.replace("R", "").replace("B", "").replace("W", "").replace("D", ""))
            }
        }

        private val tip: String
            get() {
                val tips = javaClass.getResourceAsStream("tips.txt").bufferedReader().use { it.readLines() }
                return tips[(Math.random() * tips.size).toInt()]
            }

        override fun createNode(conf: NodeConfiguration, versionInfo: VersionInfo) = EnterpriseNode(conf, versionInfo)

        private fun connectToRelay(config: RelayConfiguration, localBrokerPort: Int) {
            with(config) {
                val jsh = JSch().apply {
                    val noPassphrase = byteArrayOf()
                    addIdentity(privateKeyFile.toString(), publicKeyFile.toString(), noPassphrase)
                }

                val session = jsh.getSession(username, relayHost, sshPort).apply {
                    // We don't check the host fingerprints because they may change often, and we are only relaying
                    // data encrypted under TLS anyway: the relay box is NOT considered trusted. A compromised relay
                    // box could observe packet sizes and timings, but the sort of attackers who might be able to
                    // use such information have mostly compromised the network backbone already anyway. So this makes
                    // setup more robust without changing security much.
                    setConfig("StrictHostKeyChecking", "no")
                }

                try {
                    logger.info("Connecting to a relay at $relayHost")
                    session.connect()
                } catch (e: JSchException) {
                    throw IOException("Unable to establish a SSH connection: $username@$relayHost", e)
                }
                try {
                    val localhost = "127.0.0.1"
                    logger.info("Forwarding ports: $relayHost:$remoteInboundPort -> $localhost:$localBrokerPort")
                    session.setPortForwardingR(remoteInboundPort, localhost, localBrokerPort)
                } catch (e: JSchException) {
                    throw IOException("Unable to set up port forwarding - is SSH on the remote host configured correctly? " +
                            "(port forwarding is not enabled by default)", e)
                }
            }

            logger.info("Relay setup successfully!")
        }
    }

    private fun registerOptionalMetricsReporter(configuration: NodeConfiguration, metrics: MetricRegistry) {
        if (configuration.graphiteOptions != null) {
            nodeReadyFuture.thenMatch({
                serverThread.execute {
                    GraphiteReporter.forRegistry(metrics)
                            .prefixedWith(getGraphitePrefix(configuration))
                            .convertDurationsTo(TimeUnit.MILLISECONDS)
                            .convertRatesTo(TimeUnit.SECONDS)
                            .filter(MetricFilter.ALL)
                            .build(PickledGraphite(configuration.graphiteOptions!!.server, configuration.graphiteOptions!!.port))
                            .start(configuration.graphiteOptions!!.sampleInvervallSeconds, TimeUnit.SECONDS)
                }
            }, { th ->
                log.error("Unexpected exception", th)
            })
        }
    }

    override fun start(): NodeInfo {
        val info = super.start()
        registerOptionalMetricsReporter(configuration, services.monitoringService.metrics)
        return info
    }

    private fun makeStateMachineExecutorService(): ExecutorService {
        log.info("Multi-threaded state machine manager with ${configuration.enterpriseConfiguration.tuning.flowThreadPoolSize} threads.")
        return MultiThreadedStateMachineExecutor(configuration.enterpriseConfiguration.tuning.flowThreadPoolSize)
    }

    override fun makeStateMachineManager(): StateMachineManager {
        if (configuration.enterpriseConfiguration.useMultiThreadedSMM) {
            val executor = makeStateMachineExecutorService()
            runOnStop += { executor.shutdown() }
            return MultiThreadedStateMachineManager(
                    services,
                    checkpointStorage,
                    executor,
                    database,
                    newSecureRandom(),
                    busyNodeLatch,
                    cordappLoader.appClassLoader
            )
        } else {
            log.info("Single-threaded state machine manager with 1 thread.")
            return super.makeStateMachineManager()
        }
    }
}
