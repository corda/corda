package net.corda.djvm.analysis

import net.corda.djvm.code.ruleViolationError
import net.corda.djvm.code.thresholdViolationError
import net.corda.djvm.messages.Severity
import net.corda.djvm.references.ClassModule
import net.corda.djvm.references.MemberModule
import net.corda.djvm.source.BootstrapClassLoader
import net.corda.djvm.source.SourceClassLoader
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
     * available inside the sandboxed environment.
     */
    val pinnedClasses: Set<String> = MANDATORY_PINNED_CLASSES + additionalPinnedClasses

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

    fun isStitchedClass(className: String): Boolean = STITCHED_CLASSES.contains(className)
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
            java.lang.Short::class.java,
            java.lang.String::class.java,
            java.lang.System::class.java,
            java.lang.ThreadLocal::class.java,
            kotlin.Any::class.java
        ).sandboxed() + setOf(
            "sandbox/java/lang/DJVM",
            "sandbox/Task"
        )

        /**
         * These interfaces will be modified as follows when
         * added to the sandbox:
         *
         * <code>interface sandbox.A extends A</code>
         */
        private val STITCHED_CLASSES: Set<String> = setOf(
            CharSequence::class.java,
            Comparable::class.java,
            Iterable::class.java,
            Comparator::class.java
        ).sandboxed()

        private fun Set<Class<*>>.sandboxed(): Set<String> = map(Type::getInternalName).map { SANDBOX_PREFIX + it }.toSet()
    }

}
