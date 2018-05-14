package net.corda.behave.database

import net.corda.behave.node.configuration.DatabaseConfiguration
import java.io.Closeable
import java.sql.*
import java.util.*

class DatabaseConnection(
        private val config: DatabaseConfiguration,
        template: DatabaseConfigurationTemplate
) : Closeable {

    private val connectionString = template.connectionString(config)

    private var conn: Connection? = null

    fun open(): Connection {
        try {
            val connectionProps = Properties()
            connectionProps["user"] = config.username
            connectionProps["password"] = config.password
            retry (5) {
                conn = DriverManager.getConnection(connectionString, connectionProps)
            }
            return conn ?: throw Exception("Unable to open connection")
        } catch (ex: SQLException) {
            throw Exception("An error occurred whilst connecting to \"$connectionString\". " +
                    "Maybe the user and/or password is invalid?", ex)
        }
    }

    override fun close() {
        val connection = conn
        if (connection != null) {
            try {
                conn = null
                connection.close()
            } catch (ex: SQLException) {
                throw Exception("Failed to close database connection to \"$connectionString\"", ex)
            }
        }
    }

    private fun query(conn: Connection?, stmt: String? = null) {
        var statement: Statement? = null
        val resultset: ResultSet?
        try {
            statement = conn?.prepareStatement(stmt
                    ?: "SELECT name FROM sys.tables WHERE name = ?")
            statement?.setString(1, "Test")
            resultset = statement?.executeQuery()

            try {
                while (resultset?.next() == true) {
                    val name = resultset.getString("name")
                    println(name)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                resultset?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement?.close()
        }
    }

    private fun retry(numberOfTimes: Int, action: () -> Unit) {
        var i = numberOfTimes
        while (numberOfTimes > 0) {
            Thread.sleep(2000)
            try {
                action()
            } catch (ex: Exception) {
                if (i == 1) {
                    throw ex
                }
            }
            i -= 1
        }
    }

}