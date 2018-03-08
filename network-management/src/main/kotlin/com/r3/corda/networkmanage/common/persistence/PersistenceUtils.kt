/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.core.schemas.MappedSchema
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.SchemaMigration
import java.util.*
import javax.persistence.LockModeType
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate

fun <T> DatabaseTransaction.singleRequestWhere(clazz: Class<T>, predicate: (CriteriaBuilder, Path<T>) -> Predicate): T? {
    val builder = session.criteriaBuilder
    val criteriaQuery = builder.createQuery(clazz)
    val query = criteriaQuery.from(clazz).run {
        criteriaQuery.where(predicate(builder, this))
    }
    return session.createQuery(query).setLockMode(LockModeType.PESSIMISTIC_WRITE).resultList.firstOrNull()
}

fun <T> DatabaseTransaction.deleteRequest(clazz: Class<T>, predicate: (CriteriaBuilder, Path<T>) -> Predicate): Int {
    val builder = session.criteriaBuilder
    val criteriaDelete = builder.createCriteriaDelete(clazz)
    val delete = criteriaDelete.from(clazz).run {
        criteriaDelete.where(predicate(builder, this))
    }
    return session.createQuery(delete).executeUpdate()
}

fun configureDatabase(dataSourceProperties: Properties,
                      databaseConfig: DatabaseConfig = DatabaseConfig()): CordaPersistence {
    val config = HikariConfig(dataSourceProperties)
    val dataSource = HikariDataSource(config)

    val schemas = setOf(NetworkManagementSchemaServices.SchemaV1)
    SchemaMigration(schemas, dataSource, true, databaseConfig).nodeStartup()

    return CordaPersistence(dataSource, databaseConfig, schemas, config.dataSourceProperties.getProperty("url", ""), emptyList())
}

sealed class NetworkManagementSchemaServices {
    object SchemaV1 : MappedSchema(schemaFamily = NetworkManagementSchemaServices::class.java, version = 1,
            mappedTypes = listOf(
                    CertificateSigningRequestEntity::class.java,
                    CertificateDataEntity::class.java,
                    CertificateRevocationRequestEntity::class.java,
                    CertificateRevocationListEntity::class.java,
                    NodeInfoEntity::class.java,
                    NetworkParametersEntity::class.java,
                    NetworkMapEntity::class.java)) {
        override val migrationResource = "network-manager.changelog-master"
    }
}
