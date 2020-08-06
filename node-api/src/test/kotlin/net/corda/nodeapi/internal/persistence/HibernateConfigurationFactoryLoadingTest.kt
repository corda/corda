package net.corda.nodeapi.internal.persistence

import com.nhaarman.mockito_kotlin.mock
import net.corda.core.internal.NamedCacheFactory
import org.junit.Assert
import org.junit.Test

class HibernateConfigurationFactoryLoadingTest {
    @Test(timeout=300_000)
    fun checkErrorMessageForMissingFactory() {
        val jdbcUrl = "jdbc:madeUpNonense:foobar.com:1234"
        val presentFactories = listOf("H2", "PostgreSQL")
        try {
            val cacheFactory = mock<NamedCacheFactory>()
            HibernateConfiguration(
                    emptySet(),
                    false,
                    emptyList(),
                    jdbcUrl,
                    cacheFactory)
            Assert.fail("Expected exception not thrown")
        } catch (e: HibernateConfigException) {
            Assert.assertEquals("Failed to find a SessionFactoryFactory to handle $jdbcUrl - factories present for ${presentFactories}", e.message)
        }
    }
}