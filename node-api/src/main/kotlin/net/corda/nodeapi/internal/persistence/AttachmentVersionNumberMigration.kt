package net.corda.nodeapi.internal.persistence

import  liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.node.NetworkParameters
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.contextLogger

class AttachmentVersionNumberMigration : CustomTaskChange {
    companion object {
        private val logger = contextLogger()
    }

    override fun execute(database: Database?) {
        val connection = database?.connection as JdbcConnection
        try {
            logger.debug("Start executing...")
            val networkParameters = getNetworkParameters(connection)
            if (networkParameters == null) {
                logger.debug("Network parameters not found.")
                return
            } else {
                logger.debug("Network parameters epoch: ${networkParameters.epoch}, whitelistedContractImplementations: ${networkParameters.whitelistedContractImplementations}.")
            }
            val availableAttachments = getAttachmentsWithDefaultVersion(connection)
            if (availableAttachments.isEmpty()) {
                logger.debug("Attachments not found.")
                return
            } else {
                logger.debug("Attachments with version '1': $availableAttachments")
            }

            availableAttachments.forEach { attachmentId ->
                val versions = networkParameters?.whitelistedContractImplementations?.values.mapNotNull { it.indexOfFirst { it.toString() == attachmentId} }.filter { it >= 0 }
                val maxPosition = versions.max() ?: 0
                if (maxPosition > 0) {
                    val version = maxPosition + 1
                    val msg = "Updating version of attachment $attachmentId to '$version'"
                    if (versions.toSet().size > 1)
                        logger.warn("Several versions based on whitelistedContractImplementations position are available: ${versions.toSet()}. $msg")
                    else
                        logger.debug(msg)
                    updateVersion(connection, attachmentId, version)
                }
            }

            logger.debug("Done")
        } catch (e: Exception) {
            logger.error("Exception while retrieving network parameters ${e.message}", e)
        }
    }

    override fun validate(database: Database?): ValidationErrors? {
        return null
    }

    override fun getConfirmationMessage(): String? {
        return null
    }

    override fun setFileOpener(resourceAccessor: ResourceAccessor?) {
    }

    override fun setUp() {
    }

    private fun getNetworkParameters(connection: JdbcConnection): NetworkParameters? =
            connection.createStatement().use {
                val rs = it.executeQuery("SELECT PARAMETERS_BYTES FROM NODE_NETWORK_PARAMETERS ORDER BY EPOCH DESC")
                if (rs.next()) {
                    val networkParametersBytes = rs.getBytes(1) as ByteArray
                    val networkParameters: NetworkParameters = networkParametersBytes.deserialize()
                    rs.close()
                    networkParameters
                } else
                    null
            }

    private fun getAttachmentsWithDefaultVersion(connection: JdbcConnection): List<String> =
            connection.createStatement().use {
                val attachments = mutableListOf<String>()
                val rs = it.executeQuery("SELECT ATT_ID FROM NODE_ATTACHMENTS WHERE VERSION = 1")
                while (rs.next()) {
                    val elem = rs.getString(1)
                    attachments.add(elem)
                }
                rs.close()
                attachments
            }

    private fun updateVersion(connection: JdbcConnection, attachmentId: String, version: Int) {
        connection.prepareStatement("UPDATE NODE_ATTACHMENTS SET VERSION = ? WHERE ATT_ID = ?").use {
            it.setInt(1, version)
            it.setString(2, attachmentId)
            it.executeUpdate()
        }
    }
}