@file:JvmName("Main")
package net.corda.attestation.challenger

import net.corda.attestation.message.ias.QuoteStatus
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.net.URI
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.PKIXRevocationChecker.Option.*
import java.security.cert.*
import java.util.*

fun main(args: Array<String>) {
    val command = DefaultParser().parse(options, args)
    if (command.hasOption("h")) {
        HelpFormatter().printHelp("challenger", options)
        return
    }

    Security.addProvider(BouncyCastleProvider())

    val keyStorePassword = command.getOptionValue("t", DEFAULT_PASSWORD).toCharArray()
    val trustStorePassword = command.getOptionValue("k", DEFAULT_PASSWORD).toCharArray()
    val challengerKeyPair = loadKeyStoreResource("challenger.pfx", keyStorePassword).getKeyPair("challenge", keyStorePassword)

    val iasStore = loadKeyStoreResource("ias.pfx", trustStorePassword)
    val pkixParameters = PKIXParameters(iasStore.trustAnchorsFor("ias")).apply {
        val rlChecker = CertPathValidator.getInstance("PKIX").revocationChecker as PKIXRevocationChecker
        addCertPathChecker(rlChecker.apply { options = EnumSet.of(SOFT_FAIL) })
    }

    val hostname = command.getOptionValue("n", "localhost")
    val port = command.getOptionValue("p", "8080")
    val secretValue = command.getOptionValue("s", "And now for something very different indeed!")

    Challenger(
        keyPair = challengerKeyPair,
        enclaveHost = URI.create("http://$hostname:$port/host"),
        pkixParameters = pkixParameters
    ).apply {
        val attestation = attestToEnclave()
        println("Report ID:    ${attestation.reportID}")
        println("Quote Status: ${attestation.quoteStatus}")
        println("Timestamp:    ${attestation.timestamp}")

        if (attestation.quoteStatus == QuoteStatus.OK) {
            setSecret(secretValue, attestation)
            println("Secret provisioned successfully.")
        }
    }
}

private const val DEFAULT_PASSWORD = "attestation"

private val options = Options().apply {
    addOption("t", "trustPassword", true, "Password for IAS trust store")
    addOption("k", "keyPassword", true, "Password for Challenger's key store")
    addOption("n", "hostname", true, "Hostname for Enclave/Host")
    addOption("p", "port", true, "Port number for Enclave/Host")
    addOption("s", "secret", true, "A secret string to be provisioned")
    addOption("h", "help", false, "Displays usage")
}

private fun loadKeyStoreResource(resourceName: String, password: CharArray, type: String = "PKCS12"): KeyStore {
    return KeyStore.getInstance(type).apply {
        Challenger::class.java.classLoader.getResourceAsStream(resourceName)?.use { input ->
            load(input, password)
        }
    }
}

private fun KeyStore.getKeyPair(alias: String, password: CharArray): KeyPair {
    val privateKey = getKey(alias, password) as PrivateKey
    return KeyPair(getCertificate(alias).publicKey, privateKey)
}

private fun KeyStore.trustAnchorsFor(vararg aliases: String): Set<TrustAnchor>
    = aliases.map { alias -> TrustAnchor(getCertificate(alias) as X509Certificate, null) }.toSet()
