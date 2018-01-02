package net.corda.node.internal

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import net.corda.core.internal.Emoji
import net.corda.core.utilities.loggerFor
import net.corda.node.VersionInfo
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.RelayConfiguration
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import java.io.IOException

class EnterpriseNode(configuration: NodeConfiguration,
                     versionInfo: VersionInfo) : Node(configuration, versionInfo) {
    companion object {
        private val logger by lazy { loggerFor<EnterpriseNode>() }
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
D                 B   ______               __
D   ____ _____    B  / ____/     _________/ /___ _
D  / ___/__  /    B / /     __  / ___/ __  / __ `/
D / /    /_ <R __  B/ /___  /_/ / /  / /_/ / /_/ /
D/_/   ___/ /R/_/  B\____/     /_/   \__,_/\__,_/
D     /____/
D""".trimStart()

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
            } else {
                println(logo.replace("R", "").replace("B", "").replace("W", "").replace("D", ""))
            }
        }

        private val tip: String get() {
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
}