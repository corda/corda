package com.r3.ha.utilities

import com.r3.ha.utilities.RegistrationTool.Companion.x500PrincipalToTLSAlias
import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths

class BridgeSSLKeyTool : HAToolBase("import-ssl-key", "Key copying tool for creating bridge SSL keystore or add new node SSL identity to existing bridge SSL keystore.") {
    companion object {
        private val logger by lazy { contextLogger() }
    }

    @Option(names = ["--node-keystores"], arity = "1..*", paramLabel = "FILES", description = ["The path to the node SSL keystore(s)"], required = true)
    lateinit var nodeKeystore: Array<Path>
    @Option(names = ["--node-keystore-passwords"], arity = "1..*", paramLabel = "PASSWORDS", description = ["The password(s) of the node SSL keystore(s)"], required = true)
    lateinit var nodeKeystorePasswords: Array<String>
    @Option(names = ["-b", BASE_DIR], paramLabel = "FOLDER", description = ["The working directory where all the files are kept."])
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()
    @Option(names = ["-k", "--bridge-keystore"], paramLabel = "FILES", description = ["The path to the bridge SSL keystore."])
    private var _bridgeKeystore: Path? = null
    val bridgeKeystore: Path get() = _bridgeKeystore ?: (baseDirectory / "bridge.jks")
    @Option(names = ["-p", "--bridge-keystore-password"], paramLabel = "PASSWORD", description = ["The password of the bridge SSL keystore."], required = true)
    lateinit var bridgeKeystorePassword: String

    override val driversParentDir: Path? get() = baseDirectory

    override fun runTool() {
        if (!bridgeKeystore.exists()) {
            logger.info("Creating new bridge SSL keystore.")
        } else {
            logger.info("Adding new entries to bridge SSL keystore")
        }

        X509KeyStore.fromFile(bridgeKeystore, bridgeKeystorePassword, true).update {
            // Use the same password for all keystore is only one is provided
            // TODO: allow enter password interactively?
            val passwords = if (nodeKeystorePasswords.size == 1) MutableList(nodeKeystore.size) { nodeKeystorePasswords.first() }.toTypedArray() else nodeKeystorePasswords

            require(passwords.size == nodeKeystore.size) { "Number of passwords doesn't match the number of keystores, got ${passwords.size} passwords for ${nodeKeystore.size} keystores." }
            val skippedKeystore = mutableListOf<Path>()
            nodeKeystore.zip(passwords).forEach { (keystore, password) ->
                try {
                    logger.info("Importing SSL key from $keystore")
                    require(keystore.exists()) { "Invalid keystore path : $keystore" }
                    val tlsKeystore = X509KeyStore.fromFile(keystore, password, createNew = false)
                    val tlsKey = tlsKeystore.getPrivateKey(X509Utilities.CORDA_CLIENT_TLS, password)
                    val certChain = tlsKeystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS)
                    // Alias must be lower case to stay consistent with HSM alias after .JKS file forces to lower case on insert.
                    val alias = x500PrincipalToTLSAlias(certChain.first().subjectX500Principal)
                    // Key password need to be same as the keystore password
                    setPrivateKey(alias, tlsKey, certChain, bridgeKeystorePassword)
                    logger.info("Added new SSL key with alias '$alias', for identity '${certChain.first().subjectX500Principal}'")
                } catch (e: Exception) {
                    logger.error("Unable to import SSL key from $keystore due to error : '${e.message}'")
                    skippedKeystore.add(keystore)
                }
            }
            logger.info("Finish adding keys to keystore '$bridgeKeystore', keystore contains ${aliases().asSequence().count()} entries.")
            if (skippedKeystore.isNotEmpty()) {
                throw Exception("Error encountered when processing the following keystore(s) : $skippedKeystore , please check the inputs and rerun the key importing tool if necessary.")
            }
        }
    }
}