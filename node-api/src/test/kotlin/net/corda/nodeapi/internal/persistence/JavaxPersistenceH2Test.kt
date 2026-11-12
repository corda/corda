package net.corda.nodeapi.internal.persistence

import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.CASCADE_CHILD
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.CASCADE_PARENT
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.CHAR_ENTITY
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.sql.DriverManager
import java.util.UUID

/**
 * Runs classes built for `javax.persistence` and Hibernate 5, converted by the node, against the real Hibernate 7 on an H2 database whose
 * tables are what a 4.14 node left behind, to pin down the behaviours the conversion preserves beyond what the annotations say.
 */
class JavaxPersistenceH2Test {
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

    private val loader = ConvertingFixtureLoader(fixture, JavaxPersistenceH2Test::class.java.classLoader)

    @Test(timeout = 300_000)
    fun `converted character, yes_no and string columns read what Hibernate 5 wrote, padded CHAR columns and blanks included, and write it back`() {
        // Hibernate 5's own DDL made a Character or yes_no column CHAR(255) on H2, which comes back space-padded. A stored space (a Kotlin
        // `var code: Char = ' '`, say, or a SQL default in a yes_no column) was a space, or false, to Hibernate 5; a primitive field cannot hold
        // anything else.
        val url = database("create table char_entity (id varchar(64) primary key, ch char(255), chp char(255), yn char(255), ynp char(255), st varchar(255))",
                "insert into char_entity (id, ch, chp, yn, ynp, st) values ('a', 'Q', ' ', 'Y', ' ', 'plain')")
        val entityClass = loader.loadClass(CHAR_ENTITY)
        sessionFactory(url, entityClass).use { factory ->
            factory.inTransaction { session ->
                val entity = session.find(entityClass, "a")
                assertThat(entityClass.getField("ch").get(entity)).isEqualTo('Q')
                assertThat(entityClass.getField("chp").get(entity)).isEqualTo(' ')
                assertThat(entityClass.getField("yn").get(entity)).isEqualTo(true)
                assertThat(entityClass.getField("ynp").get(entity)).isEqualTo(false)
                assertThat(entityClass.getField("st").get(entity)).isEqualTo("plain")
            }
            factory.inTransaction { session ->
                val entity = entityClass.getDeclaredConstructor().newInstance()
                entityClass.getField("id").set(entity, "b")
                entityClass.getField("ch").set(entity, 'Z')
                entityClass.getField("chp").set(entity, ' ')
                entityClass.getField("yn").set(entity, false)
                entityClass.getField("ynp").set(entity, true)
                entityClass.getField("st").set(entity, "written")
                session.persist(entity)
            }
            // What was written reads back, the space included.
            factory.inTransaction { session ->
                val entity = session.find(entityClass, "b")
                assertThat(entityClass.getField("ch").get(entity)).isEqualTo('Z')
                assertThat(entityClass.getField("chp").get(entity)).isEqualTo(' ')
                assertThat(entityClass.getField("yn").get(entity)).isEqualTo(false)
                assertThat(entityClass.getField("ynp").get(entity)).isEqualTo(true)
            }
        }
        assertThat(rows(url, "select id, trim(ch), trim(chp), trim(yn), trim(ynp), st from char_entity order by id"))
                .containsExactly(listOf("a", "Q", "", "Y", "", "plain"), listOf("b", "Z", "", "N", "Y", "written"))
    }

    @Test(timeout = 300_000)
    @Suppress("DEPRECATION") // Hibernate 7 deprecates @Cascade in favour of the JPA cascade, but still reads it; a 4.14 CorDapp carries it.
    fun `a save-update cascade saves new children at flush but does not cascade merge, as it did not in Hibernate 5`() {
        val url = database("create table cascade_parent (id varchar(64) primary key, name varchar(255))",
                "create table cascade_child (id varchar(64) primary key, name varchar(255), parent_id varchar(64))")
        val parentClass = loader.loadClass(CASCADE_PARENT)
        val childClass = loader.loadClass(CASCADE_CHILD)
        assertThat(parentClass.getField("children").getAnnotation(org.hibernate.annotations.Cascade::class.java)!!.value)
                .containsExactly(org.hibernate.annotations.CascadeType.PERSIST)
        sessionFactory(url, parentClass, childClass).use { factory ->
            // Persisting the parent reaches the child, as Hibernate 5's save did.
            factory.inTransaction { session ->
                val parent = newEntity(parentClass, "p", "parent")
                children(parent).add(newChild(childClass, "c1", "orig", parent))
                session.persist(parent)
            }
            assertThat(rows(url, "select name from cascade_child order by id")).containsExactly(listOf("orig"))

            // Merging a detached parent whose child changed leaves the child alone: Hibernate 5's save-update cascade never fired for merge.
            val detached = factory.fromTransaction { session -> session.find(parentClass, "p").also { children(it).size } }
            childClass.getField("name").set(children(detached).single(), "changed-by-merge")
            factory.inTransaction { session -> session.merge(detached) }
            assertThat(rows(url, "select name from cascade_child order by id")).containsExactly(listOf("orig"))

            // A new child added to a managed parent is saved when the session flushes, as Hibernate 5's flush-time save-update did.
            factory.inTransaction { session ->
                val parent = session.find(parentClass, "p")
                children(parent).add(newChild(childClass, "c2", "new", parent))
            }
            assertThat(rows(url, "select name from cascade_child order by id")).containsExactly(listOf("orig"), listOf("new"))
        }
    }

    private fun newEntity(type: Class<*>, id: String, name: String): Any {
        return type.getDeclaredConstructor().newInstance().also {
            type.getField("id").set(it, id)
            type.getField("name").set(it, name)
        }
    }

    private fun newChild(childClass: Class<*>, id: String, name: String, parent: Any): Any {
        return newEntity(childClass, id, name).also { childClass.getField("parent").set(it, parent) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun children(parent: Any): MutableList<Any> = parent.javaClass.getField("children").get(parent) as MutableList<Any>

    /** An in-memory H2 database that outlives its connections, with the given statements run against it. */
    private fun database(vararg statements: String): String {
        val url = "jdbc:h2:mem:javax_${UUID.randomUUID().toString().replace("-", "")};DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
        }
        return url
    }

    private fun rows(url: String, query: String): List<List<Any?>> {
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(query).use { results ->
                    val columns = results.metaData.columnCount
                    return generateSequence { if (results.next()) (1..columns).map { results.getObject(it) } else null }.toList()
                }
            }
        }
    }

    /** Hibernate 7 over the converted classes, which it looks up by name, so through the loader that converted them. */
    private fun sessionFactory(url: String, vararg entityClasses: Class<*>): SessionFactory {
        val bootstrap = BootstrapServiceRegistryBuilder().applyClassLoader(loader).build()
        val registry = StandardServiceRegistryBuilder(bootstrap)
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver") // On the test runtime classpath only.
                .applySetting("hibernate.dialect", H2Dialect::class.java.name)
                .build()
        val sources = MetadataSources(registry)
        entityClasses.forEach { sources.addAnnotatedClass(it) }
        return sources.buildMetadata().buildSessionFactory()
    }
}
