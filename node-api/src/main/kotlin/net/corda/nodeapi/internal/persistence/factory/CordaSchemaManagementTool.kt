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
 * From Hibernate 6, the `update` mode not only creates missing tables and columns, but also alters the type and length of existing
 * columns so that they match the entity mapping. Hibernate 5 never did this. The tables of the node (and of CorDapps) are created
 * and owned by Liquibase, and their column definitions deliberately differ from Hibernate's defaults (e.g. `NVARCHAR(1024)` for a
 * `String` with no explicit length), so altering them would for example shrink such columns to 255 characters.
 *
 * This tool restores the Hibernate 5 behaviour: only missing tables and columns are created.
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
