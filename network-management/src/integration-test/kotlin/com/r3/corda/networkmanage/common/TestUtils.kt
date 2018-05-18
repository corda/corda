/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common

import com.r3.corda.networkmanage.common.utils.createSignedCrl
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.testing.database.DatabaseConstants
import net.corda.testing.node.internal.databaseProviderDataSourceConfig
import org.apache.commons.io.FileUtils
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.nio.file.Path

const val HOST = "localhost"

const val DOORMAN_DB_NAME = "doorman"

fun networkMapInMemoryH2DataSourceConfig(nodeName: String? = null, postfix: String? = null): Config {
    val nodeName = nodeName ?: SecureHash.randomSHA256().toString()
    val h2InstanceName = if (postfix != null) nodeName + "_" + postfix else nodeName

    return ConfigFactory.parseMap(mapOf(
            DatabaseConstants.DATA_SOURCE_CLASSNAME to "org.h2.jdbcx.JdbcDataSource",
            DatabaseConstants.DATA_SOURCE_URL to "jdbc:h2:mem:${h2InstanceName};DB_CLOSE_DELAY=-1",
            DatabaseConstants.DATA_SOURCE_USER to "sa",
            DatabaseConstants.DATA_SOURCE_PASSWORD to ""))
}

fun generateEmptyCrls(tempFolder: TemporaryFolder, rootCertAndKeyPair: CertificateAndKeyPair, directEndpoint: URL, indirectEndpoint: URL): Pair<Path, Path> {
    val localSigner = LocalSigner(rootCertAndKeyPair)
    val directCrl = createSignedCrl(rootCertAndKeyPair.certificate, directEndpoint, 10.days, localSigner, emptyList(), false)
    val indirectCrl = createSignedCrl(rootCertAndKeyPair.certificate, indirectEndpoint, 10.days, localSigner, emptyList(), true)
    val directCrlFile = tempFolder.newFile()
    FileUtils.writeByteArrayToFile(directCrlFile, directCrl.encoded)
    val indirectCrlFile = tempFolder.newFile()
    FileUtils.writeByteArrayToFile(indirectCrlFile, indirectCrl.encoded)
    return Pair(directCrlFile.toPath(), indirectCrlFile.toPath())
}

fun getCaCrlEndpoint(serverAddress: NetworkHostAndPort) = URL("http://$serverAddress/certificate-revocation-list/root")
fun getEmptyCrlEndpoint(serverAddress: NetworkHostAndPort) = URL("http://$serverAddress/certificate-revocation-list/empty")
fun getNodeCrlEndpoint(serverAddress: NetworkHostAndPort) = URL("http://$serverAddress/certificate-revocation-list/doorman")

//TODO add more dbs to test once doorman supports them
fun configSupplierForSupportedDatabases(): (String?, String?) -> Config =
        when (System.getProperty("custom.databaseProvider", "")) {
            "integration-sql-server", "integration-azure-sql" -> ::databaseProviderDataSourceConfig
            else -> { _, _ -> ConfigFactory.empty() }
        }