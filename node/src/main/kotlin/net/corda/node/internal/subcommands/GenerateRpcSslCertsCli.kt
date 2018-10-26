package net.corda.node.internal.subcommands

import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.node.internal.Node
import net.corda.node.internal.NodeCliCommand
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.RunAfterNodeInitialisation
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.utilities.createKeyPairAndSelfSignedTLSCertificate
import net.corda.node.utilities.saveToKeyStore
import net.corda.node.utilities.saveToTrustStore
import java.io.Console
import kotlin.system.exitProcess

class GenerateRpcSslCertsCli(startup: NodeStartup): NodeCliCommand("generate-rpc-ssl-settings", "Generate the SSL key and trust stores for a secure RPC connection.", startup) {
    override fun runProgram(): Int {
        return startup.initialiseAndRun(cmdLineOptions, GenerateRpcSslCerts())
    }
}

class GenerateRpcSslCerts: RunAfterNodeInitialisation {
    override fun run(node: Node) {
        generateRpcSslCertificates(node.configuration)
    }

    private fun generateRpcSslCertificates(conf: NodeConfiguration) {
        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(conf.myLegalName.x500Principal)

        val keyStorePath = conf.baseDirectory / "certificates" / "rpcsslkeystore.jks"
        val trustStorePath = conf.baseDirectory / "certificates" / "export" / "rpcssltruststore.jks"

        if (keyStorePath.exists() || trustStorePath.exists()) {
            println("Found existing RPC SSL keystores. Command was already run. Exiting.")
            exitProcess(0)
        }

        val console: Console? = System.console()

        when (console) {
            // In this case, the JVM is not connected to the console so we need to exit.
            null -> {
                println("Not connected to console. Exiting.")
                exitProcess(1)
            }
            // Otherwise we can proceed normally.
            else -> {
                while (true) {
                    val keystorePassword1 = console.readPassword("Enter the RPC keystore password:")
                    // TODO: consider adding a password strength policy.
                    if (keystorePassword1.isEmpty()) {
                        println("The RPC keystore password cannot be an empty String.")
                        continue
                    }

                    val keystorePassword2 = console.readPassword("Re-enter the RPC keystore password:")
                    if (!keystorePassword1.contentEquals(keystorePassword2)) {
                        println("The RPC keystore passwords don't match.")
                        continue
                    }

                    saveToKeyStore(keyStorePath, keyPair, cert, String(keystorePassword1), "rpcssl")
                    println("The RPC keystore was saved to: $keyStorePath .")
                    break
                }

                while (true) {
                    val trustStorePassword1 = console.readPassword("Enter the RPC truststore password:")
                    // TODO: consider adding a password strength policy.
                    if (trustStorePassword1.isEmpty()) {
                        println("The RPC truststore password cannot be an empty string.")
                        continue
                    }

                    val trustStorePassword2 = console.readPassword("Re-enter the RPC truststore password:")
                    if (!trustStorePassword1.contentEquals(trustStorePassword2)) {
                        println("The RPC truststore passwords don't match.")
                        continue
                    }

                    saveToTrustStore(trustStorePath, cert, String(trustStorePassword1), "rpcssl")
                    println("The RPC truststore was saved to: $trustStorePath.")
                    println("You need to distribute this file along with the password in a secure way to all RPC clients.")
                    break
                }

                val dollar = '$'
                println("""
                            |
                            |The SSL certificates for RPC were generated successfully.
                            |
                            |Add this snippet to the "rpcSettings" section of your node.conf:
                            |       useSsl=true
                            |       ssl {
                            |           keyStorePath=$dollar{baseDirectory}/certificates/rpcsslkeystore.jks
                            |           keyStorePassword=the_above_password
                            |       }
                            |""".trimMargin())
            }
        }
    }

}
