package net.corda.djvm.analysis

import net.corda.djvm.code.EmitterModule
import net.corda.djvm.code.ruleViolationError
import net.corda.djvm.code.thresholdViolationError
import net.corda.djvm.messages.Severity
import net.corda.djvm.references.ClassModule
import net.corda.djvm.references.Member
import net.corda.djvm.references.MemberModule
import net.corda.djvm.references.MethodBody
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
 * @param additionalPinnedClasses Classes that have already been declared in the sandbox namespace and that should be
 * made available inside the sandboxed environment.
 * @property minimumSeverityLevel The minimum severity level to log and report.
 * @param classPath The extended class path to use for the analysis.
 * @param bootstrapJar The location of a jar containing the Java APIs.
 * @property analyzeAnnotations Analyze annotations despite not being explicitly referenced.
 * @property prefixFilters Only record messages where the originating class name matches one of the provided prefixes.
 * If none are provided, all messages will be reported.
 * @property classModule Module for handling evolution of a class hierarchy during analysis.
 * @property memberModule Module for handling the specification and inspection of class members.
 */
class AnalysisConfiguration(
        val whitelist: Whitelist = Whitelist.MINIMAL,
        additionalPinnedClasses: Set<String> = emptySet(),
        val minimumSeverityLevel: Severity = Severity.WARNING,
        classPath: List<Path> = emptyList(),
        bootstrapJar: Path? = null,
        val analyzeAnnotations: Boolean = false,
        val prefixFilters: List<String> = emptyList(),
        val classModule: ClassModule = ClassModule(),
        val memberModule: MemberModule = MemberModule()
) : Closeable {

    /**
     * Classes that have already been declared in the sandbox namespace and that should be made
     * available inside the sandboxed environment. These classes belong to the application
     * classloader and so are shared across all sandboxes.
     */
    val pinnedClasses: Set<String> = MANDATORY_PINNED_CLASSES + additionalPinnedClasses

    /**
     * These interfaces are modified as they are mapped into the sandbox by
     * having their unsandboxed version "stitched in" as a super-interface.
     * And in some cases, we need to add some synthetic bridge methods as well.
     */
    val stitchedInterfaces: Map<String, List<Member>> get() = STITCHED_INTERFACES

    /**
     * Functionality used to resolve the qualified name and relevant information about a class.
     */
    val classResolver: ClassResolver = ClassResolver(pinnedClasses, TEMPLATE_CLASSES, whitelist, SANDBOX_PREFIX)

    private val bootstrapClassLoader = bootstrapJar?.let { BootstrapClassLoader(it, classResolver) }
    val supportingClassLoader = SourceClassLoader(classPath, classResolver, bootstrapClassLoader)

    @Throws(IOException::class)
    override fun close() {
        supportingClassLoader.use {
            bootstrapClassLoader?.close()
        }
    }

    fun isTemplateClass(className: String): Boolean = className in TEMPLATE_CLASSES
    fun isPinnedClass(className: String): Boolean = className in pinnedClasses

    companion object {
        /**
         * The package name prefix to use for classes loaded into a sandbox.
         */
        private const val SANDBOX_PREFIX: String = "sandbox/"

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
         * classloader.
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
            kotlin.Any::class.java,
            sun.misc.JavaLangAccess::class.java,
            sun.misc.SharedSecrets::class.java
        ).sandboxed() + setOf(
            "sandbox/Task",
            "sandbox/java/lang/DJVM",
            "sandbox/sun/misc/SharedSecrets\$1",
            "sandbox/sun/misc/SharedSecrets\$JavaLangAccessImpl"
        )

        /**
         * These interfaces will be modified as follows when
         * added to the sandbox:
         *
         * <code>interface sandbox.A extends A</code>
         */
        private val STITCHED_INTERFACES: Map<String, List<Member>> = mapOf(
            sandboxed(CharSequence::class.java) to listOf(
                object : MethodBuilder(
                    access = ACC_PUBLIC or ACC_SYNTHETIC or ACC_BRIDGE,
                    className = "sandbox/java/lang/CharSequence",
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
                    className = "sandbox/java/lang/CharSequence",
                    memberName = "toString",
                    descriptor = "()Ljava/lang/String;"
                ).build()
            ),
            sandboxed(Comparable::class.java) to emptyList(),
            sandboxed(Comparator::class.java) to emptyList(),
            sandboxed(Iterable::class.java) to emptyList()
        )

        private fun sandboxed(clazz: Class<*>) = SANDBOX_PREFIX + Type.getInternalName(clazz)
        private fun Set<Class<*>>.sandboxed(): Set<String> = map(Companion::sandboxed).toSet()
    }

    private open class MethodBuilder(
            protected val access: Int,
            protected val className: String,
            protected val memberName: String,
            protected val descriptor: String) {
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
            genericsDetails = "",
            body = bodies
        )
    }
}
