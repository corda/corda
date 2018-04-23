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
import com.r3.corda.networkmanage.hsm.configuration.DoormanCertificateConfig
import com.r3.corda.networkmanage.hsm.menu.Menu
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.persistence.DBSignedCertificateRequestStorage
import com.r3.corda.networkmanage.hsm.persistence.SignedCertificateSigningRequestStorage
import com.r3.corda.networkmanage.hsm.signer.HsmCsrSigner
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.CordaPersistence

class CsrProcessor(private val config: DoormanCertificateConfig,
                   private val device: String,
                   private val keySpecifier: Int,
                   private val database: CordaPersistence) : Processor() {
    private companion object {
        private val logger = contextLogger()
    }

    private val auth = config.authParameters

    fun showMenu() {
        val csrStorage = DBSignedCertificateRequestStorage(database)
        val authenticator = Authenticator(
                provider = createProvider(config.keyGroup, keySpecifier, device),
                mode = auth.mode,
                authStrengthThreshold = auth.threshold)
        val signCsr: (List<ApprovedCertificateRequestData>) -> Unit = {
            val csrSigner = config.run {
                HsmCsrSigner(
                        csrStorage,
                        loadRootKeyStore(),
                        crlDistributionPoint.toString(),
                        null,
                        validDays,
                        authenticator)
            }
            logger.debug("Signing requests: $it")
            csrSigner.sign(it)
        }
        showMenu(csrStorage, signCsr)
    }

    private fun showMenu(csrStorage: SignedCertificateSigningRequestStorage, signCsr: (List<ApprovedCertificateRequestData>) -> Unit) {
        Menu().withExceptionHandler(this::processError).setExitOption("3", "Quit").addItem("1", "Sign all approved and unsigned CSRs", {
            logger.debug("Fetching approved requests...")
            val approved = csrStorage.getApprovedRequests()
            logger.debug("Approved requests fetched: $approved")
            if (approved.isNotEmpty()) {
                if (confirmedSign(approved)) {
                    signCsr(approved)
                }
            } else {
                printlnColor("There are no approved CSRs")
            }
        }).addItem("2", "List all approved and unsigned CSRs", {
            logger.debug("Fetching approved requests...")
            val approved = csrStorage.getApprovedRequests()
            logger.debug("Approved requests fetched: $approved")
            if (approved.isNotEmpty()) {
                printlnColor("Approved CSRs:")
                approved.forEachIndexed { index, item -> printlnColor("${index + 1}. ${item.request.subject}") }
                Menu().withExceptionHandler(this::processError).setExitOption("3", "Go back").
                        addItem("1", "Sign all listed CSRs", {
                            if (confirmedSign(approved)) {
                                signCsr(approved)
                            }
                        }, isTerminating = true).
                        addItem("2", "Select and sign CSRs", {
                            val selectedItems = getSelection(approved)
                            if (confirmedSign(selectedItems)) {
                                signCsr(selectedItems)
                            }
                        }, isTerminating = true).showMenu()
            } else {
                printlnColor("There is no approved and unsigned CSR")
            }
        }).showMenu()
    }

    private fun confirmedSign(selectedItems: List<ApprovedCertificateRequestData>): Boolean {
        printlnColor("Are you sure you want to sign the following requests:")
        selectedItems.forEachIndexed { index, data ->
            printlnColor("${index + 1} ${data.request.subject}")
        }
        var result = false
        Menu().addItem("Y", "Yes", { result = true }, true).setExitOption("N", "No").showMenu()
        return result
    }

    private fun getSelection(toSelect: List<ApprovedCertificateRequestData>): List<ApprovedCertificateRequestData> {
        print("CSRs to be signed (comma separated list): ")
        val line = readLine()
        if (line == null) {
            printlnColor("EOF reached")
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
            printlnColor(exception.message)
            emptyList()
        }
    }
}