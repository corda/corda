package net.corda.nodeapi.internal.persistence.factory

import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataBuilder
import javax.persistence.AttributeConverter

class H2SessionFactoryFactory : BaseSessionFactoryFactory(){
      override fun canHandleDatabase(jdbcUrl: String): Boolean = jdbcUrl.startsWith("jdbc:h2:")
}