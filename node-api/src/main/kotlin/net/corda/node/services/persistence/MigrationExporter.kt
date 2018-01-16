package net.corda.node.services.persistence

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.migrationResourceNameForSchema
import net.corda.nodeapi.internal.persistence.HibernateConfiguration
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.cfg.Configuration
import org.hibernate.dialect.Dialect
import org.hibernate.tool.hbm2ddl.SchemaExport
import org.hibernate.tool.schema.TargetType
import java.io.File
import java.nio.file.Path
import java.sql.Types
import java.util.*
import javax.persistence.AttributeConverter
import javax.persistence.Converter

/**
 * This is useful for CorDapp developers who want to enable migrations for
 * standard "Open Source" Corda CorDapps
 */
object MigrationExporter {

    const val LIQUIBASE_HEADER = "--liquibase formatted sql"
    const val CORDA_USER = "R3.Corda.Generated"

    fun generateMigrationForCorDapp(schemaName: String, parent: Path = File(".").toPath()): Path {
        val schemaClass = Class.forName(schemaName)
        val schemaObject = schemaClass.kotlin.objectInstance as MappedSchema
        return generateMigrationForCorDapp(schemaObject, parent)
    }

    fun generateMigrationForCorDapp(mappedSchema: MappedSchema, parent: Path): Path {

        //create hibernate metadata for MappedSchema
        val metadata = createHibernateMetadataForSchema(mappedSchema)

        //create output file and add metadata
        val outputFile = File(parent.toFile(), "${migrationResourceNameForSchema(mappedSchema)}.sql")
        outputFile.apply {
            parentFile.mkdirs()
            delete()
            createNewFile()
            appendText(LIQUIBASE_HEADER)
            appendText("\n\n")
            appendText("--changeset ${CORDA_USER}:initial_schema_for_${mappedSchema::class.simpleName!!}")
            appendText("\n")
        }

        //export the schema to that file
        SchemaExport().apply {
            setDelimiter(";")
            setFormat(true)
            setOutputFile(outputFile.absolutePath)
            execute(EnumSet.of(TargetType.SCRIPT), SchemaExport.Action.CREATE, metadata)
        }
        return outputFile.toPath()
    }

    private fun createHibernateMetadataForSchema(mappedSchema: MappedSchema): Metadata {
        val metadataSources = MetadataSources(BootstrapServiceRegistryBuilder().build())
        val config = Configuration(metadataSources)
        mappedSchema.mappedTypes.forEach { config.addAnnotatedClass(it) }
        val regBuilder = config.standardServiceRegistryBuilder
                .applySetting("hibernate.dialect", HibernateGenericDialect::class.java.name)
        val metadataBuilder = metadataSources.getMetadataBuilder(regBuilder.build())

        return HibernateConfiguration.buildHibernateMetadata(metadataBuilder, "",
                listOf(DummyAbstractPartyToX500NameAsStringConverter()))
    }

    /**
     * used just for generating columns
     */
    @Converter(autoApply = true)
    class DummyAbstractPartyToX500NameAsStringConverter : AttributeConverter<AbstractParty, String> {

        override fun convertToDatabaseColumn(party: AbstractParty?) = null

        override fun convertToEntityAttribute(dbData: String?) = null
    }

    /**
     * Simplified hibernate dialect used for generating liquibase migration files
     */
    class HibernateGenericDialect : Dialect() {
        init {
            registerColumnType(Types.BIGINT, "bigint")
            registerColumnType(Types.BOOLEAN, "boolean")
            registerColumnType(Types.BLOB, "blob")
            registerColumnType(Types.CLOB, "clob")
            registerColumnType(Types.DATE, "date")
            registerColumnType(Types.FLOAT, "float")
            registerColumnType(Types.TIME, "time")
            registerColumnType(Types.TIMESTAMP, "timestamp")
            registerColumnType(Types.VARCHAR, "varchar(\$l)")
            registerColumnType(Types.BINARY, "binary")
            registerColumnType(Types.BIT, "boolean")
            registerColumnType(Types.CHAR, "char(\$l)")
            registerColumnType(Types.DECIMAL, "decimal(\$p,\$s)")
            registerColumnType(Types.NUMERIC, "decimal(\$p,\$s)")
            registerColumnType(Types.DOUBLE, "double")
            registerColumnType(Types.INTEGER, "integer")
            registerColumnType(Types.LONGVARBINARY, "longvarbinary")
            registerColumnType(Types.LONGVARCHAR, "longvarchar")
            registerColumnType(Types.REAL, "real")
            registerColumnType(Types.SMALLINT, "smallint")
            registerColumnType(Types.TINYINT, "tinyint")
        }
    }
}