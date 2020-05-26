package net.corda.nodeapi.internal.persistence.factory

import net.corda.nodeapi.internal.persistence.DatabaseConfig
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import org.hibernate.boot.MetadataSources
import org.hibernate.cfg.Configuration
import org.hibernate.type.AbstractSingleColumnStandardBasicType
import org.hibernate.type.descriptor.java.PrimitiveByteArrayTypeDescriptor
import org.hibernate.type.descriptor.sql.VarbinaryTypeDescriptor
import javax.persistence.AttributeConverter

class PostgresSessionFactoryFactory : BaseSessionFactoryFactory() {
    override fun buildHibernateMetadata(metadataBuilder: MetadataBuilder, attributeConverters: Collection<AttributeConverter<*, *>>): Metadata {
        return metadataBuilder.run {
            attributeConverters.forEach { applyAttributeConverter(it) }
            // Register a tweaked version of `org.hibernate.type.MaterializedBlobType` that truncates logged messages.
            // to avoid OOM when large blobs might get logged.
            applyBasicType(CordaMaterializedBlobType, CordaMaterializedBlobType.name)
            applyBasicType(CordaWrapperBinaryType, CordaWrapperBinaryType.name)

            // Create a custom type that will map a blob to byteA in postgres
            // This is required for the Checkpoints as a workaround for the issue that postgres has on azure.
            applyBasicType(MapBlobToPostgresByteA, MapBlobToPostgresByteA.name)

            build()
        }
    }

    override fun canHandleDatabase(jdbcUrl: String): Boolean = jdbcUrl.contains(":postgresql:")

    override fun buildHibernateConfig(databaseConfig: DatabaseConfig, metadataSources: MetadataSources, allowHibernateToManageAppSchema: Boolean): Configuration {
        val config = super.buildHibernateConfig(databaseConfig, metadataSources, allowHibernateToManageAppSchema)
        databaseConfig.schema?.apply {
            val schemaName = if (!this.startsWith("\"")) {
                "\"" + this + "\""
            } else {
                this
            }
            config.setProperty("hibernate.default_schema", schemaName)
        }
        return config
    }

    // Maps to a byte array on postgres.
    object MapBlobToPostgresByteA : AbstractSingleColumnStandardBasicType<ByteArray>(VarbinaryTypeDescriptor.INSTANCE, PrimitiveByteArrayTypeDescriptor.INSTANCE) {
        override fun getRegistrationKeys(): Array<String> {
            return arrayOf(name, "ByteArray", ByteArray::class.java.name)
        }

        override fun getName(): String {
            return "corda-blob"
        }
    }

    override val databaseType: String = "PostgreSQL"
}