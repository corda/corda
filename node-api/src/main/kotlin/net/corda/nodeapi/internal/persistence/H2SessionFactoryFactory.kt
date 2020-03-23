package net.corda.nodeapi.internal.persistence

import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import javax.persistence.AttributeConverter

class H2SessionFactoryFactory : BaseSessionFactoryFactory(){
    override fun buildHibernateMetadata(metadataBuilder: MetadataBuilder, attributeConverters: Collection<AttributeConverter<*, *>>): Metadata {
        metadataBuilder.run {
            attributeConverters.forEach { applyAttributeConverter(it) }
            // Register a tweaked version of `org.hibernate.type.MaterializedBlobType` that truncates logged messages.
            // to avoid OOM when large blobs might get logged.
            applyBasicType(CordaMaterializedBlobType, CordaMaterializedBlobType.name)
            applyBasicType(CordaWrapperBinaryType, CordaWrapperBinaryType.name)
            applyBasicType(MapBlobToNormalBlob, MapBlobToNormalBlob.name)

            return build()
        }
    }


    override fun canHandleDatabase(jdbcUrl: String): Boolean = jdbcUrl.startsWith("jdbc:h2:")
}