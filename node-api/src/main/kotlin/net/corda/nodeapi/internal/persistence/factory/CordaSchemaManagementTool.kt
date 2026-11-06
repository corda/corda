package net.corda.nodeapi.internal.persistence.factory

import org.hibernate.boot.Metadata
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.dialect.Dialect
import org.hibernate.engine.jdbc.internal.Formatter
import org.hibernate.mapping.Table
import org.hibernate.tool.schema.JdbcMetadataAccessStrategy
import org.hibernate.tool.schema.extract.spi.TableInformation
import org.hibernate.tool.schema.internal.DefaultSchemaFilterProvider
import org.hibernate.tool.schema.internal.GroupedSchemaMigratorImpl
import org.hibernate.tool.schema.internal.HibernateSchemaManagementTool
import org.hibernate.tool.schema.internal.IndividuallySchemaMigratorImpl
import org.hibernate.tool.schema.internal.StandardTableMigrator
import org.hibernate.tool.schema.spi.ExecutionOptions
import org.hibernate.tool.schema.spi.GenerationTarget
import org.hibernate.tool.schema.spi.SchemaFilter
import org.hibernate.tool.schema.spi.SchemaMigrator

/**
 * The [org.hibernate.tool.schema.spi.SchemaManagementTool] used when Hibernate is allowed to manage the schema (`hbm2ddl.auto=update`).
 *
 * In production, the schema is managed only by Liquibase, and Hibernate just validates it (`hbm2ddl.auto=validate`). Hibernate is only
 * allowed to manage the schema when the node is started with `--allow-hibernate-to-manage-app-schema`, which requires dev mode, and by
 * the test frameworks (the driver DSL and the mock network do so by default). This lets CorDapps without migration scripts be run.
 * Although the option refers to the app schema, `update` applies to every entity of the session factory, including the node's own,
 * whose tables Liquibase has already created.
 *
 * Hibernate 5's `update` mode only created missing tables and columns. From Hibernate 6, it also alters the type and length of
 * existing columns so that they match the entity mapping. The column definitions of the Liquibase tables deliberately differ from
 * Hibernate's defaults (e.g. `NVARCHAR(1024)` for a `String` with no explicit length), so altering them would for example shrink such
 * columns to 255 characters.
 *
 * This tool restores the Hibernate 5 behaviour: only missing tables and columns are created, and existing columns are never altered.
 *
 * It extends classes in `org.hibernate.tool.schema.internal`, which are not part of Hibernate's public API and can change in any
 * Hibernate release, including a patch release. When `hibernateVersion` in constants.properties changes, check that this class still
 * overrides the methods that Hibernate calls, and run the tests that start a node with `--allow-hibernate-to-manage-app-schema`.
 */
class CordaSchemaManagementTool : HibernateSchemaManagementTool() {
    override fun getSchemaMigrator(options: MutableMap<String, Any>?): SchemaMigrator {
        val migrateFilter = DefaultSchemaFilterProvider.INSTANCE.migrateFilter
        return if (JdbcMetadataAccessStrategy.interpretSetting(options) == JdbcMetadataAccessStrategy.GROUPED) {
            GroupedMigrator(this, migrateFilter)
        } else {
            IndividuallyMigrator(this, migrateFilter)
        }
    }

    private class GroupedMigrator(tool: HibernateSchemaManagementTool, filter: SchemaFilter) : GroupedSchemaMigratorImpl(tool, filter) {
        override fun migrateTable(
                table: Table,
                tableInformation: TableInformation,
                dialect: Dialect,
                metadata: Metadata,
                formatter: Formatter,
                options: ExecutionOptions,
                sqlGenerationContext: SqlStringGenerationContext,
                vararg targets: GenerationTarget
        ) {
            applySqlStrings(false, addColumnStrings(table, tableInformation, dialect, metadata, sqlGenerationContext), formatter, options, *targets)
        }
    }

    private class IndividuallyMigrator(tool: HibernateSchemaManagementTool, filter: SchemaFilter) : IndividuallySchemaMigratorImpl(tool, filter) {
        override fun migrateTable(
                table: Table,
                tableInformation: TableInformation,
                dialect: Dialect,
                metadata: Metadata,
                formatter: Formatter,
                options: ExecutionOptions,
                sqlGenerationContext: SqlStringGenerationContext,
                vararg targets: GenerationTarget
        ) {
            applySqlStrings(false, addColumnStrings(table, tableInformation, dialect, metadata, sqlGenerationContext), formatter, options, *targets)
        }
    }
}

/** The statements Hibernate would use to bring [table] up to date, excluding any that alter the type of an existing column. */
private fun addColumnStrings(
        table: Table,
        tableInformation: TableInformation,
        dialect: Dialect,
        metadata: Metadata,
        context: SqlStringGenerationContext
): Array<String> {
    val alterTable = dialect.getAlterTableString(context.format(table.qualifiedTableName)) + ' '
    val addColumn = alterTable + dialect.addColumnString
    return StandardTableMigrator.sqlAlterStrings(table, dialect, metadata, tableInformation, context)
            .filter { it.startsWith(addColumn) }
            .toTypedArray()
}
