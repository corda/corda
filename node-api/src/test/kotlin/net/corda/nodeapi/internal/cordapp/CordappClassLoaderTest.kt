package net.corda.nodeapi.internal.cordapp

import jakarta.persistence.Entity
import net.corda.common.javaxpersistence.JavaxPersistenceConversionError
import net.corda.common.javaxpersistence.JavaxPersistenceTransformer
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.DUAL_ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.PLAIN
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.STUB_ENTITY_ANNOTATION
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.UNKNOWN_TYPE_ENTITY
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CordappClassLoaderTest {
    companion object {
        @ClassRule
        @JvmField
        val tempFolder = TemporaryFolder()

        private lateinit var fixture: JavaxPersistenceFixture

        @BeforeClass
        @JvmStatic
        fun compileFixture() {
            fixture = JavaxPersistenceFixture(tempFolder.root.toPath())
        }
    }

    private fun loader() = CordappClassLoader(arrayOf(fixture.jar.toUri().toURL()), CordappClassLoaderTest::class.java.classLoader)

    @Test(timeout = 300_000)
    fun `a class built for javax persistence is converted as it is loaded from the CorDapp jar`() {
        loader().use { loader ->
            val entity = loader.loadClass(ENTITY)
            assertThat(entity.isAnnotationPresent(Entity::class.java)).isTrue()
            assertThat(loader.convertedClassNames).containsExactly(ENTITY)
            // The jar itself is untouched: the class file in it still refers to javax.persistence only.
            val resource = loader.getResourceAsStream(ENTITY.replace('.', '/') + ".class")!!.use { it.readBytes() }
            assertThat(JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(resource)).isTrue()
        }
    }

    @Test(timeout = 300_000)
    fun `converted and unconverted classes get the code source and package a URLClassLoader would give them`() {
        loader().use { loader ->
            for (className in listOf(ENTITY, PLAIN, DUAL_ENTITY)) {
                val clazz = loader.loadClass(className)
                assertThat(clazz.protectionDomain.codeSource.location).isEqualTo(fixture.jar.toUri().toURL())
                assertThat(clazz.`package`.name).isEqualTo(className.substringBeforeLast('.'))
            }
            assertThat(loader.convertedClassNames).containsExactly(ENTITY)
            assertThat(loader.loadClass(PLAIN).getMethod("hello").invoke(null)).isEqualTo("hello")
        }
    }

    @Test(timeout = 300_000)
    fun `a class that cannot be converted fails to load with the conversion error`() {
        loader().use { loader ->
            assertThatThrownBy { loader.loadClass(UNKNOWN_TYPE_ENTITY) }
                    .isInstanceOf(JavaxPersistenceConversionError::class.java)
                    .hasMessageContaining("my-custom-type")
        }
    }

    @Test(timeout = 300_000)
    fun `a CorDapp that bundles the javax persistence API keeps the API classes under their own names`() {
        CordappClassLoader(arrayOf(fixture.jarWithBundledApi.toUri().toURL()), CordappClassLoaderTest::class.java.classLoader).use { loader ->
            // The bundled API class refers to its own package, so it looks built for javax.persistence, but renaming it would give it a
            // name other than the one it is loaded under.
            val apiClass = loader.loadClass(STUB_ENTITY_ANNOTATION)
            assertThat(apiClass.name).isEqualTo(STUB_ENTITY_ANNOTATION)
            assertThat(apiClass.classLoader).isSameAs(loader)
            assertThat(loader.convertedClassNames).isEmpty()
            // The CorDapp's own classes are still converted, and refer to the node's jakarta.persistence rather than the bundled API.
            val entity = loader.loadClass(ENTITY)
            assertThat(entity.isAnnotationPresent(Entity::class.java)).isTrue()
            assertThat(loader.convertedClassNames).containsExactly(ENTITY)
        }
    }

    @Test(timeout = 300_000)
    fun `classes available from the parent are loaded by the parent`() {
        loader().use { loader ->
            assertThat(loader.loadClass(Entity::class.java.name)).isSameAs(Entity::class.java)
            assertThat(loader.convertedClassNames).isEmpty()
        }
    }
}
