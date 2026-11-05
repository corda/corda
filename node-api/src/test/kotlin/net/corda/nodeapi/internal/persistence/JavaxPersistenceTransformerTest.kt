package net.corda.nodeapi.internal.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityManager
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.CONVERTER
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.DUAL_ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.HIBERNATE_SESSION_USER
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.PLAIN
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.REPOSITORY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.TYPE_DEF_ENTITY
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.UNKNOWN_TYPE_ENTITY
import net.corda.nodeapi.internal.persistence.factory.CordaSqlTypes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.annotations.JdbcTypeCode
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Proxy
import java.sql.Types
import java.util.UUID
import java.util.function.Function
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

class JavaxPersistenceTransformerTest {
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

    /** Defines the fixture's classes from their converted bytes; everything else comes from the test classpath (which has jakarta.persistence). */
    private class ConvertingLoader(private val fixture: JavaxPersistenceFixture) : ClassLoader(JavaxPersistenceTransformerTest::class.java.classLoader) {
        override fun findClass(name: String): Class<*> {
            val bytes = try {
                fixture.classBytes(name)
            } catch (e: java.nio.file.NoSuchFileException) {
                throw ClassNotFoundException(name, e)
            }
            val converted = if (JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(bytes)) JavaxPersistenceTransformer.transform(bytes, name) else bytes
            return defineClass(name, converted, 0, converted.size)
        }
    }

    private val loader = ConvertingLoader(fixture)

    @Test(timeout = 300_000)
    fun `the persistence annotations become jakarta persistence annotations, nested annotations and enum values included`() {
        val entity = loader.loadClass(ENTITY)
        assertThat(entity.getAnnotation(Entity::class.java)).isNotNull
        val table = entity.getAnnotation(Table::class.java)!!
        assertThat(table.name).isEqualTo("legacy_entity")
        assertThat(table.indexes.single().name).isEqualTo("legacy_name_idx")
        assertThat(table.indexes.single().columnList).isEqualTo("name")
        val id = entity.getDeclaredField("id")
        assertThat(id.getAnnotation(Id::class.java)).isNotNull
        assertThat(id.getAnnotation(Column::class.java)!!.name).isEqualTo("id")
        assertThat(id.getAnnotation(Column::class.java)!!.nullable).isFalse()
        assertThat(entity.getDeclaredField("status").getAnnotation(Enumerated::class.java)!!.value).isEqualTo(EnumType.STRING)
    }

    @Test(timeout = 300_000)
    fun `no reference to javax persistence is left in the converted class`() {
        val converted = JavaxPersistenceTransformer.transform(fixture.classBytes(ENTITY), ENTITY)
        assertThat(String(converted, Charsets.ISO_8859_1)).doesNotContain("javax/persistence").contains("jakarta/persistence")
        assertThat(JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(converted)).isFalse()
    }

    @Test(timeout = 300_000)
    fun `an attribute converter implements the jakarta interface and the Convert annotation names it`() {
        val entity = loader.loadClass(ENTITY)
        val converterClass = entity.getDeclaredField("converted").getAnnotation(Convert::class.java)!!.converter.java
        assertThat(converterClass.name).isEqualTo(CONVERTER)
        assertThat(converterClass.isAnnotationPresent(Converter::class.java)).isTrue()
        @Suppress("UNCHECKED_CAST")
        val converter = converterClass.getDeclaredConstructor().newInstance() as AttributeConverter<UUID, String>
        val uuid = UUID.randomUUID()
        assertThat(converter.convertToDatabaseColumn(uuid)).isEqualTo(uuid.toString())
        assertThat(converter.convertToEntityAttribute(uuid.toString())).isEqualTo(uuid)
    }

    @Test(timeout = 300_000)
    fun `the Hibernate 5 type annotations Corda documents are replaced by the JdbcTypeCode of their Hibernate 7 mapping`() {
        val entity = loader.loadClass(ENTITY)
        val typed = entity.getDeclaredField("typed")
        assertThat(typed.getAnnotation(JdbcTypeCode::class.java)!!.value).isEqualTo(Types.VARCHAR)
        assertThat(typed.getAnnotation(org.hibernate.annotations.Type::class.java)).isNull()
        val bytes = entity.getDeclaredField("bytes")
        assertThat(bytes.getAnnotation(JdbcTypeCode::class.java)!!.value).isEqualTo(Types.VARBINARY)
        assertThat(bytes.getAnnotation(org.hibernate.annotations.Type::class.java)).isNull()
        // The remaining names Corda 4.14 registered, and the standard Hibernate 5 names with a fixed JDBC type.
        val expected = mapOf(
                "blob" to CordaSqlTypes.CORDA_BLOB,
                "materialized" to Types.BLOB,
                "binaryUuid" to Types.BINARY,
                "raw" to Types.VARBINARY,
                "text" to Types.LONGVARCHAR
        )
        for ((field, code) in expected) {
            assertThat(entity.getDeclaredField(field).getAnnotation(JdbcTypeCode::class.java)!!.value).describedAs(field).isEqualTo(code)
            assertThat(entity.getDeclaredField(field).getAnnotation(org.hibernate.annotations.Type::class.java)).describedAs(field).isNull()
        }
    }

    @Test(timeout = 300_000)
    fun `a class that uses Hibernate classes beyond its annotations is reported, one that only uses the annotations is not`() {
        assertThat(JavaxPersistenceTransformer.referencesHibernateBeyondAnnotations(fixture.classBytes(HIBERNATE_SESSION_USER))).isTrue()
        // The entity uses @Type, @Convert etc.: annotations only.
        assertThat(JavaxPersistenceTransformer.referencesHibernateBeyondAnnotations(fixture.classBytes(ENTITY))).isFalse()
        assertThat(JavaxPersistenceTransformer.referencesHibernateBeyondAnnotations(fixture.classBytes(REPOSITORY))).isFalse()
        // Such a class is still converted (the Session reference is left alone), so it loads and fails only when the code runs.
        val user = loader.loadClass(HIBERNATE_SESSION_USER)
        assertThat(user.getMethod("save", EntityManager::class.java, Any::class.java)).isNotNull
    }

    @Test(timeout = 300_000)
    fun `code using the entity manager, a lambda typed on it and its exceptions runs against a jakarta entity manager`() {
        val repository = loader.loadClass(REPOSITORY)
        // The parameter type of the method has been renamed, so this lookup succeeds.
        val save = repository.getMethod("save", EntityManager::class.java, Any::class.java)
        val persisted = ArrayList<Any?>()
        val entityManager = entityManager { method, args ->
            when (method.name) {
                "persist" -> persisted += args!![0]
                "find" -> return@entityManager "found ${args!![1]}"
                else -> throw UnsupportedOperationException(method.name)
            }
            null
        }
        val entity = loader.loadClass(ENTITY).getDeclaredConstructor().newInstance()
        assertThat(save.invoke(null, entityManager, entity)).isEqualTo("saved")
        assertThat(persisted).containsExactly(entity)

        // The exception table names the renamed exception type, so the jakarta exception is caught.
        val failing = entityManager { _, _ -> throw EntityExistsException("duplicate") }
        assertThat(save.invoke(null, failing, entity)).isEqualTo("caught EntityExistsException")

        // The lambda's invokedynamic bootstrap arguments name the renamed interface.
        @Suppress("UNCHECKED_CAST")
        val finder = repository.getMethod("finder", String::class.java).invoke(null, "42") as Function<Any, Any?>
        assertThat(finder.apply(entityManager)).isEqualTo("found 42")
    }

    @Test(timeout = 300_000)
    fun `the Kotlin metadata of a converted Kotlin class names the renamed types, so Kotlin reflection works on it`() {
        val className = KotlinPersistenceUser::class.java.name
        val jakartaBytes = javaClass.classLoader.getResourceAsStream(className.replace('.', '/') + ".class")!!.use { it.readBytes() }
        // A Kotlin class built for javax.persistence, metadata included, as the Kotlin compiler would have produced for Corda 4.14.
        val javaxBytes = JavaxPersistenceTransformer.rename(jakartaBytes, "jakarta/persistence/", "javax/persistence/")
        assertThat(JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(javaxBytes)).isTrue()

        val converted = JavaxPersistenceTransformer.transform(javaxBytes, className)
        assertThat(String(converted, Charsets.ISO_8859_1)).doesNotContain("javax/persistence")
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name != className) return super.loadClass(name, resolve)
                return findLoadedClass(name) ?: defineClass(name, converted, 0, converted.size)
            }
        }
        val kotlinClass = loader.loadClass(className).kotlin
        // Kotlin reflection resolves members through the metadata: a stale javax type there fails with KotlinReflectionInternalError.
        val persist = kotlinClass.memberFunctions.single { it.name == "persist" }
        assertThat(persist.parameters.map { it.type.jvmErasure }).containsExactly(kotlinClass, EntityManager::class, Any::class)
        assertThat(kotlinClass.memberProperties.single().returnType.jvmErasure).isEqualTo(jakarta.persistence.Query::class)
        val persisted = ArrayList<Any?>()
        val entityManager = entityManager { method, args -> if (method.name == "persist") persisted += args!![0]; null }
        val instance = kotlinClass.primaryConstructor!!.call(null)
        assertThat(persist.call(instance, entityManager, "x")).isEqualTo("persisted x")
        assertThat(persisted).containsExactly("x")
    }

    @Test(timeout = 300_000)
    fun `a class that does not refer to the persistence API is not converted`() {
        assertThat(JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(fixture.classBytes(PLAIN))).isFalse()
    }

    @Test(timeout = 300_000)
    fun `a class annotated for both persistence APIs is not converted and loads with its jakarta annotations`() {
        assertThat(JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(fixture.classBytes(DUAL_ENTITY))).isFalse()
        val entity = loader.loadClass(DUAL_ENTITY)
        assertThat(entity.annotations.map { it.annotationClass.java }).containsExactly(Entity::class.java)
        assertThat(entity.getDeclaredField("id").getAnnotation(Id::class.java)).isNotNull
    }

    @Test(timeout = 300_000)
    fun `a Hibernate 5 type with no Hibernate 7 mapping fails the conversion naming the class and the member`() {
        assertThatThrownBy { JavaxPersistenceTransformer.transform(fixture.classBytes(UNKNOWN_TYPE_ENTITY), UNKNOWN_TYPE_ENTITY) }
                .isInstanceOf(JavaxPersistenceConversionError::class.java)
                .hasMessageContaining(UNKNOWN_TYPE_ENTITY)
                .hasMessageContaining("my-custom-type")
                .hasMessageContaining(" on id,")
                .hasMessageContaining("rebuilt for Corda 4.15")
    }

    @Test(timeout = 300_000)
    fun `a Hibernate 5 type definition fails the conversion`() {
        assertThatThrownBy { JavaxPersistenceTransformer.transform(fixture.classBytes(TYPE_DEF_ENTITY), TYPE_DEF_ENTITY) }
                .isInstanceOf(JavaxPersistenceConversionError::class.java)
                .hasMessageContaining(TYPE_DEF_ENTITY)
                .hasMessageContaining("@TypeDef")
    }

    private fun entityManager(handler: (java.lang.reflect.Method, Array<Any?>?) -> Any?): EntityManager {
        return Proxy.newProxyInstance(loader, arrayOf(EntityManager::class.java)) { _, method, args -> handler(method, args) } as EntityManager
    }
}
