package net.corda.testing.javaxpersistence

import jakarta.persistence.Entity
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityManager
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.DUAL_ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.HIBERNATE_SESSION_USER
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.JAVAX_ENTITY_ANNOTATION
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.PLAIN
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.REPOSITORY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.TYPE_DEF_ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.UNKNOWN_TYPE_ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceTransformer
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.annotations.JdbcTypeCode
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.management.ManagementFactory
import java.lang.reflect.Proxy
import java.net.URLClassLoader
import java.nio.file.Path
import java.sql.Types
import java.util.jar.JarFile
import kotlin.io.path.toPath

/**
 * The agent, run from its jar (`-javaagent`, see the test task of this module), converting classes built against `javax.persistence` that
 * an ordinary class loader of the test JVM defines, as it does for the classes on a test classpath, through the node's own converter.
 */
class JavaxPersistenceTestAgentTest {
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

    /** A plain class loader, which does no conversion of its own: whatever is converted is converted by the agent. */
    private fun loader(jar: Path = fixture.jar) = URLClassLoader(arrayOf(jar.toUri().toURL()), JavaxPersistenceTestAgentTest::class.java.classLoader)

    @Test(timeout = 300_000)
    fun `the test JVM runs the agent from its jar, which holds nothing but the agent`() {
        assertThat(ManagementFactory.getRuntimeMXBean().inputArguments)
                .anyMatch { it.startsWith("-javaagent:") && it.contains("corda-javax-persistence-test-agent") }
        assertThat(System.getProperty(JavaxPersistenceTransformer.TEST_AGENT_INSTALLED_PROPERTY)).isEqualTo("true")
        // The agent class comes from the jar, not from this module's main output; the converter it calls is node-api's.
        val agentJar = JavaxPersistenceTestAgent::class.java.protectionDomain.codeSource.location.toURI().toPath()
        assertThat(agentJar.toString()).endsWith(".jar").contains("corda-javax-persistence-test-agent")
        JarFile(agentJar.toFile()).use { jar ->
            val classes = jar.entries().asSequence().map { it.name }.filter { it.endsWith(".class") }.toList()
            assertThat(classes).isNotEmpty.allMatch { it.startsWith("net/corda/testing/javaxpersistence/") }
        }
        assertThat(JavaxPersistenceTransformer::class.java.protectionDomain.codeSource.location.toURI().toPath()).isNotEqualTo(agentJar)
    }

    @Test(timeout = 300_000)
    fun `a class built for javax persistence is converted whichever class loader defines it`() {
        loader().use { loader ->
            val entity = loader.loadClass(ENTITY)
            assertThat(entity.isAnnotationPresent(Entity::class.java)).isTrue()
            assertThat(entity.getDeclaredField("typed").getAnnotation(JdbcTypeCode::class.java)!!.value).isEqualTo(Types.VARCHAR)
            // The class file itself is untouched.
            assertThat(JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(fixture.classBytes(ENTITY))).isTrue()
            assertThat(loader.loadClass(PLAIN).getMethod("hello").invoke(null)).isEqualTo("hello")
        }
    }

    @Test(timeout = 300_000)
    fun `code typed on the entity manager runs against a jakarta entity manager`() {
        loader().use { loader ->
            val repository = loader.loadClass(REPOSITORY)
            val save = repository.getMethod("save", EntityManager::class.java, Any::class.java)
            val persisted = ArrayList<Any?>()
            val entityManager = Proxy.newProxyInstance(loader, arrayOf(EntityManager::class.java)) { _, method, args ->
                when (method.name) {
                    "persist" -> { persisted += args[0]; null }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as EntityManager
            val entity = loader.loadClass(ENTITY).getDeclaredConstructor().newInstance()
            assertThat(save.invoke(null, entityManager, entity)).isEqualTo("saved")
            assertThat(persisted).containsExactly(entity)
            val failing = Proxy.newProxyInstance(loader, arrayOf(EntityManager::class.java)) { _, _, _ -> throw EntityExistsException("duplicate") } as EntityManager
            assertThat(save.invoke(null, failing, entity)).isEqualTo("caught EntityExistsException")
        }
    }

    @Test(timeout = 300_000)
    fun `a class annotated for both persistence APIs is left alone`() {
        loader().use { loader ->
            val entity = loader.loadClass(DUAL_ENTITY)
            assertThat(entity.annotations.map { it.annotationClass.java }).containsExactly(Entity::class.java)
        }
    }

    @Test(timeout = 300_000)
    fun `a CorDapp that bundles the javax persistence API keeps the API classes under their own names`() {
        loader(fixture.fatJar).use { loader ->
            val apiClass = loader.loadClass(JAVAX_ENTITY_ANNOTATION)
            assertThat(apiClass.name).isEqualTo(JAVAX_ENTITY_ANNOTATION)
            assertThat(apiClass.isAnnotation).isTrue()
            assertThat(apiClass.classLoader).isSameAs(loader)
            // The CorDapp's own classes are converted, and refer to jakarta.persistence rather than to the bundled API.
            val entity = loader.loadClass(ENTITY)
            assertThat(entity.isAnnotationPresent(Entity::class.java)).isTrue()
        }
    }

    @Test(timeout = 300_000)
    fun `a class that cannot be converted is loaded unconverted and the reason is reported and recorded`() {
        val console = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(console, true))
        try {
            loader().use { loader ->
                val unknownType = loader.loadClass(UNKNOWN_TYPE_ENTITY)
                // The javax.persistence API is not on the classpath, so the JVM drops the annotations of the unconverted class.
                assertThat(unknownType.annotations).noneMatch { it.annotationClass.java.name.startsWith("jakarta.persistence.") }
                assertThat(System.getProperty(JavaxPersistenceTransformer.TEST_AGENT_FAILURE_PROPERTY_PREFIX + UNKNOWN_TYPE_ENTITY))
                        .contains("@Type(type = \"my-custom-type\") on id")
                        .contains("rebuilt for Corda 4.15")

                loader.loadClass(TYPE_DEF_ENTITY)
                assertThat(System.getProperty(JavaxPersistenceTransformer.TEST_AGENT_FAILURE_PROPERTY_PREFIX + TYPE_DEF_ENTITY))
                        .contains("@TypeDef")
                        .contains("rebuilt for Corda 4.15")
            }
        } finally {
            System.setErr(originalErr)
        }
        val output = console.toString()
        assertThat(output)
                .contains("[corda-javax-persistence-test-agent] The class $UNKNOWN_TYPE_ENTITY was left as built for javax.persistence")
                .contains("my-custom-type")
                .contains("[corda-javax-persistence-test-agent] The class $TYPE_DEF_ENTITY was left as built for javax.persistence")
                .contains("@TypeDef")
    }

    @Test(timeout = 300_000)
    fun `the node's check of Hibernate references applies under the agent too`() {
        // The class calls Session.save, which Hibernate 7 no longer has; the node refuses it, and so does the agent, through the same converter
        // resolving against the Hibernate 7 of the test classpath.
        loader(fixture.sessionUserJar).use { loader ->
            loader.loadClass(HIBERNATE_SESSION_USER)
            assertThat(System.getProperty(JavaxPersistenceTransformer.TEST_AGENT_FAILURE_PROPERTY_PREFIX + HIBERNATE_SESSION_USER))
                    .contains("do not exist in Hibernate 7")
                    .contains("rebuilt for Corda 4.15")
        }
    }

    @Test(timeout = 300_000)
    fun `a converted class is not recorded as a failure`() {
        loader().use { loader ->
            loader.loadClass(ENTITY)
            assertThat(System.getProperty(JavaxPersistenceTransformer.TEST_AGENT_FAILURE_PROPERTY_PREFIX + ENTITY)).isNull()
        }
    }
}
