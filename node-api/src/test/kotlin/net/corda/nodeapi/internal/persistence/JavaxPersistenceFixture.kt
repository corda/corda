package net.corda.nodeapi.internal.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

/**
 * Classes built against `javax.persistence` the way a CorDapp for Corda 4.14 or earlier is built: Java sources compiled at test time
 * (with the JDK's compiler) against a stub of the `javax.persistence` API that declares just what the sources use, since the real API is
 * not on the classpath of a Corda 4.15 build. The result is a directory of class files and a jar of them, both referring to
 * `javax.persistence` only.
 */
class JavaxPersistenceFixture(root: Path) {
    companion object {
        const val ENTITY = "legacy.LegacyEntity"
        const val CONVERTER = "legacy.UuidConverter"
        const val REPOSITORY = "legacy.LegacyRepository"
        const val PLAIN = "legacy.Plain"
        const val UNKNOWN_TYPE_ENTITY = "legacy.UnknownTypeEntity"
        const val TYPE_DEF_ENTITY = "legacy.TypeDefEntity"
        const val SUSPENDING_REPOSITORY = "legacy.SuspendingRepository"
        const val SUSPENDING_CALLER = "legacy.SuspendingCaller"
        const val HIBERNATE_SESSION_USER = "legacy.HibernateSessionUser"
        const val HIBERNATE_TYPE_CLASS_USER = "legacy.HibernateTypeClassUser"
        const val HIBERNATE_COMPATIBLE_USER = "legacy.HibernateCompatibleUser"
        const val HIBERNATE_BUNDLED_USER = "legacy.HibernateBundledUser"
        const val BUNDLED_HIBERNATE_CLASS = "org.hibernate.validator.BundledValidator"
        const val INTRUDER = "legacy.Intruder"
        const val JAVAX_ENTITY_ANNOTATION = "javax.persistence.Entity"
        const val DUAL_ENTITY = "dual.DualEntity"

        /** A class annotated for both persistence APIs, as a CorDapp built to run on Corda 4.14 and 4.15 alike carries them. */
        private val dualSources = mapOf(
                "dual/DualEntity.java" to """
                    package dual;
                    @javax.persistence.Entity @jakarta.persistence.Entity
                    public class DualEntity {
                        @javax.persistence.Id @jakarta.persistence.Id public String id;
                    }
                """.trimIndent()
        )

        private val stubSources = mapOf(
                "javax/persistence/Entity.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface Entity { String name() default \"\"; }",
                "javax/persistence/Table.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface Table { String name() default \"\"; Index[] indexes() default {}; }",
                "javax/persistence/Index.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({}) public @interface Index { String name() default \"\"; String columnList(); }",
                "javax/persistence/Id.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface Id {}",
                "javax/persistence/Column.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface Column { String name() default \"\"; boolean nullable() default true; }",
                "javax/persistence/EnumType.java" to "package javax.persistence; public enum EnumType { ORDINAL, STRING }",
                "javax/persistence/Enumerated.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface Enumerated { EnumType value() default EnumType.ORDINAL; }",
                "javax/persistence/Convert.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE}) public @interface Convert { Class converter() default void.class; }",
                "javax/persistence/Converter.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface Converter { boolean autoApply() default false; }",
                "javax/persistence/AttributeConverter.java" to "package javax.persistence; public interface AttributeConverter<X, Y> { Y convertToDatabaseColumn(X attribute); X convertToEntityAttribute(Y dbData); }",
                "javax/persistence/PersistenceException.java" to "package javax.persistence; public class PersistenceException extends RuntimeException { public PersistenceException(String message) { super(message); } }",
                "javax/persistence/EntityExistsException.java" to "package javax.persistence; public class EntityExistsException extends PersistenceException { public EntityExistsException(String message) { super(message); } }",
                "javax/persistence/Query.java" to "package javax.persistence; import java.util.List; public interface Query { Query setParameter(String name, Object value); List getResultList(); }",
                "javax/persistence/EntityManager.java" to "package javax.persistence; public interface EntityManager { void persist(Object entity); <T> T find(Class<T> entityClass, Object primaryKey); Query createQuery(String qlString); <T> T unwrap(Class<T> cls); }",
                // The Hibernate 5 shapes of classes a CorDapp built for Corda 4.14 may use: save and the type descriptor are gone in Hibernate 7,
                // flush, createQuery, Query.list and the exception type still exist.
                "org/hibernate/Session.java" to "package org.hibernate; public interface Session { java.io.Serializable save(Object entity); void flush(); org.hibernate.query.Query createQuery(String hql); }",
                "org/hibernate/query/Query.java" to "package org.hibernate.query; public interface Query { java.util.List list(); }",
                "org/hibernate/exception/ConstraintViolationException.java" to "package org.hibernate.exception; public class ConstraintViolationException extends RuntimeException { public ConstraintViolationException(String message) { super(message); } }",
                "org/hibernate/type/descriptor/sql/VarbinaryTypeDescriptor.java" to "package org.hibernate.type.descriptor.sql; public class VarbinaryTypeDescriptor { public static final VarbinaryTypeDescriptor INSTANCE = new VarbinaryTypeDescriptor(); }",
                "org/hibernate/annotations/Type.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface Type { String type(); }",
                "org/hibernate/annotations/TypeDef.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.PACKAGE}) public @interface TypeDef { String name(); Class typeClass(); }"
        )

        private val cordappSources = mapOf(
                "legacy/LegacyEntity.java" to """
                    package legacy;
                    import javax.persistence.*;
                    import java.util.UUID;
                    @Entity
                    @Table(name = "legacy_entity", indexes = @Index(name = "legacy_name_idx", columnList = "name"))
                    public class LegacyEntity {
                        public enum Status { PENDING, ACTIVE }
                        @Id @Column(name = "id", nullable = false) public String id;
                        @Column(name = "name") public String name;
                        @Column(name = "status") @Enumerated(EnumType.STRING) public Status status;
                        @Column(name = "converted") @Convert(converter = UuidConverter.class) public UUID converted;
                        @Column(name = "typed") @org.hibernate.annotations.Type(type = "uuid-char") public UUID typed;
                        @Column(name = "bytes") @org.hibernate.annotations.Type(type = "corda-wrapper-binary") public byte[] bytes;
                        @Column(name = "blob") @org.hibernate.annotations.Type(type = "corda-blob") public byte[] blob;
                        @Column(name = "materialized") @org.hibernate.annotations.Type(type = "materialized_blob") public byte[] materialized;
                        @Column(name = "binary_uuid") @org.hibernate.annotations.Type(type = "uuid-binary") public UUID binaryUuid;
                        @Column(name = "raw") @org.hibernate.annotations.Type(type = "binary") public byte[] raw;
                        @Column(name = "text") @org.hibernate.annotations.Type(type = "text") public String text;
                        public LegacyEntity() {}
                    }
                """.trimIndent(),
                "legacy/HibernateSessionUser.java" to """
                    package legacy;
                    import javax.persistence.EntityManager;
                    /** Uses a Hibernate 5 Session method that no longer exists in Hibernate 7, which the conversion cannot cover. */
                    public class HibernateSessionUser {
                        public static Object save(EntityManager em, Object entity) {
                            return em.unwrap(org.hibernate.Session.class).save(entity);
                        }
                    }
                """.trimIndent(),
                "legacy/HibernateTypeClassUser.java" to """
                    package legacy;
                    /** Uses a Hibernate 5 class that no longer exists in Hibernate 7. */
                    public class HibernateTypeClassUser {
                        public static Object descriptor(javax.persistence.EntityManager em) {
                            return org.hibernate.type.descriptor.sql.VarbinaryTypeDescriptor.INSTANCE;
                        }
                    }
                """.trimIndent(),
                "legacy/HibernateCompatibleUser.java" to """
                    package legacy;
                    import javax.persistence.EntityManager;
                    import org.hibernate.Session;
                    import org.hibernate.exception.ConstraintViolationException;
                    /** Uses Hibernate classes and members that still exist in Hibernate 7: the conversion leaves them alone and the node accepts them. */
                    public class HibernateCompatibleUser {
                        public static String persist(EntityManager em, Object entity) {
                            try {
                                em.persist(entity);
                                Session session = em.unwrap(Session.class);
                                session.flush();
                                // An Object method on an interface-typed receiver: compiled with the interface as the owner.
                                String description = session.toString() + session.hashCode();
                                return "persisted, " + session.createQuery("from LegacyEntity").list().size() + " rows" + (description.isEmpty() ? "?" : "");
                            } catch (ConstraintViolationException e) {
                                return "duplicate";
                            }
                        }
                    }
                """.trimIndent(),
                // A Hibernate library the CorDapp ships itself (as Hibernate Validator would be): not the node's Hibernate, so not checked.
                "org/hibernate/validator/BundledValidator.java" to "package org.hibernate.validator; public class BundledValidator { public static boolean valid(Object o) { return o != null; } }",
                "legacy/HibernateBundledUser.java" to """
                    package legacy;
                    public class HibernateBundledUser {
                        public static boolean validate(javax.persistence.EntityManager em, Object entity) {
                            return org.hibernate.validator.BundledValidator.valid(entity);
                        }
                    }
                """.trimIndent(),
                "legacy/UuidConverter.java" to """
                    package legacy;
                    import java.util.UUID;
                    @javax.persistence.Converter
                    public class UuidConverter implements javax.persistence.AttributeConverter<UUID, String> {
                        public String convertToDatabaseColumn(UUID attribute) { return attribute == null ? null : attribute.toString(); }
                        public UUID convertToEntityAttribute(String dbData) { return dbData == null ? null : UUID.fromString(dbData); }
                    }
                """.trimIndent(),
                "legacy/LegacyRepository.java" to """
                    package legacy;
                    import javax.persistence.EntityManager;
                    import javax.persistence.PersistenceException;
                    import java.util.function.Function;
                    public class LegacyRepository {
                        /** Persists through the entity manager; a JPA exception is translated, which exercises the exception table. */
                        public static String save(EntityManager em, Object entity) {
                            try {
                                em.persist(entity);
                                return "saved";
                            } catch (PersistenceException e) {
                                return "caught " + e.getClass().getSimpleName();
                            }
                        }
                        /** A lambda typed on the entity manager: its invokedynamic bootstrap arguments name the interface. */
                        public static Function<EntityManager, Object> finder(String id) {
                            return em -> em.find(LegacyEntity.class, id);
                        }
                    }
                """.trimIndent(),
                "legacy/Plain.java" to "package legacy; public class Plain { public static String hello() { return \"hello\"; } }",
                "legacy/Intruder.java" to "package legacy; public class Intruder {}",
                "legacy/UnknownTypeEntity.java" to """
                    package legacy;
                    @javax.persistence.Entity
                    public class UnknownTypeEntity {
                        @javax.persistence.Id @org.hibernate.annotations.Type(type = "my-custom-type") public String id;
                    }
                """.trimIndent(),
                "legacy/TypeDefEntity.java" to """
                    package legacy;
                    @javax.persistence.Entity
                    @org.hibernate.annotations.TypeDef(name = "x", typeClass = Object.class)
                    public class TypeDefEntity {
                        @javax.persistence.Id public String id;
                    }
                """.trimIndent(),
                "legacy/SuspendingRepository.java" to """
                    package legacy;
                    import co.paralleluniverse.fibers.Fiber;
                    import co.paralleluniverse.fibers.Suspendable;
                    import co.paralleluniverse.fibers.suspend.SuspendExecution;
                    import co.paralleluniverse.strands.Strand;
                    import javax.persistence.EntityManager;
                    import javax.persistence.PersistenceException;
                    /** Suspendable code whose method descriptor names the persistence API, as a flow helper of a CorDapp built for Corda 4.14 might. */
                    public class SuspendingRepository {
                        @Suspendable
                        public static String persistAfterSuspending(EntityManager em, String what) throws SuspendExecution, InterruptedException {
                            Strand.sleep(10);
                            try {
                                em.persist(what);
                            } catch (PersistenceException | IllegalStateException e) {
                                return "caught " + e.getClass().getSimpleName() + " for " + what;
                            }
                            return "persisted " + what + (Fiber.isCurrentFiber() ? " in a fiber" : " on a thread");
                        }
                    }
                """.trimIndent(),
                "legacy/SuspendingCaller.java" to """
                    package legacy;
                    import co.paralleluniverse.fibers.Suspendable;
                    import co.paralleluniverse.fibers.suspend.SuspendExecution;
                    import co.paralleluniverse.strands.SuspendableCallable;
                    import javax.persistence.EntityManager;
                    /** Calls the suspendable repository through a descriptor that names the persistence API. */
                    public class SuspendingCaller implements SuspendableCallable<String> {
                        private final EntityManager em;
                        public SuspendingCaller(EntityManager em) { this.em = em; }
                        @Suspendable
                        @Override
                        public String run() throws SuspendExecution, InterruptedException {
                            return SuspendingRepository.persistAfterSuspending(em, "x") + "; " + SuspendingRepository.persistAfterSuspending(em, "y");
                        }
                    }
                """.trimIndent()
        )
    }

    private val sourceDir = (root / "src").createDirectories()
    private val stubClassesDir = (root / "stub-classes").createDirectories()

    /** The compiled CorDapp-like classes (without the stub API, which a real node does not have either). */
    val classesDir: Path = (root / "classes").createDirectories()

    /** The CorDapp-like classes as a jar. */
    val jar: Path = root / "legacy-cordapp.jar"

    /** The same classes except [INTRUDER], in a jar whose manifest seals every package. */
    val sealedJar: Path = root / "sealed-cordapp.jar"

    /** Just [INTRUDER], in the `legacy` package that [sealedJar] seals. */
    val intruderJar: Path = root / "intruder.jar"

    /** The CorDapp-like classes together with the stub `javax.persistence` API, as a CorDapp that bundles the API ships them. */
    val fatJar: Path = root / "fat-legacy-cordapp.jar"

    /** The CorDapp-like classes that convert: without the ones that use a Hibernate 5 `@Type` or `@TypeDef`, class or member with no Hibernate 7 equivalent. */
    val cleanJar: Path = root / "clean-legacy-cordapp.jar"

    /** [HIBERNATE_SESSION_USER] (which cannot be converted) and [PLAIN] only. */
    val sessionUserJar: Path = root / "session-user-cordapp.jar"

    init {
        compile(stubSources, stubClassesDir, classpath = emptyList())
        val quasarJar = co.paralleluniverse.fibers.Fiber::class.java.protectionDomain.codeSource.location.toURI().let(Path::of)
        compile(cordappSources, classesDir, classpath = listOf(stubClassesDir, quasarJar))
        val jakartaApiJar = jakarta.persistence.Entity::class.java.protectionDomain.codeSource.location.toURI().let(Path::of)
        compile(dualSources, classesDir, classpath = listOf(stubClassesDir, jakartaApiJar))
        val intruderEntry = entryOf(INTRUDER)
        val unconvertible = setOf(HIBERNATE_SESSION_USER, HIBERNATE_TYPE_CLASS_USER, UNKNOWN_TYPE_ENTITY, TYPE_DEF_ENTITY).map(::entryOf)
        writeJar(jar, classesDir)
        writeJar(sealedJar, classesDir, sealed = true) { it != intruderEntry }
        writeJar(intruderJar, classesDir) { it == intruderEntry }
        writeJar(fatJar, classesDir, stubClassesDir) { !it.startsWith("org/hibernate/") }
        writeJar(cleanJar, classesDir) { it !in unconvertible }
        writeJar(sessionUserJar, classesDir) { it == entryOf(HIBERNATE_SESSION_USER) || it == entryOf(PLAIN) }
    }

    private fun entryOf(className: String) = className.replace('.', '/') + ".class"

    fun classBytes(className: String): ByteArray = (classesDir / (className.replace('.', '/') + ".class")).readBytes()

    private fun compile(sources: Map<String, String>, outputDir: Path, classpath: List<Path>) {
        val files = sources.map { (path, source) ->
            val file = sourceDir / path
            file.parent.createDirectories()
            file.writeText(source)
            file.toString()
        }
        val compiler = ToolProvider.getSystemJavaCompiler() ?: error("These tests need a JDK")
        val options = listOf("-d", outputDir.toString(), "-cp", classpath.joinToString(java.io.File.pathSeparator), "-nowarn", "-proc:none")
        check(compiler.run(null, null, null, *(options + files).toTypedArray()) == 0) { "Compilation of the fixture failed" }
    }

    private fun writeJar(jar: Path, vararg roots: Path, sealed: Boolean = false, include: (String) -> Boolean = { true }) {
        val manifest = Manifest().apply {
            mainAttributes.putValue("Manifest-Version", "1.0")
            if (sealed) mainAttributes.putValue("Sealed", "true")
        }
        JarOutputStream(jar.outputStream(), manifest).use { out ->
            for (root in roots) {
                Files.walk(root).filter { Files.isRegularFile(it) }.forEach { file ->
                    val entryName = root.relativize(file).joinToString("/")
                    if (!include(entryName)) return@forEach
                    out.putNextEntry(JarEntry(entryName))
                    out.write(file.readBytes())
                    out.closeEntry()
                }
            }
        }
    }
}

/**
 * A Kotlin class typed on the persistence API. The tests rename its references to `javax.persistence` (metadata included, as the Kotlin
 * compiler would have written it for Corda 4.14) to get a Kotlin class built for `javax.persistence`, which the fixture cannot compile.
 */
@Suppress("unused")
class KotlinPersistenceUser(val query: jakarta.persistence.Query?) {
    fun persist(em: jakarta.persistence.EntityManager, entity: Any): String {
        em.persist(entity)
        return "persisted $entity"
    }
}
