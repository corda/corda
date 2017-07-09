package net.corda.node.utilities

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import java.io.Closeable
import java.util.*
import javax.sql.DataSource

class CordaPersistence(initWithDatasource: DataSource, initWithDatabase: Database) {

    var dataSource: DataSource
    var database: Database
    init {
        dataSource = initWithDatasource
        database = initWithDatabase
    }


    fun <T> transaction(statement: Transaction.() -> T): T {
        // We need to set the database for the current [Thread] or [Fiber] here as some tests share threads across databases.
        CordaTransactionManager.database = this.dataSource
        println("thread=${Thread.currentThread().id} db change to $this")
        return this.database.transaction(statement)
    }

    fun createTransaction(): Transaction {
        // We need to set the database for the current [Thread] or [Fiber] here as some tests share threads across databases.
        CordaTransactionManager.database = this.dataSource
        println("thread=${Thread.currentThread().id} db change to $this for create")
        return this.database.createTransaction()
    }

    @Deprecated("Use Database.transaction instead.")
    fun <T> databaseTransaction(db: CordaPersistence, statement: Transaction.() -> T) = db.transaction(statement)

    fun <T> isolatedTransaction(block: Transaction.() -> T): T {
        return this.database.isolatedTransaction(block)
    }

    companion object {
        fun configureDatabase(props: Properties): Pair<Closeable, CordaPersistence> {
            val config = HikariConfig(props)
            val dataSource = HikariDataSource(config)

            val database = Database.connect(dataSource) { db -> StrandLocalTransactionManager(db, CordaTransactionManager(dataSource)) }

            val persistence = CordaPersistence(dataSource, database)
            // Check not in read-only mode.
            persistence.transaction {
                check(!database.metadata.isReadOnly) { "Database should not be readonly." }
            }
            return Pair(dataSource, persistence)
        }
    }
}