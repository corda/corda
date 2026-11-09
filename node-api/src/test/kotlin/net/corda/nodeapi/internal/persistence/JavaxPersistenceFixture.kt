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
        const val HIBERNATE_FIELD_USER = "legacy.HibernateFieldUser"
        const val ANNOTATED_ENTITY = "legacy.AnnotatedEntity"
        const val PARAM_DEF_ENTITY = "legacy.ParamDefEntity"
        const val DYNAMIC_UPDATE_FALSE_ENTITY = "legacy.DynamicUpdateFalseEntity"
        const val DDL_TABLE_ENTITY = "legacy.DdlTableEntity"
        const val CUSTOM_SQL_TABLE_ENTITY = "legacy.CustomSqlTableEntity"
        const val JOIN_COLUMN_OR_FORMULA_ENTITY = "legacy.JoinColumnOrFormulaEntity"
        const val IDIOMS_ENTITY = "legacy.IdiomsEntity"
        const val MISMATCHED_TYPE_ENTITY = "legacy.MismatchedTypeEntity"
        const val COLLECTION_TYPE_ENTITY = "legacy.CollectionTypeEntity"
        const val CHAR_ENTITY = "legacy.CharEntity"
        const val CASCADE_PARENT = "legacy.CascadeParent"
        const val CASCADE_CHILD = "legacy.CascadeChild"
        const val INTRUDER = "legacy.Intruder"
        const val JAVAX_ENTITY_ANNOTATION = "javax.persistence.Entity"
        const val DUAL_ENTITY = "dual.DualEntity"

        /** The classes of the fixture that the node refuses to convert, each for a different reason. */
        val unconvertibleClasses: Set<String> = setOf(
                HIBERNATE_SESSION_USER, HIBERNATE_TYPE_CLASS_USER, HIBERNATE_FIELD_USER, UNKNOWN_TYPE_ENTITY, TYPE_DEF_ENTITY,
                PARAM_DEF_ENTITY, CUSTOM_SQL_TABLE_ENTITY, MISMATCHED_TYPE_ENTITY, COLLECTION_TYPE_ENTITY
        )

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
                "javax/persistence/JoinColumn.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface JoinColumn { String name() default \"\"; String referencedColumnName() default \"\"; }",
                "javax/persistence/FetchType.java" to "package javax.persistence; public enum FetchType { LAZY, EAGER }",
                "javax/persistence/OneToMany.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface OneToMany { String mappedBy() default \"\"; FetchType fetch() default FetchType.LAZY; }",
                "javax/persistence/ManyToOne.java" to "package javax.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface ManyToOne { FetchType fetch() default FetchType.EAGER; }",
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
                "org/hibernate/annotations/TypeDef.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.PACKAGE}) public @interface TypeDef { String name(); Class typeClass(); }",
                // The Hibernate 5 shapes of annotations Hibernate 7 renamed (Where, OrderBy), changed (DynamicUpdate lost its member, ParamDef.type
                // became a Class), dropped (Index) or kept (Immutable).
                "org/hibernate/annotations/Where.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD}) public @interface Where { String clause(); }",
                "org/hibernate/annotations/OrderBy.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface OrderBy { String clause(); }",
                "org/hibernate/annotations/DynamicUpdate.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface DynamicUpdate { boolean value() default true; }",
                "org/hibernate/annotations/Index.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface Index { String name(); String[] columnNames() default {}; }",
                "org/hibernate/annotations/Immutable.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD}) public @interface Immutable {}",
                "org/hibernate/annotations/FilterDef.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.PACKAGE}) public @interface FilterDef { String name(); String defaultCondition() default \"\"; ParamDef[] parameters() default {}; }",
                "org/hibernate/annotations/ParamDef.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({}) public @interface ParamDef { String name(); String type(); }",
                "org/hibernate/annotations/DynamicInsert.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface DynamicInsert { boolean value() default true; }",
                // The Hibernate 5 shape of @Table (and @Tables), which Hibernate 7 no longer has: DDL members and members that changed how the table was read and written.
                "org/hibernate/annotations/ForeignKey.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({}) public @interface ForeignKey { String name(); String inverseName() default \"\"; }",
                "org/hibernate/annotations/ResultCheckStyle.java" to "package org.hibernate.annotations; public enum ResultCheckStyle { NONE, COUNT, PARAM }",
                "org/hibernate/annotations/FetchMode.java" to "package org.hibernate.annotations; public enum FetchMode { SELECT, JOIN, SUBSELECT }",
                "org/hibernate/annotations/SQLInsert.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD}) public @interface SQLInsert { String sql(); boolean callable() default false; ResultCheckStyle check() default ResultCheckStyle.NONE; }",
                "org/hibernate/annotations/SQLUpdate.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD}) public @interface SQLUpdate { String sql(); boolean callable() default false; ResultCheckStyle check() default ResultCheckStyle.NONE; }",
                "org/hibernate/annotations/SQLDelete.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD}) public @interface SQLDelete { String sql(); boolean callable() default false; ResultCheckStyle check() default ResultCheckStyle.NONE; }",
                "org/hibernate/annotations/Table.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface Table { String appliesTo(); Index[] indexes() default {}; String comment() default \"\"; ForeignKey foreignKey() default @ForeignKey(name = \"\"); FetchMode fetch() default FetchMode.JOIN; boolean inverse() default false; boolean optional() default true; SQLInsert sqlInsert() default @SQLInsert(sql = \"\"); SQLUpdate sqlUpdate() default @SQLUpdate(sql = \"\"); SQLDelete sqlDelete() default @SQLDelete(sql = \"\"); }",
                "org/hibernate/annotations/Tables.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface Tables { Table[] value(); }",
                // The Hibernate 5 shapes of annotations whose members Hibernate 7 reads differently but which mean the same, and of ones typed on a JPA annotation.
                "org/hibernate/annotations/CascadeType.java" to "package org.hibernate.annotations; public enum CascadeType { ALL, PERSIST, MERGE, REMOVE, REFRESH, DELETE, SAVE_UPDATE, REPLICATE, DELETE_ORPHAN, LOCK, EVICT, DETACH }",
                "org/hibernate/annotations/Cascade.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface Cascade { CascadeType[] value(); }",
                "org/hibernate/annotations/GenerationTime.java" to "package org.hibernate.annotations; public enum GenerationTime { NEVER, INSERT, ALWAYS }",
                "org/hibernate/annotations/Generated.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface Generated { GenerationTime value(); }",
                "org/hibernate/annotations/CacheConcurrencyStrategy.java" to "package org.hibernate.annotations; public enum CacheConcurrencyStrategy { NONE, READ_ONLY, NONSTRICT_READ_WRITE, READ_WRITE, TRANSACTIONAL }",
                "org/hibernate/annotations/Cache.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD}) public @interface Cache { CacheConcurrencyStrategy usage(); String region() default \"\"; String include() default \"all\"; }",
                "org/hibernate/annotations/CacheModeType.java" to "package org.hibernate.annotations; public enum CacheModeType { GET, IGNORE, NORMAL, PUT, REFRESH }",
                "org/hibernate/annotations/NamedQuery.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.PACKAGE}) public @interface NamedQuery { String name(); String query(); CacheModeType cacheMode() default CacheModeType.NORMAL; }",
                "org/hibernate/annotations/JoinFormula.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface JoinFormula { String value(); String referencedColumnName() default \"\"; }",
                "org/hibernate/annotations/JoinColumnOrFormula.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface JoinColumnOrFormula { JoinFormula formula() default @JoinFormula(value = \"\"); javax.persistence.JoinColumn column() default @javax.persistence.JoinColumn(); }",
                "org/hibernate/annotations/JoinColumnsOrFormulas.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface JoinColumnsOrFormulas { JoinColumnOrFormula[] value(); }",
                "org/hibernate/annotations/Parameter.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({}) public @interface Parameter { String name(); String value(); }",
                "org/hibernate/annotations/CollectionType.java" to "package org.hibernate.annotations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD}) public @interface CollectionType { String type(); Parameter[] parameters() default {}; }",
                // A static field whose type differs from Hibernate 7's (there it is a String constant): the JVM resolves fields by name and type.
                "org/hibernate/cfg/Environment.java" to "package org.hibernate.cfg; public class Environment { public static Object DIALECT = \"hibernate.dialect\"; }"
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
                        @Column(name = "fqcn_uuid") @org.hibernate.annotations.Type(type = "org.hibernate.type.UUIDCharType") public UUID fqcnUuid;
                        @Column(name = "yes_no") @org.hibernate.annotations.Type(type = "yes_no") public Boolean yesNo;
                        @Column(name = "true_false") @org.hibernate.annotations.Type(type = "true_false") public Boolean trueFalse;
                        @Column(name = "numeric") @org.hibernate.annotations.Type(type = "numeric_boolean") public Boolean numeric;
                        public LegacyEntity() {}
                    }
                """.trimIndent(),
                "legacy/AnnotatedEntity.java" to """
                    package legacy;
                    import javax.persistence.*;
                    import org.hibernate.annotations.DynamicUpdate;
                    import org.hibernate.annotations.Immutable;
                    import org.hibernate.annotations.Index;
                    import org.hibernate.annotations.Where;
                    /** Hibernate 5 annotations Hibernate 7 renamed (@Where, @OrderBy), kept without its member (@DynamicUpdate), dropped (@Index) or kept (@Immutable). */
                    @Entity
                    @Where(clause = "deleted = false")
                    @DynamicUpdate(true)
                    @Immutable
                    public class AnnotatedEntity {
                        @Id public String id;
                        @Index(name = "idx_name") @Column(name = "name") public String name;
                        @org.hibernate.annotations.OrderBy(clause = "name asc") public java.util.List<String> names;
                    }
                """.trimIndent(),
                "legacy/ParamDefEntity.java" to """
                    package legacy;
                    import org.hibernate.annotations.FilterDef;
                    import org.hibernate.annotations.ParamDef;
                    /** A nested Hibernate annotation whose member Hibernate 7 declares as a Class (ParamDef.type), naming a Hibernate 5 type the node knows no class for. */
                    @javax.persistence.Entity
                    @FilterDef(name = "live", defaultCondition = "deleted = false", parameters = @ParamDef(name = "flag", type = "my-type"))
                    public class ParamDefEntity {
                        @javax.persistence.Id public String id;
                    }
                """.trimIndent(),
                "legacy/CollectionTypeEntity.java" to """
                    package legacy;
                    /** A Hibernate annotation member Hibernate 7 declares with another type (CollectionType.type: String in 5, Class in 7). */
                    @javax.persistence.Entity
                    public class CollectionTypeEntity {
                        @javax.persistence.Id public String id;
                        @org.hibernate.annotations.CollectionType(type = "legacy.MyListType") public java.util.List<String> items;
                    }
                """.trimIndent(),
                "legacy/DynamicUpdateFalseEntity.java" to """
                    package legacy;
                    /** Hibernate 5's way of saying what the absence of the annotation says: dropped. */
                    @javax.persistence.Entity
                    @org.hibernate.annotations.DynamicUpdate(false)
                    public class DynamicUpdateFalseEntity {
                        @javax.persistence.Id public String id;
                    }
                """.trimIndent(),
                "legacy/DdlTableEntity.java" to """
                    package legacy;
                    import org.hibernate.annotations.Index;
                    /** Hibernate 5's @Table with members that only affected generated DDL, which Hibernate 7 no longer has: dropped. */
                    @javax.persistence.Entity
                    @org.hibernate.annotations.Table(appliesTo = "ddl_table_entity", comment = "legacy", indexes = @Index(name = "ddl_idx", columnNames = "id"))
                    public class DdlTableEntity {
                        @javax.persistence.Id public String id;
                    }
                """.trimIndent(),
                "legacy/CustomSqlTableEntity.java" to """
                    package legacy;
                    import org.hibernate.annotations.SQLInsert;
                    import org.hibernate.annotations.Table;
                    import org.hibernate.annotations.Tables;
                    /** Hibernate 5's @Table with members that changed how Hibernate wrote the table: refused rather than dropped. */
                    @javax.persistence.Entity
                    @Tables(@Table(appliesTo = "custom_sql_extra", optional = false, sqlInsert = @SQLInsert(sql = "insert into custom_sql_extra (id) values (?)")))
                    public class CustomSqlTableEntity {
                        @javax.persistence.Id public String id;
                    }
                """.trimIndent(),
                "legacy/JoinColumnOrFormulaEntity.java" to """
                    package legacy;
                    import javax.persistence.JoinColumn;
                    import org.hibernate.annotations.JoinColumnOrFormula;
                    import org.hibernate.annotations.JoinColumnsOrFormulas;
                    /** A Hibernate annotation whose member is typed on a JPA annotation, which the rename turns into the jakarta one Hibernate 7 declares. */
                    @javax.persistence.Entity
                    public class JoinColumnOrFormulaEntity {
                        @javax.persistence.Id public String id;
                        @JoinColumnsOrFormulas({@JoinColumnOrFormula(column = @JoinColumn(name = "ref_id", referencedColumnName = "id"))}) public Object ref;
                    }
                """.trimIndent(),
                "legacy/IdiomsEntity.java" to """
                    package legacy;
                    import java.util.List;
                    import java.util.UUID;
                    import javax.persistence.Column;
                    import org.hibernate.annotations.Cache;
                    import org.hibernate.annotations.CacheConcurrencyStrategy;
                    import org.hibernate.annotations.CacheModeType;
                    import org.hibernate.annotations.Cascade;
                    import org.hibernate.annotations.CascadeType;
                    import org.hibernate.annotations.DynamicInsert;
                    import org.hibernate.annotations.FilterDef;
                    import org.hibernate.annotations.Generated;
                    import org.hibernate.annotations.GenerationTime;
                    import org.hibernate.annotations.NamedQuery;
                    import org.hibernate.annotations.ParamDef;
                    import org.hibernate.annotations.Type;
                    /** Common Hibernate 5 idioms that Hibernate 7 reads differently but which mean the same: each is rewritten rather than refused. */
                    @javax.persistence.Entity
                    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE, include = "non-lazy")
                    @DynamicInsert(false)
                    @FilterDef(name = "live", parameters = @ParamDef(name = "flag", type = "boolean"))
                    @NamedQuery(name = "IdiomsEntity.all", query = "from IdiomsEntity", cacheMode = CacheModeType.IGNORE)
                    public class IdiomsEntity {
                        @javax.persistence.Id public String id;
                        @Generated(GenerationTime.INSERT) public java.sql.Timestamp created;
                        @Generated(GenerationTime.ALWAYS) public java.sql.Timestamp updated;
                        @Generated(GenerationTime.NEVER) public String never;
                        @Cascade({CascadeType.SAVE_UPDATE, CascadeType.DELETE, CascadeType.PERSIST}) public List<String> children;
                        @Cascade(CascadeType.EVICT) public Object other;
                        @Column(name = "count") @Type(type = "long") public Long count;
                        @Column(name = "label") @Type(type = "org.hibernate.type.StringType") public String label;
                        @Column(name = "pg") @Type(type = "pg-uuid") public UUID pg;
                    }
                """.trimIndent(),
                "legacy/CharEntity.java" to """
                    package legacy;
                    import javax.persistence.Column;
                    import org.hibernate.annotations.Type;
                    /**
                     * Character columns as a 4.14 CorDapp mapped them: an explicit character type and a yes_no boolean, boxed and primitive,
                     * and an explicit string type.
                     */
                    @javax.persistence.Entity
                    @javax.persistence.Table(name = "char_entity")
                    public class CharEntity {
                        @javax.persistence.Id @Column(name = "id") public String id;
                        @Column(name = "ch") @Type(type = "character") public Character ch;
                        @Column(name = "chp") @Type(type = "character") public char chp;
                        @Column(name = "yn") @Type(type = "yes_no") public Boolean yn;
                        @Column(name = "ynp") @Type(type = "yes_no") public boolean ynp;
                        @Column(name = "st") @Type(type = "string") public String st;
                        public CharEntity() {}
                    }
                """.trimIndent(),
                "legacy/CascadeParent.java" to """
                    package legacy;
                    import java.util.ArrayList;
                    import java.util.List;
                    import javax.persistence.Column;
                    import javax.persistence.OneToMany;
                    import org.hibernate.annotations.Cascade;
                    import org.hibernate.annotations.CascadeType;
                    /** A parent whose children carry Hibernate 5's save-update cascade. */
                    @javax.persistence.Entity
                    @javax.persistence.Table(name = "cascade_parent")
                    public class CascadeParent {
                        @javax.persistence.Id @Column(name = "id") public String id;
                        @Column(name = "name") public String name;
                        @OneToMany(mappedBy = "parent") @Cascade(CascadeType.SAVE_UPDATE) public List<CascadeChild> children = new ArrayList<>();
                        public CascadeParent() {}
                    }
                """.trimIndent(),
                "legacy/CascadeChild.java" to """
                    package legacy;
                    import javax.persistence.Column;
                    import javax.persistence.JoinColumn;
                    import javax.persistence.ManyToOne;
                    @javax.persistence.Entity
                    @javax.persistence.Table(name = "cascade_child")
                    public class CascadeChild {
                        @javax.persistence.Id @Column(name = "id") public String id;
                        @Column(name = "name") public String name;
                        @ManyToOne @JoinColumn(name = "parent_id") public CascadeParent parent;
                        public CascadeChild() {}
                    }
                """.trimIndent(),
                "legacy/MismatchedTypeEntity.java" to """
                    package legacy;
                    /** A Hibernate 5 basic type named on a member of another type: a choice the node cannot express in Hibernate 7. */
                    @javax.persistence.Entity
                    public class MismatchedTypeEntity {
                        @javax.persistence.Id public String id;
                        @org.hibernate.annotations.Type(type = "long") public String label;
                    }
                """.trimIndent(),
                "legacy/HibernateFieldUser.java" to """
                    package legacy;
                    /** Reads a Hibernate field whose type differs in Hibernate 7. */
                    public class HibernateFieldUser {
                        public static Object dialect(javax.persistence.EntityManager em) {
                            return org.hibernate.cfg.Environment.DIALECT;
                        }
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

    /** [HIBERNATE_SESSION_USER] together with the Hibernate 5 stub of `org.hibernate.Session` it was built against, as a fat CorDapp might bundle it. */
    val bundledSessionJar: Path = root / "bundled-session-cordapp.jar"

    init {
        compile(stubSources, stubClassesDir, classpath = emptyList())
        val quasarJar = co.paralleluniverse.fibers.Fiber::class.java.protectionDomain.codeSource.location.toURI().let(Path::of)
        compile(cordappSources, classesDir, classpath = listOf(stubClassesDir, quasarJar))
        val jakartaApiJar = jakarta.persistence.Entity::class.java.protectionDomain.codeSource.location.toURI().let(Path::of)
        compile(dualSources, classesDir, classpath = listOf(stubClassesDir, jakartaApiJar))
        val intruderEntry = entryOf(INTRUDER)
        val unconvertible = unconvertibleClasses.map(::entryOf)
        writeJar(jar, classesDir)
        writeJar(sealedJar, classesDir, sealed = true) { it != intruderEntry }
        writeJar(intruderJar, classesDir) { it == intruderEntry }
        writeJar(fatJar, classesDir, stubClassesDir) { !it.startsWith("org/hibernate/") }
        writeJar(cleanJar, classesDir) { it !in unconvertible }
        writeJar(sessionUserJar, classesDir) { it == entryOf(HIBERNATE_SESSION_USER) || it == entryOf(PLAIN) }
        writeJar(bundledSessionJar, classesDir, stubClassesDir) { it == entryOf(HIBERNATE_SESSION_USER) || it == "org/hibernate/Session.class" }
    }

    private fun entryOf(className: String) = className.replace('.', '/') + ".class"

    fun classBytes(className: String): ByteArray = (classesDir / (className.replace('.', '/') + ".class")).readBytes()

    @Suppress("SpreadOperator") // The compiler's API takes its arguments as varargs.
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
            roots.forEach { root -> addClasses(out, root, include) }
        }
    }

    private fun addClasses(out: JarOutputStream, root: Path, include: (String) -> Boolean) {
        Files.walk(root).use { files ->
            files.filter { Files.isRegularFile(it) }.forEach { file ->
                val entryName = root.relativize(file).joinToString("/")
                if (include(entryName)) {
                    out.putNextEntry(JarEntry(entryName))
                    out.write(file.readBytes())
                    out.closeEntry()
                }
            }
        }
    }
}

/**
 * Defines the fixture's classes from their converted bytes; everything else comes from [parent], the test classpath, which has
 * `jakarta.persistence` and Hibernate 7, against which the Hibernate references of the converted classes are resolved.
 */
class ConvertingFixtureLoader(private val fixture: JavaxPersistenceFixture, parent: ClassLoader) : ClassLoader(parent) {
    override fun findClass(name: String): Class<*> {
        val bytes = try {
            fixture.classBytes(name)
        } catch (e: java.nio.file.NoSuchFileException) {
            throw ClassNotFoundException(name, e)
        }
        val converted = if (JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(bytes)) JavaxPersistenceTransformer.transform(bytes, name, parent) else bytes
        return defineClass(name, converted, 0, converted.size)
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
