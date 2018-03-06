/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.processor

import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.configuration.DoormanCertificateParameters
import com.r3.corda.networkmanage.hsm.menu.Menu
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.persistence.DBSignedCertificateRequestStorage
import com.r3.corda.networkmanage.hsm.signer.HsmCsrSigner
import com.r3.corda.networkmanage.hsm.utils.mapCryptoServerException
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.CordaPersistence

class CsrProcessor(private val parameters: DoormanCertificateParameters,
                   private val device: String,
                   private val keySpecifier: Int,
                   private val database: CordaPersistence) {
    companion object {
        val logger = contextLogger()
    }

    private val auth = parameters.authParameters

    fun showMenu() {
        val csrStorage = DBSignedCertificateRequestStorage(database)
        val sign: (List<ApprovedCertificateRequestData>) -> Unit = {
            val signer = parameters.run {
                HsmCsrSigner(
                        csrStorage,
                        loadRootKeyStore(),
                        crlDistributionPoint,
                        null,
                        validDays,
                        Authenticator(
                                provider = createProvider(parameters.keyGroup, keySpecifier, device),
                                mode = auth.mode,
                                authStrengthThreshold = auth.threshold))
            }
            logger.debug("Signing requests: $it")
            signer.sign(it)
        }
        Menu().withExceptionHandler(this::processError).setExitOption("3", "Quit").addItem("1", "Sign all approved and unsigned CSRs",
                {
                    logger.debug("Fetching approved requests...")
                    val approved = csrStorage.getApprovedRequests()
                    logger.debug("Approved requests fetched: $approved")
                    if (approved.isNotEmpty()) {
                        if (confirmedSign(approved)) {
                            sign(approved)
                        }
                    } else {
                        println("There is no approved CSR")
                    }
                }).addItem("2", "List all approved and unsigned CSRs",
                {
                    logger.debug("Fetching approved requests...")
                    val approved = csrStorage.getApprovedRequests()
                    logger.debug("Approved requests fetched: $approved")
                    if (approved.isNotEmpty()) {
                        println("Approved CSRs:")
                        approved.forEachIndexed { index, item -> println("${index + 1}. ${item.request.subject}") }
                        Menu().withExceptionHandler(this::processError).setExitOption("3", "Go back").
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

    private fun confirmedSign(selectedItems: List<ApprovedCertificateRequestData>): Boolean {
        println("Are you sure you want to sign the following requests:")
        selectedItems.forEachIndexed { index, data ->
            println("${index + 1} ${data.request.subject}")
        }
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

    fun processError(exception: Exception) {
        val processed = mapCryptoServerException(exception)
        System.err.println("An error occurred:")
        processed.printStackTrace()
    }
}