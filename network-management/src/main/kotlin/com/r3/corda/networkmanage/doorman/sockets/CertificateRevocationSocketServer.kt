package com.r3.corda.networkmanage.doorman.sockets

import com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationListStorage
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestStorage
import com.r3.corda.networkmanage.common.persistence.CrlIssuer
import com.r3.corda.networkmanage.common.sockets.*
import com.r3.corda.networkmanage.common.utils.readObject
import com.r3.corda.networkmanage.common.utils.writeObject
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CertificateRevocationSocketServer(val port: Int,
                                        private val crlStorage: CertificateRevocationListStorage,
                                        private val crrStorage: CertificateRevocationRequestStorage) : AutoCloseable {
    private companion object {
        private val logger = contextLogger()

        private val RECONNECT_INTERVAL = 10.seconds.toMillis()
    }

    private val executor = Executors.newSingleThreadScheduledExecutor()

    @Volatile
    private var isRunning = false

    fun start() {
        check(!isRunning) { "The server has already been started." }
        isRunning = true
        executor.submit {
            while (isRunning) {
                try {
                    listen()
                } catch (e: Exception) {
                    logger.error("Socket execution error.", e)
                    if (isRunning) {
                        logger.info("Server socket will be recreated in $RECONNECT_INTERVAL milliseconds")
                        Thread.sleep(RECONNECT_INTERVAL)
                        logger.info("Recreating server socket...")
                    }
                }
            }
        }
    }

    override fun close() {
        isRunning = false
        shutdownAndAwaitTermination(executor, RECONNECT_INTERVAL, TimeUnit.MILLISECONDS)
    }

    private fun listen() {
        ServerSocket(port).use {
            logger.info("Server socket is running.")
            while (isRunning) {
                logger.debug("Waiting for socket data...")
                val acceptedSocket = it.accept()
                acceptedSocket.use {
                    val message = it.getInputStream().readObject<CrrSocketMessage>()
                    logger.debug("Received message type is $message.")
                    acceptedSocket.getOutputStream().let {
                        when (message) {
                            is CrlRetrievalMessage -> {
                                val crl = crlStorage.getCertificateRevocationList(CrlIssuer.DOORMAN)
                                it.writeObject(CrlResponseMessage(crl))
                                logger.debug("Sending the current certificate revocation list.")
                            }
                            is CrrsByStatusMessage -> {
                                it.writeObject(crrStorage.getRevocationRequests(message.status))
                                logger.debug("Sending ${message.status.name} certificate revocation requests.")
                            }
                            is CrlSubmissionMessage -> {
                                val crlSubmission = acceptedSocket.getInputStream().readObject<CertificateRevocationListSubmission>()
                                crlStorage.saveCertificateRevocationList(crlSubmission.list, CrlIssuer.DOORMAN, crlSubmission.signer, crlSubmission.revocationTime)
                            }
                            else -> logger.warn("Unknown message type $message")
                        }
                    }
                }
            }
        }
    }


}