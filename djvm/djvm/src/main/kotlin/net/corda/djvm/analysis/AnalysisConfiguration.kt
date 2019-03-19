package net.corda.djvm.analysis

import net.corda.djvm.code.EmitterModule
import net.corda.djvm.code.ruleViolationError
import net.corda.djvm.code.thresholdViolationError
import net.corda.djvm.messages.Severity
import net.corda.djvm.references.ClassModule
import net.corda.djvm.references.Member
import net.corda.djvm.references.MemberModule
import net.corda.djvm.references.MethodBody
import net.corda.djvm.source.AbstractSourceClassLoader
import net.corda.djvm.source.BootstrapClassLoader
import net.corda.djvm.source.SourceClassLoader
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import sandbox.net.corda.djvm.costing.RuntimeCostAccounter
import java.io.Closeable
import java.io.IOException
import java.nio.file.Path

/**
 * The configuration to use for an analysis.
 *
 * @property whitelist The whitelist of class names.
 * @property pinnedClasses Classes that have already been declared in the sandbox namespace and that should be
 * made available inside the sandboxed environment. These classes belong to the application
 * classloader and so are shared across all sandboxes.
 * @property classResolver Functionality used to resolve the qualified name and relevant information about a class.
 * @property exceptionResolver Resolves the internal names of synthetic exception classes.
 * @property minimumSeverityLevel The minimum severity level to log and report.
 * @property analyzeAnnotations Analyze annotations despite not being explicitly referenced.
 * @property prefixFilters Only record messages where the originating class name matches one of the provided prefixes.
 * If none are provided, all messages will be reported.
 * @property classModule Module for handling evolution of a class hierarchy during analysis.
 * @property memberModule Module for handling the specification and inspection of class members.
 * @property bootstrapClassLoader Optional provider for the Java API classes.
 * @property supportingClassLoader ClassLoader providing the classes to run inside the sandbox.
 * @property isRootConfiguration Effectively, whether we are allowed to close [bootstrapClassLoader].
 */
class AnalysisConfiguration private constructor(
        val whitelist: Whitelist,
        val pinnedClasses: Set<String>,
        val classResolver: ClassResolver,
        val exceptionResolver: ExceptionResolver,
        val minimumSeverityLevel: Severity,
        val analyzeAnnotations: Boolean,
        val prefixFilters: List<String>,
        val classModule: ClassModule,
        val memberModule: MemberModule,
        private val bootstrapClassLoader: BootstrapClassLoader?,
        val supportingClassLoader: AbstractSourceClassLoader,
        private val isRootConfiguration: Boolean
) : Closeable {

    /**
     * These interfaces are modified as they are mapped into the sandbox by
     * having their unsandboxed version "stitched in" as a super-interface.
     * And in some cases, we need to add some synthetic bridge methods as well.
     */
    val stitchedInterfaces: Map<String, List<Member>> get() = STITCHED_INTERFACES

    /**
     * These classes have extra methods added as they are mapped into the sandbox.
     */
    val stitchedClasses: Map<String, List<Member>> get() = STITCHED_CLASSES

    @Throws(IOException::class)
    override fun close() {
        supportingClassLoader.use {
            if (isRootConfiguration) {
                bootstrapClassLoader?.close()
            }
        }
    }

    /**
     * Creates a child [AnalysisConfiguration] with this instance as its parent.
     * The child inherits the same [whitelist], [pinnedClasses] and [bootstrapClassLoader].
     */
    fun createChild(
        classPaths: List<Path> = emptyList(),
        newMinimumSeverityLevel: Severity?
    ): AnalysisConfiguration {
        return AnalysisConfiguration(
            whitelist = whitelist,
            pinnedClasses = pinnedClasses,
            classResolver = classResolver,
            exceptionResolver = exceptionResolver,
            minimumSeverityLevel = newMinimumSeverityLevel ?: minimumSeverityLevel,
            analyzeAnnotations = analyzeAnnotations,
            prefixFilters = prefixFilters,
            classModule = classModule,
            memberModule = memberModule,
            bootstrapClassLoader = bootstrapClassLoader,
            supportingClassLoader = SourceClassLoader(classPaths, classResolver, bootstrapClassLoader),
            isRootConfiguration = false
        )
    }

    fun isTemplateClass(className: String): Boolean = className in TEMPLATE_CLASSES
    fun isPinnedClass(className: String): Boolean = className in pinnedClasses

    fun isJvmException(className: String): Boolean = className in JVM_EXCEPTIONS
    fun isSandboxClass(className: String): Boolean = className.startsWith(SANDBOX_PREFIX) && !isPinnedClass(className)

    companion object {
        /**
         * The package name prefix to use for classes loaded into a sandbox.
         */
        const val SANDBOX_PREFIX: String = "sandbox/"

        /**
         * These class must belong to the application class loader.
         * They should already exist within the sandbox namespace.
         */
        private val MANDATORY_PINNED_CLASSES: Set<String> = setOf(
            RuntimeCostAccounter.TYPE_NAME,
            ruleViolationError,
            thresholdViolationError
        )

        /**
         * These classes will be duplicated into every sandbox's
         * parent classloader.
         */
        private val TEMPLATE_CLASSES: Set<String> = setOf(
            java.lang.Boolean::class.java,
            java.lang.Byte::class.java,
            java.lang.Character::class.java,
            java.lang.Double::class.java,
            java.lang.Float::class.java,
            java.lang.Integer::class.java,
            java.lang.Long::class.java,
            java.lang.Number::class.java,
            java.lang.Runtime::class.java,
            java.lang.Short::class.java,
            java.lang.String::class.java,
            java.lang.String.CASE_INSENSITIVE_ORDER::class.java,
            java.lang.System::class.java,
            java.lang.ThreadLocal::class.java,
            java.lang.Throwable::class.java,
            kotlin.Any::class.java,
            sun.misc.JavaLangAccess::class.java,
            sun.misc.SharedSecrets::class.java
        ).sandboxed() + setOf(
            "sandbox/Task",
            "sandbox/TaskTypes",
            "sandbox/java/lang/Character\$Cache",
            "sandbox/java/lang/DJVM",
            "sandbox/java/lang/DJVMException",
            "sandbox/java/lang/DJVMThrowableWrapper",
            "sandbox/sun/misc/SharedSecrets\$1",
            "sandbox/sun/misc/SharedSecrets\$JavaLangAccessImpl"
        )

        /**
         * These exceptions are thrown by the JVM itself, and
         * so we need to handle them without wrapping them.
         *
         * Note that this set is closed, i.e. every one
         * of these exceptions' [Throwable] super classes
         * is also within this set.
         *
         * The full list of exceptions is determined by:
         * hotspot/src/share/vm/classfile/vmSymbols.hpp
         */
        val JVM_EXCEPTIONS: Set<String> = setOf(
            java.io.IOException::class.java,
            java.lang.AbstractMethodError::class.java,
            java.lang.ArithmeticException::class.java,
            java.lang.ArrayIndexOutOfBoundsException::class.java,
            java.lang.ArrayStoreException::class.java,
            java.lang.ClassCastException::class.java,
            java.lang.ClassCircularityError::class.java,
            java.lang.ClassFormatError::class.java,
            java.lang.ClassNotFoundException::class.java,
            java.lang.CloneNotSupportedException::class.java,
            java.lang.Error::class.java,
            java.lang.Exception::class.java,
            java.lang.ExceptionInInitializerError::class.java,
            java.lang.IllegalAccessError::class.java,
            java.lang.IllegalAccessException::class.java,
            java.lang.IllegalArgumentException::class.java,
            java.lang.IllegalStateException::class.java,
            java.lang.IncompatibleClassChangeError::class.java,
            java.lang.IndexOutOfBoundsException::class.java,
            java.lang.InstantiationError::class.java,
            java.lang.InstantiationException::class.java,
            java.lang.InternalError::class.java,
            java.lang.LinkageError::class.java,
            java.lang.NegativeArraySizeException::class.java,
            java.lang.NoClassDefFoundError::class.java,
            java.lang.NoSuchFieldError::class.java,
            java.lang.NoSuchFieldException::class.java,
            java.lang.NoSuchMethodError::class.java,
            java.lang.NoSuchMethodException::class.java,
            java.lang.NullPointerException::class.java,
            java.lang.OutOfMemoryError::class.java,
            java.lang.ReflectiveOperationException::class.java,
            java.lang.RuntimeException::class.java,
            java.lang.StackOverflowError::class.java,
            java.lang.StringIndexOutOfBoundsException::class.java,
            java.lang.ThreadDeath::class.java,
            java.lang.Throwable::class.java,
            java.lang.UnknownError::class.java,
            java.lang.UnsatisfiedLinkError::class.java,
            java.lang.UnsupportedClassVersionError::class.java,
            java.lang.UnsupportedOperationException::class.java,
            java.lang.VerifyError::class.java,
            java.lang.VirtualMachineError::class.java
        ).sandboxed() + setOf(
            // Mentioned here to prevent the DJVM from generating a synthetic wrapper.
            "sandbox/java/lang/DJVMThrowableWrapper"
        )

        /**
         * These interfaces will be modified as follows when
         * added to the sandbox:
         *
         * <code>interface sandbox.A extends A</code>
         */
        private val STITCHED_INTERFACES: Map<String, List<Member>> = listOf(
            object : MethodBuilder(
                access = ACC_PUBLIC or ACC_SYNTHETIC or ACC_BRIDGE,
                className = sandboxed(CharSequence::class.java),
                memberName = "subSequence",
                descriptor = "(II)Ljava/lang/CharSequence;"
            ) {
                override fun writeBody(emitter: EmitterModule) = with(emitter) {
                    pushObject(0)
                    pushInteger(1)
                    pushInteger(2)
                    invokeInterface(className, memberName, "(II)L$className;")
                    returnObject()
                }
            }.withBody()
             .build(),

            MethodBuilder(
                access = ACC_PUBLIC or ACC_ABSTRACT,
                className = sandboxed(CharSequence::class.java),
                memberName = "toString",
                descriptor = "()Ljava/lang/String;"
            ).build()
        ).mapByClassName() + mapOf(
            sandboxed(Comparable::class.java) to emptyList(),
            sandboxed(Comparator::class.java) to emptyList(),
            sandboxed(Iterable::class.java) to emptyList()
        )

        /**
         * These classes have extra methods added when mapped into the sandbox.
         */
        private val STITCHED_CLASSES: Map<String, List<Member>> = listOf(
            object : MethodBuilder(
                access = ACC_FINAL,
                className = sandboxed(Enum::class.java),
                memberName = "fromDJVM",
                descriptor = "()Ljava/lang/Enum;",
                signature = "()Ljava/lang/Enum<*>;"
            ) {
                override fun writeBody(emitter: EmitterModule) = with(emitter) {
                    pushObject(0)
                    invokeStatic("sandbox/java/lang/DJVM", "fromDJVMEnum", "(Lsandbox/java/lang/Enum;)Ljava/lang/Enum;")
                    returnObject()
                }
            }.withBody()
             .build(),

            object : MethodBuilder(
                access = ACC_BRIDGE or ACC_SYNTHETIC,
                className = sandboxed(Enum::class.java),
                memberName = "fromDJVM",
                descriptor = "()Ljava/lang/Object;"
            ) {
                override fun writeBody(emitter: EmitterModule) = with(emitter) {
                    pushObject(0)
                    invokeVirtual(className, memberName, "()Ljava/lang/Enum;")
                    returnObject()
                }
            }.withBody()
             .build()
        ).mapByClassName()

        private fun sandboxed(clazz: Class<*>): String = (SANDBOX_PREFIX + Type.getInternalName(clazz)).intern()
        private fun Set<Class<*>>.sandboxed(): Set<String> = map(Companion::sandboxed).toSet()
        private fun Iterable<Member>.mapByClassName(): Map<String, List<Member>>
                      = groupBy(Member::className).mapValues(Map.Entry<String, List<Member>>::value)

        /**
         * @see [AnalysisConfiguration]
         */
        fun createRoot(
            whitelist: Whitelist = Whitelist.MINIMAL,
            additionalPinnedClasses: Set<String> = emptySet(),
            minimumSeverityLevel: Severity = Severity.WARNING,
            analyzeAnnotations: Boolean = false,
            prefixFilters: List<String> = emptyList(),
            classModule: ClassModule = ClassModule(),
            memberModule: MemberModule = MemberModule(),
            bootstrapClassLoader: BootstrapClassLoader? = null,
            sourceClassLoaderFactory: (ClassResolver, BootstrapClassLoader?) -> AbstractSourceClassLoader = { classResolver, bootstrapCL ->
                SourceClassLoader(emptyList(), classResolver, bootstrapCL)
            }
        ): AnalysisConfiguration {
            val pinnedClasses = MANDATORY_PINNED_CLASSES + additionalPinnedClasses
            val classResolver = ClassResolver(pinnedClasses, TEMPLATE_CLASSES, whitelist, SANDBOX_PREFIX)

            return AnalysisConfiguration(
                whitelist = whitelist,
                pinnedClasses = pinnedClasses,
                classResolver = classResolver,
                exceptionResolver = ExceptionResolver(JVM_EXCEPTIONS, pinnedClasses, SANDBOX_PREFIX),
                minimumSeverityLevel = minimumSeverityLevel,
                analyzeAnnotations = analyzeAnnotations,
                prefixFilters = prefixFilters,
                classModule = classModule,
                memberModule = memberModule,
                bootstrapClassLoader = bootstrapClassLoader,
                supportingClassLoader = sourceClassLoaderFactory(classResolver, bootstrapClassLoader),
                isRootConfiguration = true
            )
        }
    }

    private open class MethodBuilder(
            protected val access: Int,
            protected val className: String,
            protected val memberName: String,
            protected val descriptor: String,
            protected val signature: String = ""
    ) {
        private val bodies = mutableListOf<MethodBody>()

        protected open fun writeBody(emitter: EmitterModule) {}

        fun withBody(): MethodBuilder {
            bodies.add(::writeBody)
            return this
        }

        fun build() = Member(
            access = access,
            className = className,
            memberName = memberName,
            signature = descriptor,
            genericsDetails = signature,
            body = bodies
        )
    }
}
