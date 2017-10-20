package com.r3.corda.networkmanage.hsm

import com.r3.corda.networkmanage.common.persistence.SchemaService
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.configuration.Parameters
import com.r3.corda.networkmanage.hsm.configuration.parseParameters
import com.r3.corda.networkmanage.hsm.generator.KeyCertificateGenerator
import com.r3.corda.networkmanage.hsm.menu.Menu
import com.r3.corda.networkmanage.hsm.persistence.CertificateRequestData
import com.r3.corda.networkmanage.hsm.persistence.DBSignedCertificateRequestStorage
import com.r3.corda.networkmanage.hsm.signer.HsmSigner
import com.r3.corda.networkmanage.hsm.utils.mapCryptoServerException
import net.corda.node.utilities.configureDatabase

fun main(args: Array<String>) {
    run(parseParameters(*args))
}

fun run(parameters: Parameters) {
    parameters.run {
        // Create DB connection.
        checkNotNull(dataSourceProperties)
        val database = configureDatabase(dataSourceProperties, databaseProperties, {
            // Identity service not needed
            throw UnsupportedOperationException()
        }, SchemaService())

        val storage = DBSignedCertificateRequestStorage(database)
        val provider = createProvider()
        val sign: (List<CertificateRequestData>) -> Unit = {
            val signer = HsmSigner(
                    storage,
                    certificateName,
                    privateKeyPass,
                    rootCertificateName,
                    validDays,
                    keyStorePass,
                    Authenticator(provider, authMode, autoUsername, authKeyFilePath, authKeyFilePass, signAuthThreshold))
            signer.sign(it)
        }
        Menu().withExceptionHandler(::processError).addItem("1", "Generate root and intermediate certificates", {
            if (confirmedKeyGen()) {
                val generator = KeyCertificateGenerator(
                        Authenticator(provider, authMode, autoUsername, authKeyFilePath, authKeyFilePass, keyGenAuthThreshold),
                        keySpecifier,
                        keyGroup)
                generator.generateAllCertificates(keyStorePass, certificateName, privateKeyPass, rootCertificateName, rootPrivateKeyPass, validDays)
            }
        }).addItem("2", "Sign all approved and unsigned CSRs", {
            val approved = storage.getApprovedRequests()
            if (approved.isNotEmpty()) {
                if (confirmedSign(approved)) {
                    sign(approved)
                }
            } else {
                println("There is no approved CSR")
            }
        }).addItem("3", "List all approved and unsigned CSRs", {
            val approved = storage.getApprovedRequests()
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
    }
}

private fun processError(exception: Exception) {
    val processed = mapCryptoServerException(exception)
    println("An error occured: ${processed.message}")
}

private fun confirmedSign(selectedItems: List<CertificateRequestData>): Boolean {
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

private fun getSelection(toSelect: List<CertificateRequestData>): List<CertificateRequestData> {
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


