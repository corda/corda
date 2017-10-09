package net.corda.signing

import net.corda.node.utilities.configureDatabase
import net.corda.signing.authentication.Authenticator
import net.corda.signing.authentication.createProvider
import net.corda.signing.configuration.Parameters
import net.corda.signing.configuration.parseParameters
import net.corda.signing.generator.KeyCertificateGenerator
import net.corda.signing.hsm.HsmSigner
import net.corda.signing.menu.Menu
import net.corda.signing.persistence.ApprovedCertificateRequestData
import net.corda.signing.persistence.DBCertificateRequestStorage
import net.corda.signing.persistence.SigningServerSchemaService
import net.corda.signing.utils.mapCryptoServerException

fun main(args: Array<String>) {
    run(parseParameters(*args))
}

fun run(parameters: Parameters) {
    parameters.run {
        // Create DB connection.
        checkNotNull(dataSourceProperties)
        val database = configureDatabase(dataSourceProperties!!, databaseProperties, { SigningServerSchemaService() }, createIdentityService = {
            // Identity service not needed
            throw UnsupportedOperationException()
        })

        val storage = DBCertificateRequestStorage(database)
        val provider = createProvider()
        val sign: (List<ApprovedCertificateRequestData>) -> Unit = {
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


