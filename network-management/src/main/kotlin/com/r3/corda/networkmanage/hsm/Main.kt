package com.r3.corda.networkmanage.hsm

import com.r3.corda.networkmanage.common.persistence.PersistentNetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.configuration.Parameters
import com.r3.corda.networkmanage.hsm.configuration.parseParameters
import com.r3.corda.networkmanage.hsm.generator.CertificateNameAndPass
import com.r3.corda.networkmanage.hsm.generator.KeyCertificateGenerator
import com.r3.corda.networkmanage.hsm.menu.Menu
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.persistence.DBSignedCertificateRequestStorage
import com.r3.corda.networkmanage.hsm.signer.HsmCsrSigner
import com.r3.corda.networkmanage.hsm.signer.HsmNetworkMapSigner
import com.r3.corda.networkmanage.hsm.utils.mapCryptoServerException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.crypto.Cipher

fun main(args: Array<String>) {
    // Grabbed from https://stackoverflow.com/questions/7953567/checking-if-unlimited-cryptography-is-available
    if (Cipher.getMaxAllowedKeyLength("AES") < 256) {
        System.err.println("Unlimited Strength Jurisdiction Policy Files must be installed, see http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html")
        System.exit(1)
    }

    try {
        run(parseParameters(*args))
    } catch (e: ShowHelpException) {
        e.errorMessage?.let(::println)
        e.parser.printHelpOn(System.out)
    }
}

fun run(parameters: Parameters) {
    parameters.run {
        // Ensure the BouncyCastle provider is installed
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        // Create DB connection.
        checkNotNull(dataSourceProperties)
        val database = configureDatabase(dataSourceProperties, databaseConfig)
        val csrStorage = DBSignedCertificateRequestStorage(database)
        val networkMapStorage = PersistentNetworkMapStorage(database)
        val hsmNetworkMapSigningThread = HsmNetworkMapSigner(
                networkMapStorage,
                networkMapCertificateName,
                networkMapPrivateKeyPassword,
                Authenticator(createProvider(), AuthMode.KEY_FILE, autoUsername, authKeyFilePath, authKeyFilePassword, signAuthThreshold)).start()
        val sign: (List<ApprovedCertificateRequestData>) -> Unit = {
            val signer = HsmCsrSigner(
                    csrStorage,
                    csrCertificateName,
                    csrPrivateKeyPassword,
                    rootCertificateName,
                    validDays,
                    Authenticator(createProvider(), authMode, autoUsername, authKeyFilePath, authKeyFilePassword, signAuthThreshold))
            signer.sign(it)
        }
        Menu().withExceptionHandler(::processError).addItem("1", "Generate root and intermediate certificates", {
            if (confirmedKeyGen()) {
                val generator = KeyCertificateGenerator(
                        Authenticator(createProvider(), authMode, autoUsername, authKeyFilePath, authKeyFilePassword, keyGenAuthThreshold),
                        keySpecifier,
                        keyGroup)
                generator.generateAllCertificates(
                        listOf(CertificateNameAndPass(csrCertificateName, csrPrivateKeyPassword), CertificateNameAndPass(networkMapCertificateName, networkMapPrivateKeyPassword)),
                        rootCertificateName,
                        rootPrivateKeyPassword,
                        validDays)
            }
        }).addItem("2", "Sign all approved and unsigned CSRs", {
            val approved = csrStorage.getApprovedRequests()
            if (approved.isNotEmpty()) {
                if (confirmedSign(approved)) {
                    sign(approved)
                }
            } else {
                println("There is no approved CSR")
            }
        }).addItem("3", "List all approved and unsigned CSRs", {
            val approved = csrStorage.getApprovedRequests()
            if (approved.isNotEmpty()) {
                println("Approved CSRs:")
                approved.forEachIndexed { index, item -> println("${index + 1}. ${item.request.subject}") }
                Menu().withExceptionHandler(::processError).setExitOption("3", "Go back").
                        addItem("1", "Sign all listed CSRs", {
                            if (confirmedSign(approved)) {
                                sign(approved)
                            }
                        }, isTerminating = true).
                        addItem("2", "Select and sign CSRs", {
                            val selectedItems = getSelection(approved)
                            if (confirmedSign(selectedItems)) {
                                sign(selectedItems)
                            }
                        }, isTerminating = true).showMenu()
            } else {
                println("There is no approved and unsigned CSR")
            }
        }).showMenu()
        hsmNetworkMapSigningThread.stop()
    }
}

private fun processError(exception: Exception) {
    val processed = mapCryptoServerException(exception)
    println("An error occured: ${processed.message}")
}

private fun confirmedSign(selectedItems: List<ApprovedCertificateRequestData>): Boolean {
    println("Are you sure you want to sign the following requests:")
    selectedItems.forEachIndexed { index, data ->
        println("${index + 1} ${data.request.subject}")
    }
    var result = false
    Menu().addItem("Y", "Yes", { result = true }, true).setExitOption("N", "No").showMenu()
    return result
}

private fun confirmedKeyGen(): Boolean {
    println("Are you sure you want to generate new keys/certificates (it will overwrite the existing ones):")
    var result = false
    Menu().addItem("Y", "Yes", { result = true }, true).setExitOption("N", "No").showMenu()
    return result
}

private fun getSelection(toSelect: List<ApprovedCertificateRequestData>): List<ApprovedCertificateRequestData> {
    print("CSRs to be signed (comma separated list): ")
    val line = readLine()
    if (line == null) {
        println("EOF reached")
        return emptyList()
    }
    return try {
        line.split(",").map {
            val result = it.toInt() - 1
            if (result > toSelect.size - 1) {
                throw IllegalArgumentException("Selected ${result + 1} item is out of bounds")
            } else {
                toSelect[result]
            }
        }
    } catch (exception: Exception) {
        println(exception.message)
        emptyList()
    }
}


