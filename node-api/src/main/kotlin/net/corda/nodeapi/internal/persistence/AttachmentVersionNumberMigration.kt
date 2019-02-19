package net.corda.nodeapi.internal.persistence

import  liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.internal.div
import net.corda.core.internal.readObject
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.persistence.SchemaMigration.Companion.NODE_BASE_DIR_KEY
import java.nio.file.Path
import java.nio.file.Paths

class AttachmentVersionNumberMigration : CustomTaskChange {
    companion object {
        private val logger = contextLogger()
    }

    override fun execute(database: Database?) {
        val connection = database?.connection as JdbcConnection
        val msg = "Attachment version creation from whitelisted JARs"

        try {
            logger.info("Start executing...")
            var networkParameters: NetworkParameters?
            val baseDir = System.getProperty(SchemaMigration.NODE_BASE_DIR_KEY)
            val availableAttachments = getAttachmentsWithDefaultVersion(connection)
            if (baseDir != null) {
                val path = Paths.get(baseDir) / NETWORK_PARAMS_FILE_NAME
                networkParameters = getNetworkParametersFromFile(path)
                if (networkParameters != null) {
                    logger.info("$msg using network parameters from $path, whitelistedContractImplementations: ${networkParameters.whitelistedContractImplementations}.")
                } else if (availableAttachments.isEmpty()){
                    logger.info("$msg skipped, network parameters not found in $path, but there are no available attachments to migrate.")
                    return
                } else {
                    logger.warn("$msg skipped, network parameters not found in $path.")
                    return
                }
            } else {
                logger.error("$msg skipped, network parameters not retrieved, could not determine node base directory due to system property $NODE_BASE_DIR_KEY being not set.")
                return
            }

            if (availableAttachments.isEmpty()) {
                logger.info("$msg skipped, no attachments not found.")
                return
            } else {
                logger.info("$msg, candidate attachments with version '1': $availableAttachments")
            }

            availableAttachments.forEach { attachmentId ->
                val versions = networkParameters?.whitelistedContractImplementations?.values.mapNotNull { it.indexOfFirst { it.toString() == attachmentId } }.filter { it >= 0 }
                val maxPosition = versions.max() ?: 0
                if (maxPosition > 0) {
                    val version = maxPosition + 1
                    val msg = "Updating version of attachment $attachmentId to '$version'."
                    if (versions.toSet().size > 1)
                        logger.warn("Several versions based on whitelistedContractImplementations position are available: ${versions.toSet()}. $msg")
                    else
                        logger.info(msg)
                    updateVersion(connection, attachmentId, version)
                }
            }
        } catch (e: Exception) {
            logger.error("$msg exception ${e.message}", e)
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

    private fun getNetworkParametersFromFile(path: Path): NetworkParameters? {
        return try {
            val networkParametersBytes = path?.readObject<SignedNetworkParameters>()
            networkParametersBytes?.raw?.deserialize()
        } catch (e: Exception) {
            // This condition is logged in the calling function, so no need to do that here.
            null
        }
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