package com.r3.corda.dbmigration

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.node.services.persistence.DBCheckpointStorage
import picocli.CommandLine.Mixin

class ExecuteMigrationsCli : CliWrapperBase(EXECUTE_MIGRATION, "This option will run the database migration on the configured database. This is the only command that will actually write to the database.") {
    @Mixin
    var cmdLineOptions = DbManagerOptions()

    override fun runProgram(): Int {
        val db = cmdLineOptions.toConfig()
        migrationLogger.info("Running the database migration on ${cmdLineOptions.baseDirectory}")
        db.runMigrationCommand(db.schemas) { migration, dataSource ->
            migration.runMigration(
                    dataSource.connection.use {
                        DBCheckpointStorage().getCheckpointCount(it) != 0L
                    },
                    migrationLogger
            )
        }
        migrationLogger.info("Migration completed successfully")
        return ExitCodes.SUCCESS
    }
}