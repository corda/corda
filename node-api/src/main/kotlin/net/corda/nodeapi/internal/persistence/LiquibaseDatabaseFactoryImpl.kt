package net.corda.nodeapi.internal.persistence

import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection

class LiquibaseDatabaseFactoryImpl : LiquibaseDatabaseFactory {
    override fun getLiquibaseDatabase(conn: JdbcConnection): Database {
        return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(conn)
    }
}