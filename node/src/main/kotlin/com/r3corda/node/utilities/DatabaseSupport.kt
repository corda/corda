package com.r3corda.node.utilities

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import java.io.Closeable
import java.util.*

fun <T> databaseTransaction(statement: Transaction.() -> T): T = org.jetbrains.exposed.sql.transactions.transaction(statement)

fun configureDatabase(props: Properties): Pair<Closeable, Database> {
    val config = HikariConfig(props)
    val dataSource = HikariDataSource(config)
    val database = Database.connect(dataSource)
    // Check not in read-only mode.
    check(!database.metadata.isReadOnly) { "Database should not be readonly." }
    return Pair(dataSource, database)
}