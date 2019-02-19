package com.r3.corda.dbmigration

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.nodeapi.internal.persistence.SchemaMigration
import picocli.CommandLine

class ReleaseLockCli : CliWrapperBase(RELEASE_LOCK, "Releases whatever locks are on the database change log table, in case shutdown failed.") {
    @CommandLine.Mixin
    var cmdLineOptions = DbManagerOptions()

    override fun runProgram(): Int {
        val db = cmdLineOptions.toConfig()
        db.runWithDataSource { it ->
            SchemaMigration(emptySet(), it, db.config.database, null, currentDirectory = null).forceReleaseMigrationLock()
        }
        return ExitCodes.SUCCESS
    }
}