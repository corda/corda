package net.corda.nodeapi.internal.cordapp

import jakarta.persistence.Entity
import net.corda.nodeapi.internal.persistence.CordappConversionException
import net.corda.nodeapi.internal.persistence.JavaxPersistenceConversionError
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.DUAL_ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.HIBERNATE_BUNDLED_USER
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.HIBERNATE_COMPATIBLE_USER
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.HIBERNATE_SESSION_USER
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.INTRUDER
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.JAVAX_ENTITY_ANNOTATION
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.PLAIN
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.UNKNOWN_TYPE_ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceTransformer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLClassLoader

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
    fun `a class that uses a Hibernate member that no longer exists fails to load with the conversion error`() {
        loader().use { loader ->
            assertThatThrownBy { loader.loadClass(HIBERNATE_SESSION_USER) }
                    .isInstanceOf(JavaxPersistenceConversionError::class.java)
                    .hasMessageContaining("method org.hibernate.Session.save(java.lang.Object)")
            assertThat(loader.convertedClassNames).isEmpty()
            // One that only uses what Hibernate 7 still has converts.
            loader.loadClass(HIBERNATE_COMPATIBLE_USER)
            assertThat(loader.convertedClassNames).containsExactly(HIBERNATE_COMPATIBLE_USER)
        }
    }

    @Test(timeout = 300_000)
    fun `a loader configured to warn about missing Hibernate references loads such a class after reporting it once`() {
        CordappClassLoader(arrayOf(fixture.sessionUserJar.toUri().toURL()), javaClass.classLoader, refuseMissingReferences = false).use { loader ->
            loader.checkConvertible()
            assertThat(loader.loadClass(HIBERNATE_SESSION_USER).name).isEqualTo(HIBERNATE_SESSION_USER)
            assertThat(loader.convertedClassNames).containsExactly(HIBERNATE_SESSION_USER)
        }
    }

    @Test(timeout = 300_000)
    fun `the start-up check reports every class that cannot be converted, as an exception, without defining anything`() {
        CordappClassLoader(arrayOf(fixture.sessionUserJar.toUri().toURL()), javaClass.classLoader).use { loader ->
            assertThatThrownBy { loader.checkConvertible() }
                    .isInstanceOf(CordappConversionException::class.java)
                    .hasMessageContaining("1 class(es)")
                    .hasMessageContaining(HIBERNATE_SESSION_USER)
                    .hasMessageContaining("org.hibernate.Session.save")
            assertThat(loader.convertedClassNames).isEmpty()
        }
        // Every unconvertible class of the jar is named in the one message, so that an operator does not find them one restart at a time.
        loader().use { loader ->
            val thrown = assertThatThrownBy { loader.checkConvertible() }
                    .isInstanceOf(CordappConversionException::class.java)
                    .hasMessageContaining("${JavaxPersistenceFixture.unconvertibleClasses.size} class(es)")
            JavaxPersistenceFixture.unconvertibleClasses.forEach { thrown.hasMessageContaining(it) }
            assertThat(loader.convertedClassNames).isEmpty()
        }
        // A Hibernate class the CorDapp bundles but the node also has is resolved against the node's copy, which is the one that runs.
        CordappClassLoader(arrayOf(fixture.bundledSessionJar.toUri().toURL()), javaClass.classLoader).use { loader ->
            assertThatThrownBy { loader.checkConvertible() }
                    .isInstanceOf(CordappConversionException::class.java)
                    .hasMessageContaining("org.hibernate.Session.save")
        }
        CordappClassLoader(arrayOf(fixture.cleanJar.toUri().toURL()), javaClass.classLoader).use { loader ->
            // The clean jar also has a class using a bundled Hibernate library (org.hibernate.validator), which the jar provides itself.
            loader.checkConvertible()
            assertThat(loader.convertedClassNames).isEmpty()
            assertThat(loader.loadClass(ENTITY).isAnnotationPresent(Entity::class.java)).isTrue()
            assertThat(loader.convertedClassNames).containsExactly(ENTITY)
            assertThat(loader.loadClass(HIBERNATE_BUNDLED_USER).getMethod("validate", jakarta.persistence.EntityManager::class.java, Any::class.java)
                    .invoke(null, null, "x")).isEqualTo(true)
        }
    }

    @Test(timeout = 300_000)
    fun `a sealed package accepts classes from its own jar only, as in a URLClassLoader`() {
        val sealedFirst = CordappClassLoader(arrayOf(fixture.sealedJar.toUri().toURL(), fixture.intruderJar.toUri().toURL()), javaClass.classLoader)
        sealedFirst.use { loader ->
            assertThat(loader.loadClass(PLAIN).`package`.isSealed).isTrue()
            assertThatThrownBy { loader.loadClass(INTRUDER) }
                    .isInstanceOf(SecurityException::class.java)
                    .hasMessageContaining("package legacy is sealed")
        }
        val intruderFirst = CordappClassLoader(arrayOf(fixture.intruderJar.toUri().toURL(), fixture.sealedJar.toUri().toURL()), javaClass.classLoader)
        intruderFirst.use { loader ->
            assertThat(loader.loadClass(INTRUDER).`package`.isSealed).isFalse()
            assertThatThrownBy { loader.loadClass(PLAIN) }
                    .isInstanceOf(SecurityException::class.java)
                    .hasMessageContaining("can't seal package legacy")
        }
        // The same checks URLClassLoader applies.
        URLClassLoader(arrayOf(fixture.sealedJar.toUri().toURL(), fixture.intruderJar.toUri().toURL()), javaClass.classLoader).use { loader ->
            loader.loadClass(PLAIN)
            assertThatThrownBy { loader.loadClass(INTRUDER) }.isInstanceOf(SecurityException::class.java)
        }
    }

    @Test(timeout = 300_000)
    fun `a bundled javax persistence API class is defined as it is, not renamed`() {
        CordappClassLoader(arrayOf(fixture.fatJar.toUri().toURL()), javaClass.classLoader).use { loader ->
            val javaxEntity = loader.loadClass(JAVAX_ENTITY_ANNOTATION)
            assertThat(javaxEntity.name).isEqualTo(JAVAX_ENTITY_ANNOTATION)
            assertThat(javaxEntity.classLoader).isSameAs(loader)
            assertThat(loader.convertedClassNames).isEmpty()
            // With the API present, a dual-annotated class now carries both annotations; a javax-only one is still converted.
            assertThat(loader.loadClass(DUAL_ENTITY).annotations.map { it.annotationClass.java.name })
                    .containsExactlyInAnyOrder(JAVAX_ENTITY_ANNOTATION, Entity::class.java.name)
            assertThat(loader.loadClass(ENTITY).isAnnotationPresent(Entity::class.java)).isTrue()
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
