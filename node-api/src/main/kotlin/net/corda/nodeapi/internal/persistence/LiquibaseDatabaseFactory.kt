package net.corda.nodeapi.internal.persistence

import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection

interface LiquibaseDatabaseFactory {
    fun getLiquibaseDatabase(conn: JdbcConnection): Database
}