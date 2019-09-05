package com.r3.corda.dbmigration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.schemas.MappedSchema
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.services.config.configOf
import net.corda.node.services.schema.NodeSchemaService
import picocli.CommandLine.Option
import java.net.URLClassLoader
import java.nio.file.Path

//command line arguments
const val DOORMAN_JAR_PATH = "doorman-jar-path"
const val EXECUTE_MIGRATION = "execute-migration"
const val DRY_RUN = "dry-run"
const val CREATE_MIGRATION_CORDAPP = "create-migration-sql-for-cordapp"
const val JAR_OUTPUT = "jar"
const val RELEASE_LOCK = "release-lock"

enum class Mode {
    NODE, DOORMAN, JPA_NOTARY
}

fun SharedDbManagerOptions.toConfig(): DbManagerConfiguration {
    return if (this.mode == Mode.DOORMAN) {
        requireNotNull(this.doormanJarPath) { "If running against the doorman you must provide the --$DOORMAN_JAR_PATH" }
        DoormanDbManagerConfiguration(this)
    } else if (this.mode == Mode.JPA_NOTARY) {
        JPANotaryDbManagerConfiguration(this)
    } else {
        NodeDbManagerConfiguration(this)
    }
}

interface SharedDbManagerOptions {
    var mode: Mode
    var baseDirectory: Path?
    var configFile: String?
    var doormanJarPath: Path?

    fun copyFrom(other: LegacyDbManagerOptions) {
        this.mode = other.mode
        this.baseDirectory = other.baseDirectory
        this.configFile = other.configFile
        this.doormanJarPath = other.doormanJarPath
    }
}

class DbManagerOptions : SharedDbManagerOptions {
    @Option(
            names = ["--mode"],
            description = ["The operating mode. \${COMPLETION-CANDIDATES}"]
    )
    override var mode: Mode = Mode.NODE

    @Option(
            names = ["-b", "--base-directory"],
            description = ["The node or doorman directory."],
            required = true
    )
    override var baseDirectory: Path? = null

    @Option(
            names = ["-f", "--config-file"],
            description = ["The name of the config file. By default 'node.conf' for a simple node and 'network-management.conf' for a doorman."]
    )
    override var configFile: String? = null

    @Option(
            names = ["--$DOORMAN_JAR_PATH"],
            description = ["The path to the doorman JAR."]
    )
    override var doormanJarPath: Path? = null
}

class LegacyDbManagerOptions : SharedDbManagerOptions {
    @Option(
            names = ["--mode"],
            description = ["DEPRECATED. The operating mode. \${COMPLETION-CANDIDATES}"],
            hidden = true
    )
    override var mode: Mode = Mode.NODE

    // --base-directory needs to be set as not required in the root command, otherwise picocli wants you to enter it twice,
    // once for the base command and once for the subcommand
    @Option(
            names = ["-b", "--base-directory"],
            description = ["DEPRECATED. The node or doorman directory."],
            hidden = true
    )
    override var baseDirectory: Path? = null

    @Option(
            names = ["-f", "--config-file"],
            description = ["DEPRECATED. The name of the config file. By default 'node.conf' for a simple node and 'network-management.conf' for a doorman."],
            hidden = true
    )
    override var configFile: String? = null

    @Option(
            names = ["--$DOORMAN_JAR_PATH"],
            description = ["DEPRECATED. The path to the doorman JAR."],
            hidden = true
    )
    override var doormanJarPath: Path? = null

    @Option(
            names = ["--$EXECUTE_MIGRATION"],
            description = ["DEPRECATED. This option will run the database migration on the configured database. This is the only command that will actually write to the database."],
            hidden = true
    )
    var executeMigration: Boolean = false

    @Option(
            names = ["--$DRY_RUN"],
            arity = "0..1",
            description = ["DEPRECATED. Output the database migration to the specified output file.",
                "The output directory is the base-directory.",
                "You can specify a file name or 'CONSOLE' if you want to send the output to the console."],
            hidden = true
    )
    var dryRun: String? = null

    @Option(
            names = ["--$CREATE_MIGRATION_CORDAPP"],
            arity = "0..1",
            description = ["DEPRECATED. Create migration files for a CorDapp.",
                "You can specify the fully qualified name of the `MappedSchema` class. If not specified it will generate the migration for all schemas that don't have migrations.",
                "The output directory is the base-directory, where a `migration` folder is created."],
            hidden = true
    )
    var createMigrationSqlForCordappPath: String? = null
    val createMigrationSqlForCordapp: Boolean get() = createMigrationSqlForCordappPath != null

    @Option(
            names = ["--$RELEASE_LOCK"],
            description = ["DEPRECATED. Releases whatever locks are on the database change log table, in case shutdown failed."],
            hidden = true
    )
    var releaseLock: Boolean = false
}