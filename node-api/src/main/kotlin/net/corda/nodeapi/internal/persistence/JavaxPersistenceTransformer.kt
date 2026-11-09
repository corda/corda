package net.corda.nodeapi.internal.persistence

import net.corda.core.CordaRuntimeException
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.persistence.factory.CordaSqlTypes
import org.hibernate.type.SqlTypes
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.lang.reflect.Method
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Types
import java.time.Instant

/**
 * Converts a class that was built against Java Persistence (`javax.persistence`, i.e. a CorDapp built for Corda 4.14 or earlier) to
 * Jakarta Persistence (`jakarta.persistence`), which is what Hibernate 7 reads, by renaming the package in the bytes of the class as
 * it is loaded. This is what the application servers did for Java EE applications when they moved to Jakarta EE, and it lets such a
 * CorDapp run on this node without being rebuilt: the rename covers the annotations of the schema classes (nested annotations and
 * enum values included), `AttributeConverter` implementations, code that uses the `EntityManager`, `Query` and criteria types in
 * `ServiceHub.withEntityManager` blocks (lambdas included) and `catch` clauses of the JPA exception types.
 *
 * The rename also covers the strings of a Kotlin class's `@kotlin.Metadata` (its `d2` string table), which name the types of its
 * members in the same form, so that Kotlin reflection on a converted class sees the renamed types too.
 *
 * Only a class that refers to `javax.persistence` and not to `jakarta.persistence` is converted (see [isBuiltForJavaxPersistenceOnly]).
 * A class that carries both sets of annotations, i.e. a CorDapp built to run on Corda 4.14 and 4.15 alike, must be left alone, as the
 * rename would duplicate its annotations.
 *
 * Hibernate's own annotations are not renamed, and several of them changed between Hibernate 5 and 7, so a converted class's Hibernate
 * annotations are converted or checked one by one:
 * - `@Type(type = "...")`, whose members changed in Hibernate 6, is replaced by the `@JdbcTypeCode` Corda's own schemas use for the
 *   type names Corda registered or documented and the standard Hibernate 5 names with a fixed JDBC type ([substitutedHibernate5Types]),
 *   or by a `@Convert` to a converter that stores the same format for the boolean types ([convertedHibernate5Types]); one that names
 *   the default mapping of the member's own type (`@Type(type = "long")` on a `Long`) is dropped, except that `string` and `character`
 *   keep an explicit `@JdbcTypeCode`, as the explicit type opted the column out of nationalised character data on SQL Server
 *   ([hibernate5BasicTypeClasses]); any other name fails the conversion, naming the class and the member.
 * - `@Where`, `@WhereJoinTable` and `@OrderBy`, which Hibernate 7 renamed, become `@SQLRestriction`, `@SQLJoinTableRestriction` and
 *   `@SQLOrder`; `@DynamicInsert(true)` and `@DynamicUpdate(true)` lose the member Hibernate 7 no longer has, and `(false)`, which only
 *   said what the absence of the annotation says, is dropped; `@Generated(GenerationTime)` becomes `@Generated(event = ...)`.
 * - Members Hibernate 7 reads differently but which mean the same are rewritten: the `CascadeType` constants Hibernate 7 removed
 *   (`DELETE`, `EVICT`, `SAVE_UPDATE`), `@ParamDef(type = "name")` to the Java class of the named basic type, `@Cache(include = ...)` to
 *   `includeLazy`, and `CacheModeType` to `CacheMode`.
 * - `@Index` and `@ForeignKey`, which only affected the DDL Hibernate generated (a node's tables come from Liquibase), are dropped with a
 *   warning, and so is a `@Table` (or each `@Table` of a `@Tables`) that only sets such members; one that sets a member that changed how
 *   Hibernate read or wrote the table (custom SQL, `fetch`, `inverse`, `optional`) fails the conversion.
 * - Every other Hibernate annotation, `@TypeDef` included, is resolved against the node's Hibernate 7: a missing annotation, a member
 *   Hibernate 7 does not have or declares with another type, or a member Hibernate 7 requires that Hibernate 5 did not, fails the
 *   conversion. The JVM would otherwise drop the annotation or the member silently, and the mapping would change without a trace.
 *
 * Hibernate classes other than annotations (the `Session` API, the type classes) are not converted either, and many of them changed
 * between Hibernate 5 and 7. Given the class loader of the node's Hibernate, [transform] resolves every Hibernate class and member the
 * converted class refers to (supertypes, field and method types, calls, field accesses, casts, `catch` clauses, method handles) and, if
 * any does not exist in Hibernate 7, fails naming them, as such code would fail with a `NoSuchMethodError` or `NoClassDefFoundError`
 * when it runs; with [Options.refuseMissingReferences] off (the [MISSING_HIBERNATE_REFERENCES_PROPERTY] says `warn`), it logs them and
 * converts the class anyway, so that the JVM fails lazily as it would have without the check. References to classes and members that
 * still exist (the exception types, `Session.flush`) pass, as the code runs against them unchanged.
 */
object JavaxPersistenceTransformer {
    private val logger = loggerFor<JavaxPersistenceTransformer>()

    /**
     * System property deciding what happens to a converted class that refers to Hibernate classes or members missing from Hibernate 7:
     * `refuse` (the default) fails the conversion, so the node does not start; [MISSING_HIBERNATE_REFERENCES_WARN] logs the references
     * and converts the class anyway, so that the code fails only if it runs. Annotations that cannot be converted are refused in both modes.
     * The node reads it once, when it creates its CorDapp class loader (see [refuseMissingReferencesByDefault]).
     */
    const val MISSING_HIBERNATE_REFERENCES_PROPERTY = "net.corda.cordapp.missingHibernateReferences"
    const val MISSING_HIBERNATE_REFERENCES_WARN = "warn"

    const val REBUILD_ADVICE = "The CorDapp that contains the class needs to be rebuilt for Corda 4.15 - see the " +
            "\"Upgrading to Hibernate 7\" documentation."

    private const val JAVAX_PERSISTENCE = "javax/persistence/"
    private const val JAKARTA_PERSISTENCE = "jakarta/persistence/"
    private const val HIBERNATE = "org/hibernate/"
    private const val HIBERNATE_ANNOTATION_PREFIX = "Lorg/hibernate/annotations/"
    private const val HIBERNATE_TYPE = "Lorg/hibernate/annotations/Type;"
    private const val HIBERNATE_TYPE_DEF = "Lorg/hibernate/annotations/TypeDef;"
    private const val HIBERNATE_TYPE_DEFS = "Lorg/hibernate/annotations/TypeDefs;"
    private const val HIBERNATE_TABLE = "Lorg/hibernate/annotations/Table;"
    private const val HIBERNATE_TABLES = "Lorg/hibernate/annotations/Tables;"
    private const val HIBERNATE_GENERATED = "Lorg/hibernate/annotations/Generated;"
    private const val HIBERNATE_GENERATION_TIME = "Lorg/hibernate/annotations/GenerationTime;"
    private const val HIBERNATE_EVENT_TYPE = "Lorg/hibernate/generator/EventType;"
    private const val HIBERNATE_CASCADE = "Lorg/hibernate/annotations/Cascade;"
    private const val HIBERNATE_CASCADE_TYPE = "Lorg/hibernate/annotations/CascadeType;"
    private const val HIBERNATE_PARAM_DEF = "Lorg/hibernate/annotations/ParamDef;"
    private const val HIBERNATE_CACHE = "Lorg/hibernate/annotations/Cache;"
    private const val HIBERNATE_JDBC_TYPE_CODE = "Lorg/hibernate/annotations/JdbcTypeCode;"
    private const val JAKARTA_CONVERT = "Ljakarta/persistence/Convert;"
    private const val KOTLIN_METADATA = "Lkotlin/Metadata;"
    private const val KOTLIN_METADATA_STRINGS = "d2"

    /** The rename, for the places that compare a descriptor of the original class with one of Hibernate 7's. */
    private val javaxToJakarta = PackageRemapper(JAVAX_PERSISTENCE, JAKARTA_PERSISTENCE)

    /**
     * The Hibernate 5 type names the node substitutes by `@JdbcTypeCode`, with the JDBC type code of their Hibernate 7 mapping. The names
     * Corda 4.14 registered or documented (`BaseSessionFactoryFactory` registered `corda-wrapper-binary`, `corda-blob` and its own
     * `materialized_blob`, and its schemas used `uuid-char`), and the standard Hibernate 5 names whose JDBC type is fixed, each by its
     * short name and by the name of its Hibernate 5 type class, which `@Type` also accepted. Each mapping stores the same database
     * format as the Hibernate 5 type did under Corda's Hibernate 7 compatibility settings.
     */
    val substitutedHibernate5Types: Map<String, Int> = mapOf(
            "uuid-char" to Types.VARCHAR,
            "org.hibernate.type.UUIDCharType" to Types.VARCHAR,
            "corda-wrapper-binary" to Types.VARBINARY,
            // A BLOB, except bytea on PostgreSQL: the node's own blob columns use the same code (CordaTypeContributor).
            "corda-blob" to CordaSqlTypes.CORDA_BLOB,
            "materialized_blob" to Types.BLOB,
            "org.hibernate.type.MaterializedBlobType" to Types.BLOB,
            "materialized_clob" to Types.CLOB,
            "org.hibernate.type.MaterializedClobType" to Types.CLOB,
            // The default Hibernate 7 mapping of a UUID under hibernate.type.preferred_uuid_jdbc_type=BINARY.
            "uuid-binary" to Types.BINARY,
            "org.hibernate.type.UUIDBinaryType" to Types.BINARY,
            // PostgreSQL's native uuid column, which Hibernate 7 addresses by its own type code.
            "pg-uuid" to SqlTypes.UUID,
            "org.hibernate.type.PostgresUUIDType" to SqlTypes.UUID,
            "binary" to Types.VARBINARY,
            "org.hibernate.type.BinaryType" to Types.VARBINARY,
            "text" to Types.LONGVARCHAR,
            "org.hibernate.type.TextType" to Types.LONGVARCHAR
    )

    /**
     * The Hibernate 5 boolean type names the node substitutes by a JPA `@Convert` to an `AttributeConverter` that stores the same format:
     * Corda's [YesNoBooleanConverter] and [TrueFalseBooleanConverter] for 'Y'/'N' and 'T'/'F' (see there for why not Hibernate 7's own),
     * Hibernate 7's `NumericBooleanConverter` for 1/0.
     */
    val convertedHibernate5Types: Map<String, String> = mapOf(
            "yes_no" to YesNoBooleanConverter::class.java.name,
            "org.hibernate.type.YesNoType" to YesNoBooleanConverter::class.java.name,
            "true_false" to TrueFalseBooleanConverter::class.java.name,
            "org.hibernate.type.TrueFalseType" to TrueFalseBooleanConverter::class.java.name,
            "numeric_boolean" to "org.hibernate.type.NumericBooleanConverter",
            "org.hibernate.type.NumericBooleanType" to "org.hibernate.type.NumericBooleanConverter"
    )

    /** The JDBC type code emitted next to the `@Convert` of a converter the node substitutes, where the column type must be pinned. */
    private val converterJdbcTypeCodes: Map<String, Int> = mapOf(
            YesNoBooleanConverter::class.java.name to Types.CHAR,
            TrueFalseBooleanConverter::class.java.name to Types.CHAR,
            SingleCharacterConverter::class.java.name to Types.CHAR
    )

    /**
     * A Hibernate 5 basic type that was the default mapping of its Java type: by name and by type class name, with the member types it
     * was the default for. A `@Type` naming it on such a member is dropped, unless the type pins a [jdbcTypeCode] or a [converter]: an
     * explicit character type opted the column out of nationalised character data on SQL Server (`varchar` where the default gives
     * `nvarchar`), so `string` and `character` keep an explicit mapping, and `character` reads a padded column as Hibernate 5 did.
     */
    private class Hibernate5BasicType(
            val javaClass: Class<*>,
            vararg val names: String,
            alsoDefaultFor: Set<Class<*>> = emptySet(),
            val jdbcTypeCode: Int? = null,
            val converter: String? = null
    ) {
        val defaultFor: Set<String> = (setOf(javaClass) + alsoDefaultFor).map { Type.getDescriptor(it) }.toSet()
    }

    private val hibernate5BasicTypes: List<Hibernate5BasicType> = listOf(
            Hibernate5BasicType(String::class.java, "string", "org.hibernate.type.StringType", jdbcTypeCode = Types.VARCHAR),
            Hibernate5BasicType(java.lang.Long::class.java, "long", "org.hibernate.type.LongType", alsoDefaultFor = setOf(java.lang.Long.TYPE)),
            Hibernate5BasicType(Integer::class.java, "integer", "int", "org.hibernate.type.IntegerType", alsoDefaultFor = setOf(Integer.TYPE)),
            Hibernate5BasicType(java.lang.Short::class.java, "short", "org.hibernate.type.ShortType", alsoDefaultFor = setOf(java.lang.Short.TYPE)),
            Hibernate5BasicType(java.lang.Byte::class.java, "byte", "org.hibernate.type.ByteType", alsoDefaultFor = setOf(java.lang.Byte.TYPE)),
            Hibernate5BasicType(java.lang.Boolean::class.java, "boolean", "org.hibernate.type.BooleanType", alsoDefaultFor = setOf(java.lang.Boolean.TYPE)),
            Hibernate5BasicType(java.lang.Double::class.java, "double", "org.hibernate.type.DoubleType", alsoDefaultFor = setOf(java.lang.Double.TYPE)),
            Hibernate5BasicType(java.lang.Float::class.java, "float", "org.hibernate.type.FloatType", alsoDefaultFor = setOf(java.lang.Float.TYPE)),
            Hibernate5BasicType(Character::class.java, "character", "org.hibernate.type.CharacterType", alsoDefaultFor = setOf(Character.TYPE),
                    converter = SingleCharacterConverter::class.java.name),
            Hibernate5BasicType(BigDecimal::class.java, "big_decimal", "org.hibernate.type.BigDecimalType"),
            Hibernate5BasicType(BigInteger::class.java, "big_integer", "org.hibernate.type.BigIntegerType"),
            Hibernate5BasicType(java.util.Date::class.java, "timestamp", "org.hibernate.type.TimestampType", alsoDefaultFor = setOf(java.sql.Timestamp::class.java)),
            Hibernate5BasicType(java.sql.Date::class.java, "date", "org.hibernate.type.DateType"),
            Hibernate5BasicType(java.sql.Time::class.java, "time", "org.hibernate.type.TimeType"),
            Hibernate5BasicType(Instant::class.java, "instant", "org.hibernate.type.InstantType")
    )

    /**
     * The Java class of each Hibernate 5 basic type name (short name, type class name or Java class name) that was the default mapping of
     * that Java class, so means the same as no `@Type` on a member of that class, and the same as the class in a `@ParamDef(type = ...)`,
     * which Hibernate 7 declares as a `Class`.
     */
    val hibernate5BasicTypeClasses: Map<String, Class<*>> = hibernate5BasicTypes.flatMap { type ->
        (type.names.toList() + type.javaClass.name).map { it to type.javaClass }
    }.toMap()

    /** The basic types by each of their Hibernate 5 names. */
    private val hibernate5BasicTypesByName: Map<String, Hibernate5BasicType> = hibernate5BasicTypes.flatMap { type -> type.names.map { it to type } }.toMap()

    private class AnnotationSubstitute(val descriptor: String, val members: Map<String, String>)

    /** Hibernate 5 annotations Hibernate 7 renamed, with the renamed members. */
    private val substitutedAnnotations: Map<String, AnnotationSubstitute> = mapOf(
            "Lorg/hibernate/annotations/Where;" to AnnotationSubstitute("Lorg/hibernate/annotations/SQLRestriction;", mapOf("clause" to "value")),
            "Lorg/hibernate/annotations/WhereJoinTable;" to AnnotationSubstitute("Lorg/hibernate/annotations/SQLJoinTableRestriction;", mapOf("clause" to "value")),
            "Lorg/hibernate/annotations/OrderBy;" to AnnotationSubstitute("Lorg/hibernate/annotations/SQLOrder;", mapOf("clause" to "value"))
    )

    /** Hibernate 5 annotations that only affected the DDL Hibernate generated, which a node does not use (Liquibase owns its tables). */
    private val droppedDdlAnnotations: Set<String> = setOf(
            "Lorg/hibernate/annotations/Index;",
            "Lorg/hibernate/annotations/ForeignKey;"
    )

    /** The members of Hibernate 5's `@Table` that only affected generated DDL; its other members changed how Hibernate read and wrote the table. */
    private val tableDdlMembers: Set<String> = setOf("appliesTo", "indexes", "comment", "foreignKey")

    /** Hibernate 5 annotations whose `value` member Hibernate 7 dropped: present now means what `value = true` meant. */
    private val impliedTrueAnnotations: Set<String> = setOf(
            "Lorg/hibernate/annotations/DynamicUpdate;",
            "Lorg/hibernate/annotations/DynamicInsert;"
    )

    /** Hibernate 5's `GenerationTime` constants as Hibernate 7's `@Generated(event = ...)` says them; `NEVER` meant not generated at all. */
    private val generationTimeEvents: Map<String, List<String>> = mapOf(
            "INSERT" to listOf("INSERT"),
            "ALWAYS" to listOf("INSERT", "UPDATE"),
            "NEVER" to emptyList()
    )

    /**
     * The `CascadeType` constants Hibernate 7 removed, with the constants that mean the same under its unified (JPA) cascade model.
     * `DELETE` and `EVICT` were the native names of `REMOVE` and `DETACH`. `SAVE_UPDATE` cascaded Hibernate 5's `save`, `update` and
     * `saveOrUpdate`, and its flush, which saved new children reachable from a managed parent; Hibernate 7's flush cascades `PERSIST`,
     * which does the same, and a converted CorDapp cannot call `save` or `update` (they no longer exist). `MERGE` is deliberately not
     * added: Hibernate 5's save-update cascade never fired for `merge`, so adding it would make `merge` overwrite children it left alone.
     */
    private val substitutedCascadeTypes: Map<String, List<String>> = mapOf(
            "DELETE" to listOf("REMOVE"),
            "EVICT" to listOf("DETACH"),
            "SAVE_UPDATE" to listOf("PERSIST")
    )

    /** Enum types Hibernate 7 replaced by another enum with the same constant names. */
    private val substitutedEnumTypes: Map<String, String> = mapOf(
            "Lorg/hibernate/annotations/CacheModeType;" to "Lorg/hibernate/CacheMode;"
    )

    /**
     * How [transform] treats what it finds beyond the rename.
     *
     * @property refuseMissingReferences Whether a class that refers to Hibernate classes or members missing from Hibernate 7 is refused
     * (the default) or only warned about and converted anyway; the node takes it from [MISSING_HIBERNATE_REFERENCES_PROPERTY] once.
     * @property quiet Logs the warnings about dropped annotations and missing references at debug level instead: for a class whose
     * conversion was already reported, by the start-up pass over the CorDapp jars.
     */
    class Options(val refuseMissingReferences: Boolean = refuseMissingReferencesByDefault(), val quiet: Boolean = false)

    /** What [MISSING_HIBERNATE_REFERENCES_PROPERTY] currently says. */
    fun refuseMissingReferencesByDefault(): Boolean {
        return !MISSING_HIBERNATE_REFERENCES_WARN.equals(System.getProperty(MISSING_HIBERNATE_REFERENCES_PROPERTY), ignoreCase = true)
    }

    /**
     * Whether the bytes of a class file refer to a `javax.persistence` class without also referring to a `jakarta.persistence` one
     * (constant pool entries are ASCII-compatible). A class that refers to both is annotated for both persistence APIs on purpose.
     */
    fun isBuiltForJavaxPersistenceOnly(classBytes: ByteArray): Boolean {
        val text = String(classBytes, Charsets.ISO_8859_1)
        return text.contains(JAVAX_PERSISTENCE) && !text.contains(JAKARTA_PERSISTENCE)
    }

    /**
     * The bytes of the class with every reference to `javax.persistence` renamed to `jakarta.persistence` and the Hibernate annotations
     * converted. [className] is only used in log and error messages. With [hibernateClassLoader], the class loader that has the node's
     * Hibernate 7 on its classpath, every Hibernate annotation, class and member the converted class refers to is resolved there. A
     * Hibernate class that the node does not have but for which [bundledClass] is true (given its internal name) is one the CorDapp
     * ships itself, such as a Hibernate library the node does not ship (Hibernate Validator); it is not checked here, as it is converted
     * and checked on its own when it is loaded. A class the node does have is always resolved against the node's copy, which is the one
     * that runs (the CorDapp class loader delegates to its parent first).
     *
     * @throws JavaxPersistenceConversionError if a Hibernate annotation cannot be converted, or, unless [Options.refuseMissingReferences]
     * is off, if the class refers to a Hibernate class or member that does not exist in Hibernate 7.
     */
    fun transform(
            classBytes: ByteArray,
            className: String,
            hibernateClassLoader: ClassLoader? = null,
            options: Options = Options(),
            bundledClass: (internalName: String) -> Boolean = { false }
    ): ByteArray {
        val resolver = hibernateClassLoader?.let { HibernateResolver(it, bundledClass) }
        val converted = rename(classBytes, JAVAX_PERSISTENCE, JAKARTA_PERSISTENCE,
                decorate = { HibernateAnnotationRewriter(className, resolver, options.quiet, it) },
                decorateOutput = { if (resolver != null) HibernateReferenceChecker(resolver, it) else it })
        if (resolver != null && resolver.missing.isNotEmpty()) {
            val problem = "The class $className uses Hibernate classes or members that do not exist in Hibernate 7 (Corda 4.15), so that " +
                    "code would fail when it runs: ${resolver.missing.joinToString("; ")}."
            if (options.refuseMissingReferences) throw JavaxPersistenceConversionError("$problem $REBUILD_ADVICE")
            val message = "$problem The class is loaded anyway, as the node is configured to warn about such references " +
                    "($MISSING_HIBERNATE_REFERENCES_PROPERTY=$MISSING_HIBERNATE_REFERENCES_WARN). $REBUILD_ADVICE"
            if (options.quiet) logger.debug(message) else logger.warn(message)
        }
        logger.debug { "Converted $className from javax.persistence to jakarta.persistence" }
        return converted
    }

    /**
     * The bytes of the class with every reference to the package [from] renamed to [to] (both in internal form, with a trailing slash):
     * types, descriptors, signatures, annotation values, invokedynamic arguments and the Kotlin metadata string table. [decorate] wraps
     * the visitor chain with any further rewriting of the original class; [decorateOutput] wraps the writer, so sees the renamed class.
     * Public for tests, which use it in the opposite direction to produce classes built for `javax.persistence` from classes built for
     * `jakarta.persistence`.
     */
    fun rename(
            classBytes: ByteArray,
            from: String,
            to: String,
            decorate: (ClassVisitor) -> ClassVisitor = { it },
            decorateOutput: (ClassVisitor) -> ClassVisitor = { it }
    ): ByteArray {
        val reader = ClassReader(classBytes)
        // A fresh writer, so that the constant pool of the result does not keep the entries of the original.
        val writer = ClassWriter(0)
        reader.accept(decorate(KotlinMetadataRenamer(from, to, ClassRemapper(decorateOutput(writer), PackageRemapper(from, to)))), 0)
        return writer.toByteArray()
    }

    private fun simpleName(descriptor: String) = "@" + descriptor.substringAfterLast('/').removeSuffix(";")

    /**
     * Resolves Hibernate classes, annotations and members against a class loader that has Hibernate 7, remembering what is missing.
     * Only a class that the class loader reports as not found counts as missing: a class that exists but cannot be loaded there (an
     * optional dependency of Hibernate's that the node does not have), and a reflection failure while looking into an existing class,
     * count as "exists", so that the check never refuses more than it can prove. Members are looked up the way the JVM resolves them:
     * in the class, its superclasses and all their interfaces, and, for an interface, in `Object` too (an `Object` method called on an
     * interface-typed receiver names the interface as its owner); fields by name and type, as the JVM does.
     */
    private class HibernateResolver(private val classLoader: ClassLoader, private val bundledClass: (String) -> Boolean) {
        /** Descriptions of the missing classes and members, in the order found. */
        val missing = LinkedHashSet<String>()
        private val classes = HashMap<String, Class<*>?>()
        private val annotations = HashMap<String, AnnotationResolution>()
        private val members = HashMap<String, Boolean>()

        sealed class AnnotationResolution {
            object Missing : AnnotationResolution()
            object Unknown : AnnotationResolution()
            class Present(val annotationClass: Class<*>) : AnnotationResolution()
        }

        fun checkType(type: Type) {
            when (type.sort) {
                Type.ARRAY -> checkType(type.elementType)
                Type.OBJECT -> checkClass(type.internalName)
                Type.METHOD -> {
                    type.argumentTypes.forEach(::checkType)
                    checkType(type.returnType)
                }
            }
        }

        fun checkMethod(owner: String, name: String, descriptor: String) {
            val clazz = classOf(owner) ?: return
            val exists = members.getOrPut("$owner.$name$descriptor") {
                runCatching {
                    if (name == "<init>") {
                        clazz.declaredConstructors.any { Type.getConstructorDescriptor(it) == descriptor }
                    } else {
                        hierarchy(clazz).any { type -> type.declaredMethods.any { it.name == name && Type.getMethodDescriptor(it) == descriptor } }
                    }
                }.getOrDefault(true)
            }
            if (!exists) {
                val arguments = Type.getArgumentTypes(descriptor).joinToString { it.className }
                missing += "method ${clazz.name}.$name($arguments)"
            }
        }

        fun checkField(owner: String, name: String, descriptor: String) {
            val clazz = classOf(owner) ?: return
            val exists = members.getOrPut("$owner.$name:$descriptor") {
                runCatching {
                    hierarchy(clazz).any { type -> type.declaredFields.any { it.name == name && Type.getDescriptor(it.type) == descriptor } }
                }.getOrDefault(true)
            }
            if (!exists) missing += "field ${clazz.name}.$name of type ${Type.getType(descriptor).className}"
        }

        /** How a Hibernate annotation (by descriptor) resolves on the node. */
        fun resolveAnnotation(descriptor: String): AnnotationResolution {
            val internalName = descriptor.substring(1, descriptor.length - 1)
            return annotations.getOrPut(internalName) {
                try {
                    AnnotationResolution.Present(Class.forName(internalName.replace('/', '.'), false, classLoader))
                } catch (e: ClassNotFoundException) {
                    AnnotationResolution.Missing
                } catch (e: LinkageError) {
                    AnnotationResolution.Unknown
                }
            }
        }

        private fun checkClass(internalName: String) {
            if (internalName.startsWith(HIBERNATE)) classOf(internalName)
        }

        /**
         * The Hibernate class with the given internal name as the node has it, or null if there is nothing to check: not a Hibernate
         * class, missing on the node but bundled by the CorDapp, missing (recorded once), or present but not loadable here.
         */
        private fun classOf(internalName: String): Class<*>? {
            if (!internalName.startsWith(HIBERNATE)) return null
            return classes.getOrPut(internalName) {
                try {
                    Class.forName(internalName.replace('/', '.'), false, classLoader)
                } catch (e: ClassNotFoundException) {
                    if (!bundledClass(internalName)) missing += "class ${internalName.replace('/', '.')}"
                    null
                } catch (e: LinkageError) {
                    // The class is there but something it depends on is not; the CorDapp's use of it may still be fine.
                    null
                }
            }
        }

        /**
         * The class, its superclasses and every interface any of them implements, which is where the JVM resolves members; for an
         * interface, `Object` as well (JVMS 5.4.3.4).
         */
        private fun hierarchy(clazz: Class<*>): Sequence<Class<*>> = sequence {
            val seen = HashSet<Class<*>>()
            val queue = ArrayDeque<Class<*>>()
            queue += clazz
            if (clazz.isInterface) queue += Any::class.java
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (!seen.add(next)) continue
                yield(next)
                next.superclass?.let { queue += it }
                queue += next.interfaces
            }
        }
    }

    /** Resolves, through [resolver], every reference to a Hibernate class or member in the class it visits (the renamed class). */
    private class HibernateReferenceChecker(private val resolver: HibernateResolver, next: ClassVisitor) : ClassVisitor(Opcodes.ASM9, next) {
        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            superName?.let { resolver.checkType(Type.getObjectType(it)) }
            interfaces?.forEach { resolver.checkType(Type.getObjectType(it)) }
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
            resolver.checkType(Type.getType(descriptor))
            return super.visitField(access, name, descriptor, signature, value)
        }

        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            resolver.checkType(Type.getMethodType(descriptor))
            exceptions?.forEach { resolver.checkType(Type.getObjectType(it)) }
            val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
            return object : MethodVisitor(Opcodes.ASM9, methodVisitor) {
                override fun visitTypeInsn(opcode: Int, type: String) {
                    resolver.checkType(Type.getObjectType(type))
                    super.visitTypeInsn(opcode, type)
                }

                override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                    resolver.checkField(owner, name, descriptor)
                    resolver.checkType(Type.getType(descriptor))
                    super.visitFieldInsn(opcode, owner, name, descriptor)
                }

                override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                    checkHandle(owner, name, descriptor)
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }

                @Suppress("SpreadOperator") // ASM's API takes the arguments as varargs.
                override fun visitInvokeDynamicInsn(name: String, descriptor: String, handle: Handle, vararg arguments: Any) {
                    arguments.forEach { if (it is Handle) checkHandle(it.owner, it.name, it.desc) }
                    super.visitInvokeDynamicInsn(name, descriptor, handle, *arguments)
                }

                override fun visitLdcInsn(value: Any) {
                    if (value is Type) resolver.checkType(value)
                    super.visitLdcInsn(value)
                }

                override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
                    resolver.checkType(Type.getType(descriptor))
                    super.visitMultiANewArrayInsn(descriptor, numDimensions)
                }

                override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
                    type?.let { resolver.checkType(Type.getObjectType(it)) }
                    super.visitTryCatchBlock(start, end, handler, type)
                }

                private fun checkHandle(owner: String, name: String, descriptor: String) {
                    if (owner.startsWith("[")) return
                    resolver.checkType(Type.getMethodType(descriptor))
                    resolver.checkMethod(owner, name, descriptor)
                }
            }
        }
    }

    private class PackageRemapper(private val from: String, private val to: String) : Remapper(Opcodes.ASM9) {
        override fun map(internalName: String): String {
            return if (internalName.startsWith(from)) to + internalName.substring(from.length) else internalName
        }
    }

    /**
     * Renames the package in the string table (`d2`) of a Kotlin class's `@kotlin.Metadata`, whose entries name the types of the
     * class's members as JVM descriptors and internal names, which ASM's remapper does not see. The Kotlin compiler writes those
     * entries whole (it does not use the substring form of the string table), so a plain replacement keeps them consistent.
     */
    private class KotlinMetadataRenamer(private val from: String, private val to: String, next: ClassVisitor) : ClassVisitor(Opcodes.ASM9, next) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            val next = super.visitAnnotation(descriptor, visible) ?: return null
            if (descriptor != KOTLIN_METADATA) return next
            return object : AnnotationVisitor(Opcodes.ASM9, next) {
                override fun visitArray(name: String?): AnnotationVisitor? {
                    val array = super.visitArray(name) ?: return null
                    if (name != KOTLIN_METADATA_STRINGS) return array
                    return object : AnnotationVisitor(Opcodes.ASM9, array) {
                        override fun visit(name: String?, value: Any?) {
                            super.visit(name, if (value is String) value.replace(from, to) else value)
                        }
                    }
                }
            }
        }
    }

    /** Renames the members of an annotation Hibernate 7 renamed, passing everything else through. */
    private class RenamingAnnotationVisitor(private val members: Map<String, String>, next: AnnotationVisitor) : AnnotationVisitor(Opcodes.ASM9, next) {
        override fun visit(name: String?, value: Any?) = super.visit(members[name] ?: name, value)
        override fun visitEnum(name: String?, descriptor: String, value: String) = super.visitEnum(members[name] ?: name, descriptor, value)
        override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor? = super.visitAnnotation(members[name] ?: name, descriptor)
        override fun visitArray(name: String?): AnnotationVisitor? = super.visitArray(members[name] ?: name)
    }

    /** Replaces the enum types Hibernate 7 moved ([substitutedEnumTypes]) in the values it sees, passing everything else through. */
    private open class EnumSubstitutingVisitor(next: AnnotationVisitor) : AnnotationVisitor(Opcodes.ASM9, next) {
        override fun visitEnum(name: String?, descriptor: String, value: String) {
            super.visitEnum(name, substitutedEnumTypes[descriptor] ?: descriptor, value)
        }
    }

    /** Rewrites the `CascadeType` constants of a `@Cascade`: the ones Hibernate 7 removed become their equivalents, each constant once. */
    private class CascadeTypesVisitor(array: AnnotationVisitor) : AnnotationVisitor(Opcodes.ASM9, array) {
        private val emitted = LinkedHashSet<String>()

        override fun visitEnum(name: String?, descriptor: String, value: String) {
            val values = if (descriptor == HIBERNATE_CASCADE_TYPE) substitutedCascadeTypes[value] ?: listOf(value) else listOf(value)
            values.forEach { if (emitted.add(it)) super.visitEnum(name, descriptor, it) }
        }
    }

    /**
     * Converts the Hibernate annotations of the class, its fields, methods and parameters, and the Hibernate annotations nested in the
     * values of any annotation: `@Type` is substituted, renamed annotations are renamed, DDL-only ones are dropped, members Hibernate 7
     * reads differently are rewritten, and every other one is resolved against Hibernate 7 through [resolver] (when there is one), member
     * by member. See the class comment of [JavaxPersistenceTransformer] for the rules. With [quiet], the warnings about what is dropped
     * are logged at debug level, as they were already given for this class.
     */
    private class HibernateAnnotationRewriter(
            private val className: String,
            private val resolver: HibernateResolver?,
            private val quiet: Boolean,
            next: ClassVisitor
    ) : ClassVisitor(Opcodes.ASM9, next) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            return annotation(descriptor, visible, "the class", null) { d, v -> super.visitAnnotation(d, v) }
        }

        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
            val fieldVisitor = super.visitField(access, name, descriptor, signature, value) ?: return null
            return object : FieldVisitor(Opcodes.ASM9, fieldVisitor) {
                override fun visitAnnotation(annotationDescriptor: String, visible: Boolean): AnnotationVisitor? {
                    return annotation(annotationDescriptor, visible, name, descriptor, fieldVisitor::visitAnnotation)
                }
            }
        }

        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
            // A property mapped through its getter: the type the getter returns is the type its annotations map.
            val propertyType = Type.getReturnType(descriptor).descriptor.takeIf { Type.getArgumentTypes(descriptor).isEmpty() }
            return object : MethodVisitor(Opcodes.ASM9, methodVisitor) {
                override fun visitAnnotation(annotationDescriptor: String, visible: Boolean): AnnotationVisitor? {
                    return annotation(annotationDescriptor, visible, name, propertyType, methodVisitor::visitAnnotation)
                }

                override fun visitParameterAnnotation(parameter: Int, annotationDescriptor: String, visible: Boolean): AnnotationVisitor? {
                    return annotation(annotationDescriptor, visible, "parameter $parameter of $name", null) { d, v ->
                        methodVisitor.visitParameterAnnotation(parameter, d, v)
                    }
                }
            }
        }

        private fun warn(message: String) = if (quiet) logger.debug(message) else logger.warn(message)

        /**
         * Handles one annotation found on [site], a member of type [memberDescriptor] (null for the class or a parameter), emitting the
         * annotation to write, if any, through [emit] (descriptor, visible) and returning the visitor for its members, or null to drop it.
         */
        private fun annotation(
                descriptor: String,
                visible: Boolean,
                site: String,
                memberDescriptor: String?,
                emit: (String, Boolean) -> AnnotationVisitor?
        ): AnnotationVisitor? {
            return when (descriptor) {
                HIBERNATE_TYPE -> typeSubstitution(site, memberDescriptor, emit)
                HIBERNATE_TYPE_DEF, HIBERNATE_TYPE_DEFS -> throw JavaxPersistenceConversionError("The class $className uses the Hibernate 5 " +
                        "annotation @TypeDef, which no longer exists in Hibernate 7. $REBUILD_ADVICE")
                HIBERNATE_TABLE, HIBERNATE_TABLES -> tables(descriptor, site)
                HIBERNATE_GENERATED -> generated(visible, site, emit)
                in droppedDdlAnnotations -> dropDdlAnnotation(descriptor, site)
                in substitutedAnnotations -> renamed(descriptor, visible, site, emit)
                in impliedTrueAnnotations -> impliedTrue(descriptor, visible, site, emit)
                else -> kept(descriptor, visible, site, emit)
            }
        }

        private fun dropDdlAnnotation(descriptor: String, site: String): AnnotationVisitor? {
            warn("The class $className uses the Hibernate 5 annotation ${simpleName(descriptor)} on $site, which no longer exists in Hibernate 7; " +
                    "it is dropped, as it only affected the DDL Hibernate generates, which the node does not use")
            return null
        }

        private fun renamed(descriptor: String, visible: Boolean, site: String, emit: (String, Boolean) -> AnnotationVisitor?): AnnotationVisitor? {
            val substitute = substitutedAnnotations.getValue(descriptor)
            val next = emit(substitute.descriptor, visible) ?: return null
            logger.debug { "Replaced ${simpleName(descriptor)} on $className.$site by ${simpleName(substitute.descriptor)}" }
            return RenamingAnnotationVisitor(substitute.members, next)
        }

        /** Every other annotation: a Hibernate one is checked against Hibernate 7 and has its changed members rewritten first, any other is scanned for nested Hibernate ones. */
        private fun kept(descriptor: String, visible: Boolean, site: String, emit: (String, Boolean) -> AnnotationVisitor?): AnnotationVisitor? {
            val next = emit(descriptor, visible) ?: return null
            if (!descriptor.startsWith(HIBERNATE_ANNOTATION_PREFIX)) return NestedAnnotationScanner(site, next)
            return MemberSubstitutingVisitor(descriptor, site, checked(descriptor, site, next))
        }

        /**
         * Consumes a Hibernate 5 `@Table`, or each `@Table` of a `@Tables`, which Hibernate 7 no longer has: dropped, with a warning, when it
         * only sets members that affected the DDL Hibernate generated ([tableDdlMembers]); refused when it sets one that changed how Hibernate
         * read or wrote the secondary table, as Hibernate 7 expresses those through other annotations the node does not substitute.
         */
        private fun tables(descriptor: String, site: String): AnnotationVisitor {
            if (descriptor == HIBERNATE_TABLE) return TableVisitor(site)
            return object : AnnotationVisitor(Opcodes.ASM9) {
                override fun visitArray(name: String?): AnnotationVisitor = object : AnnotationVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor = TableVisitor(site)
                }
            }
        }

        private inner class TableVisitor(private val site: String) : AnnotationVisitor(Opcodes.ASM9) {
            private val members = LinkedHashSet<String>()

            override fun visit(name: String?, value: Any?) {
                name?.let(members::add)
            }

            override fun visitEnum(name: String?, descriptor: String, value: String) {
                name?.let(members::add)
            }

            override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor? {
                name?.let(members::add)
                return null
            }

            override fun visitArray(name: String?): AnnotationVisitor? {
                name?.let(members::add)
                return null
            }

            override fun visitEnd() {
                val behavioural = members - tableDdlMembers
                if (behavioural.isNotEmpty()) {
                    throw JavaxPersistenceConversionError("The class $className uses the Hibernate 5 annotation @Table on $site with " +
                            "${behavioural.joinToString { "`$it`" }}, which changed how Hibernate read and wrote the table, not just the DDL it " +
                            "generated; Hibernate 7 has no @Table, and expresses those through @SecondaryRow and the `table` member of @SQLInsert, " +
                            "@SQLUpdate and @SQLDelete, which the node does not substitute. $REBUILD_ADVICE")
                }
                warn("The class $className uses the Hibernate 5 annotation @Table on $site, which no longer exists in Hibernate 7; it is " +
                        "dropped, as its members (${members.joinToString { "`$it`" }}) only affected the DDL Hibernate generates, which the node " +
                        "does not use")
            }
        }

        /** Buffers a `@DynamicInsert` or `@DynamicUpdate`: `(true)` is emitted without the member Hibernate 7 no longer has, `(false)` is dropped. */
        private fun impliedTrue(descriptor: String, visible: Boolean, site: String, emit: (String, Boolean) -> AnnotationVisitor?): AnnotationVisitor {
            return object : AnnotationVisitor(Opcodes.ASM9) {
                private var setting: Any? = true

                override fun visit(name: String?, value: Any?) {
                    if (name == "value") setting = value
                }

                override fun visitEnd() {
                    if (setting == false) {
                        logger.debug { "Dropped ${simpleName(descriptor)}(false) on $className.$site: Hibernate 7 has no such setting, and false was the default" }
                        return
                    }
                    emit(descriptor, visible)?.visitEnd()
                }
            }
        }

        /**
         * Buffers a `@Generated(GenerationTime)` and emits Hibernate 7's `@Generated(event = ...)` for it ([generationTimeEvents]); `NEVER`,
         * which meant not generated, drops the annotation.
         */
        private fun generated(visible: Boolean, site: String, emit: (String, Boolean) -> AnnotationVisitor?): AnnotationVisitor {
            return object : AnnotationVisitor(Opcodes.ASM9) {
                private var time: String? = null

                override fun visitEnum(name: String?, descriptor: String, value: String) {
                    if (name == "value" && descriptor == HIBERNATE_GENERATION_TIME) time = value
                }

                override fun visitEnd() {
                    val events = generationTimeEvents[time] ?: throw JavaxPersistenceConversionError("The class $className uses the Hibernate 5 " +
                            "annotation @Generated on $site with ${time?.let { "GenerationTime.$it" } ?: "no generation time"}, which the node " +
                            "cannot express as Hibernate 7's @Generated(event = ...). $REBUILD_ADVICE")
                    if (events.isEmpty()) {
                        logger.debug { "Dropped @Generated(GenerationTime.NEVER) on $className.$site: it meant not generated" }
                        return
                    }
                    val next = emit(HIBERNATE_GENERATED, visible) ?: return
                    next.visitArray("event")?.apply {
                        events.forEach { visitEnum(null, HIBERNATE_EVENT_TYPE, it) }
                        visitEnd()
                    }
                    next.visitEnd()
                    logger.debug { "Replaced @Generated(GenerationTime.$time) on $className.$site by @Generated(event = $events)" }
                }
            }
        }

        /** Resolves a Hibernate annotation and the members it sets against Hibernate 7, refusing what Hibernate 7 cannot read. */
        private fun checked(descriptor: String, site: String, next: AnnotationVisitor): AnnotationVisitor {
            val resolution = resolver?.resolveAnnotation(descriptor) ?: return NestedAnnotationScanner(site, next)
            return when (resolution) {
                is HibernateResolver.AnnotationResolution.Missing -> throw JavaxPersistenceConversionError("The class $className uses the " +
                        "Hibernate 5 annotation ${simpleName(descriptor)} on $site, which no longer exists in Hibernate 7 (Corda 4.15). $REBUILD_ADVICE")
                is HibernateResolver.AnnotationResolution.Unknown -> NestedAnnotationScanner(site, next)
                is HibernateResolver.AnnotationResolution.Present -> MemberCheckingVisitor(resolution.annotationClass, MemberChecks(descriptor, site), next)
            }
        }

        /**
         * Rewrites, before they are checked, the members of a Hibernate annotation whose Hibernate 5 form Hibernate 7 reads differently but
         * which mean the same: the `CascadeType` constants it removed, `@ParamDef(type = "name")` as the Java class of the named basic type,
         * `@Cache(include = ...)` as `includeLazy`, and the enum types it moved. Nested Hibernate annotations come back through [annotation].
         */
        private inner class MemberSubstitutingVisitor(
                private val descriptor: String,
                private val site: String,
                next: AnnotationVisitor
        ) : EnumSubstitutingVisitor(next) {
            override fun visit(name: String?, value: Any?) {
                when {
                    descriptor == HIBERNATE_PARAM_DEF && name == "type" && value is String -> super.visit(name, Type.getType(paramDefTypeClass(value)))
                    descriptor == HIBERNATE_CACHE && name == "include" && value is String -> cacheInclude(value)
                    else -> super.visit(name, value)
                }
            }

            override fun visitArray(name: String?): AnnotationVisitor? {
                val array = super.visitArray(name) ?: return null
                return if (descriptor == HIBERNATE_CASCADE && name == "value") CascadeTypesVisitor(array) else EnumSubstitutingVisitor(array)
            }

            private fun paramDefTypeClass(name: String): Class<*> {
                return hibernate5BasicTypeClasses[name] ?: throw JavaxPersistenceConversionError("The class $className sets member `type` of " +
                        "the Hibernate annotation @ParamDef on $site to \"$name\", a Hibernate 5 type name for which Hibernate 7's @ParamDef needs a " +
                        "Java class the node does not know (it knows the basic types ${hibernate5BasicTypes.map { it.names.first() }}). $REBUILD_ADVICE")
            }

            private fun cacheInclude(value: String) {
                when (value) {
                    "all" -> logger.debug { "Dropped @Cache(include = \"all\") on $className.$site: it is Hibernate 7's default (includeLazy = true)" }
                    "non-lazy" -> super.visit("includeLazy", false)
                    else -> throw JavaxPersistenceConversionError("The class $className sets member `include` of the Hibernate annotation @Cache " +
                            "on $site to \"$value\", which Hibernate 5 did not define either (it accepted \"all\" and \"non-lazy\"). $REBUILD_ADVICE")
                }
            }
        }

        /** Passes an annotation through, converting any Hibernate annotation nested in its values. */
        private inner class NestedAnnotationScanner(private val site: String, next: AnnotationVisitor) : AnnotationVisitor(Opcodes.ASM9, next) {
            override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor? {
                return annotation(descriptor, true, site, null) { d, _ -> av.visitAnnotation(name, d) }
            }

            override fun visitArray(name: String?): AnnotationVisitor? {
                val array = av.visitArray(name) ?: return null
                return NestedAnnotationScanner(site, array)
            }
        }

        /** Checks each member a Hibernate annotation sets against Hibernate 7's declaration of the annotation, and the members it requires. */
        private inner class MemberCheckingVisitor(
                private val annotationClass: Class<*>,
                private val checks: MemberChecks,
                next: AnnotationVisitor
        ) : AnnotationVisitor(Opcodes.ASM9, next) {
            private val provided = HashSet<String>()

            private fun member(name: String?): Method {
                val member = annotationClass.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }
                        ?: throw JavaxPersistenceConversionError("The class $className sets member `$name` of the Hibernate annotation " +
                                "${checks.annotation} on ${checks.site}, which Hibernate 7's ${checks.annotation} does not have. $REBUILD_ADVICE")
                provided += member.name
                return member
            }

            override fun visit(name: String?, value: Any?) {
                val member = member(name)
                if (value != null) checks.checkKind(member, member.returnType, value)
                super.visit(name, value)
            }

            override fun visitEnum(name: String?, enumDescriptor: String, value: String) {
                val member = member(name)
                checks.checkEnum(member, member.returnType, enumDescriptor, value)
                super.visitEnum(name, enumDescriptor, value)
            }

            override fun visitAnnotation(name: String?, nestedDescriptor: String): AnnotationVisitor? {
                val member = member(name)
                checks.checkNested(member, member.returnType, nestedDescriptor)
                return annotation(nestedDescriptor, true, checks.site, null) { d, _ -> av.visitAnnotation(name, d) }
            }

            override fun visitArray(name: String?): AnnotationVisitor? {
                val member = member(name)
                if (!member.returnType.isArray) {
                    throw JavaxPersistenceConversionError("The class $className sets member `${member.name}` of the Hibernate annotation " +
                            "${checks.annotation} on ${checks.site} to an array, which Hibernate 7 declares as ${member.returnType.simpleName}. $REBUILD_ADVICE")
                }
                val array = av.visitArray(name) ?: return null
                return ElementCheckingVisitor(checks, member, member.returnType.componentType, array)
            }

            override fun visitEnd() {
                val required = annotationClass.declaredMethods.filter { it.defaultValue == null && it.name !in provided }
                if (required.isNotEmpty()) {
                    throw JavaxPersistenceConversionError("The class $className uses the Hibernate annotation ${checks.annotation} on " +
                            "${checks.site} without ${required.joinToString { "`${it.name}`" }}, which Hibernate 7's ${checks.annotation} requires " +
                            "and Hibernate 5's did not. $REBUILD_ADVICE")
                }
                super.visitEnd()
            }
        }

        /** Checks the elements of an array member against the component type Hibernate 7 declares. */
        private inner class ElementCheckingVisitor(
                private val checks: MemberChecks,
                private val member: Method,
                private val component: Class<*>,
                array: AnnotationVisitor
        ) : AnnotationVisitor(Opcodes.ASM9, array) {
            override fun visit(name: String?, value: Any?) {
                if (value != null) checks.checkKind(member, component, value)
                super.visit(name, value)
            }

            override fun visitEnum(name: String?, enumDescriptor: String, value: String) {
                checks.checkEnum(member, component, enumDescriptor, value)
                super.visitEnum(name, enumDescriptor, value)
            }

            override fun visitAnnotation(name: String?, nestedDescriptor: String): AnnotationVisitor? {
                checks.checkNested(member, component, nestedDescriptor)
                return annotation(nestedDescriptor, true, checks.site, null) { d, _ -> av.visitAnnotation(name, d) }
            }
        }

        /**
         * The checks of the members of one Hibernate annotation ([descriptor]) found on [site], against what Hibernate 7 declares. The
         * values come from the original class, so a JPA type among them is compared as the rename will write it (`jakarta.persistence`).
         */
        private inner class MemberChecks(descriptor: String, val site: String) {
            val annotation = simpleName(descriptor)

            fun checkKind(member: Method, expected: Class<*>, value: Any) {
                if (!kindMatches(expected, value)) {
                    throw JavaxPersistenceConversionError("The class $className sets member `${member.name}` of the Hibernate annotation " +
                            "$annotation on $site to a ${kindName(value)}, which Hibernate 7 declares as ${expected.simpleName}. $REBUILD_ADVICE")
                }
            }

            fun checkEnum(member: Method, expected: Class<*>, enumDescriptor: String, value: String) {
                val written = javaxToJakarta.mapDesc(enumDescriptor)
                val accepted = Type.getDescriptor(expected) == written && expected.enumConstants?.any { (it as Enum<*>).name == value } == true
                if (!accepted) {
                    throw JavaxPersistenceConversionError("The class $className sets member `${member.name}` of the Hibernate annotation " +
                            "$annotation on $site to ${Type.getType(enumDescriptor).className.substringAfterLast('.')}.$value, which " +
                            "Hibernate 7's $annotation does not accept. $REBUILD_ADVICE")
                }
            }

            fun checkNested(member: Method, expected: Class<*>, nestedDescriptor: String) {
                val written = substitutedAnnotations[nestedDescriptor]?.descriptor ?: javaxToJakarta.mapDesc(nestedDescriptor)
                if (Type.getDescriptor(expected) != written) {
                    throw JavaxPersistenceConversionError("The class $className sets member `${member.name}` of the Hibernate annotation " +
                            "$annotation on $site to a ${simpleName(nestedDescriptor)} annotation, which Hibernate 7 declares as " +
                            "${expected.simpleName}. $REBUILD_ADVICE")
                }
            }

            private fun kindMatches(expected: Class<*>, value: Any): Boolean = when (value) {
                is String -> expected == String::class.java
                is Type -> expected == Class::class.java
                is Boolean -> expected == java.lang.Boolean.TYPE
                is Byte -> expected == java.lang.Byte.TYPE
                is Char -> expected == Character.TYPE
                is Short -> expected == java.lang.Short.TYPE
                is Int -> expected == Integer.TYPE
                is Long -> expected == java.lang.Long.TYPE
                is Float -> expected == java.lang.Float.TYPE
                is Double -> expected == java.lang.Double.TYPE
                else -> value.javaClass.isArray && expected == value.javaClass
            }

            private fun kindName(value: Any): String = when (value) {
                is String -> "String"
                is Type -> "Class"
                is Boolean, is Byte, is Char, is Short, is Int, is Long, is Float, is Double -> value.javaClass.simpleName.lowercase()
                else -> value.javaClass.simpleName
            }
        }

        /**
         * Consumes a `@Type` annotation and, once its `type` is known, emits the substitute through [emit] (descriptor, visible): a
         * `@JdbcTypeCode` for [substitutedHibernate5Types], a `@Convert` (with the `@JdbcTypeCode` that pins the column type, where there
         * is one) for [convertedHibernate5Types], nothing for a basic type that was the default of the member's type ([memberDescriptor]).
         */
        private fun typeSubstitution(site: String, memberDescriptor: String?, emit: (String, Boolean) -> AnnotationVisitor?): AnnotationVisitor {
            return object : AnnotationVisitor(Opcodes.ASM9) {
                private var typeName: String? = null

                override fun visit(name: String?, value: Any?) {
                    if (name == "type") typeName = value as? String
                }

                override fun visitEnd() {
                    val name = typeName ?: refuse("the Hibernate annotation @Type on $site without a type name, which the node cannot substitute")
                    val replacement = when (name) {
                        in substitutedHibernate5Types -> jdbcTypeCode(substitutedHibernate5Types.getValue(name))
                        in convertedHibernate5Types -> convert(convertedHibernate5Types.getValue(name))
                        in hibernate5BasicTypesByName -> basicType(name)
                        else -> refuse("the Hibernate 5 annotation @Type(type = \"$name\") on $site, which has no equivalent in Hibernate 7 " +
                                "that the node can substitute (it substitutes ${substitutedHibernate5Types.keys + convertedHibernate5Types.keys}, " +
                                "and drops the basic types ${hibernate5BasicTypes.map { it.names.first() }} on a member of their own type)")
                    }
                    logger.debug { "Replaced @Type(type = \"$name\") on $className.$site by $replacement" }
                }

                private fun refuse(what: String): Nothing = throw JavaxPersistenceConversionError("The class $className uses $what. $REBUILD_ADVICE")

                private fun jdbcTypeCode(code: Int): String {
                    emit(HIBERNATE_JDBC_TYPE_CODE, true)?.apply {
                        visit("value", code)
                        visitEnd()
                    }
                    return "@JdbcTypeCode($code)"
                }

                private fun convert(converter: String): String {
                    emit(JAKARTA_CONVERT, true)?.apply {
                        visit("converter", Type.getObjectType(converter.replace('.', '/')))
                        visitEnd()
                    }
                    val code = converterJdbcTypeCodes[converter]
                    return "@Convert($converter)" + (code?.let { " ${jdbcTypeCode(it)}" } ?: "")
                }

                /**
                 * A basic type on a member of the type it was the default mapping for is dropped, or replaced by the explicit mapping the
                 * type pins; on a member of another type it was a choice the node cannot express.
                 */
                private fun basicType(name: String): String {
                    val type = hibernate5BasicTypesByName.getValue(name)
                    if (memberDescriptor !in type.defaultFor) {
                        refuse("the Hibernate 5 annotation @Type(type = \"$name\") on $site, whose type is " +
                                "${memberDescriptor?.let { Type.getType(it).className } ?: "not known"}; the node only converts it on a " +
                                "${type.defaultFor.joinToString(" or ") { Type.getType(it).className }}, for which it was the default mapping anyway, and " +
                                "Hibernate 7 has no @Type by name")
                    }
                    return when {
                        type.converter != null -> convert(type.converter)
                        type.jdbcTypeCode != null -> jdbcTypeCode(type.jdbcTypeCode)
                        else -> "nothing: it was the default mapping of the member's type"
                    }
                }
            }
        }
    }
}

/**
 * Thrown when a class built for Corda 4.14 or earlier cannot be converted for Hibernate 7. A [LinkageError] rather than an exception,
 * so that it is not swallowed by the code that loads CorDapp classes defensively; the start-up pass over the CorDapp jars reports it as a
 * [CordappConversionException] instead.
 */
class JavaxPersistenceConversionError(message: String) : LinkageError(message)

/**
 * Thrown by the start-up pass over the CorDapp jars (`CordappClassLoader.checkConvertible`) when classes cannot be converted for
 * Hibernate 7, listing all of them. An exception rather than an error, because the node's start-up rethrows errors as fatal and only
 * reports exceptions with their message.
 */
class CordappConversionException(message: String) : CordaRuntimeException(message)
