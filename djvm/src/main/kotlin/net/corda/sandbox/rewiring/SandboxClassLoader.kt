package net.corda.sandbox.rewiring

import net.corda.sandbox.SandboxConfiguration
import net.corda.sandbox.analysis.AnalysisContext
import net.corda.sandbox.analysis.ClassAndMemberVisitor
import net.corda.sandbox.source.ClassSource
import net.corda.sandbox.source.SourceClassLoader
import net.corda.sandbox.utilities.loggerFor
import net.corda.sandbox.validation.RuleValidator

/**
 * Class loader that enables registration of rewired classes.
 *
 * @property configuration The configuration to use for the sandbox.
 * @property context The context in which analysis and processing is performed.
 */
class SandboxClassLoader(
        val configuration: SandboxConfiguration,
        val context: AnalysisContext
) : ClassLoader() {

    /**
     * The instance used to validate that any loaded class complies with the specified rules.
     */
    private val ruleValidator: RuleValidator = RuleValidator(
            rules = configuration.rules,
            configuration = configuration.analysisConfiguration
    )

    /**
     * The analyzer used to traverse the class hierarchy.
     */
    val analyzer: ClassAndMemberVisitor
        get() = ruleValidator

    /**
     * Set of classes that should be left untouched.
     */
    private val pinnedClasses = configuration.analysisConfiguration.pinnedClasses

    /**
     * Cache of loaded classes.
     */
    private val loadedClasses = mutableMapOf<String, LoadedClass>()

    /**
     * The class loader used to find classes on the extended class path.
     */
    private val supportingClassLoader = SourceClassLoader(
            configuration.analysisConfiguration.classPath,
            configuration.analysisConfiguration.classResolver
    )

    /**
     * The re-writer to use for registered classes.
     */
    private val rewriter: ClassRewriter = ClassRewriter(configuration, supportingClassLoader)

    /**
     * Load the class with the specified binary name.
     *
     * @param name The binary name of the class.
     * @param resolve If `true` then resolve the class.
     *
     * @return The resulting <tt>Class</tt> object.
     */
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return loadClassAndBytes(ClassSource.fromClassName(name), context).type
    }

    /**
     * Load the class with the specified binary name.
     *
     * @param source The class source, including the binary name of the class.
     * @param context The context in which the analysis is conducted.
     *
     * @return The resulting <tt>Class</tt> object and its byte code representation.
     */
    fun loadClassAndBytes(source: ClassSource, context: AnalysisContext): LoadedClass {
        logger.trace("Loading class {}, origin={}...", source.qualifiedClassName, source.origin)
        val name = configuration.analysisConfiguration.classResolver.reverseNormalized(source.qualifiedClassName)
        val resolvedName = configuration.analysisConfiguration.classResolver.resolveNormalized(name)

        // Check if the class has already been loaded.
        val loadedClass = loadedClasses[name]
        if (loadedClass != null) {
            logger.trace("Class {} already loaded", source.qualifiedClassName)
            return loadedClass
        }

        // Load the byte code for the specified class.
        val reader = supportingClassLoader.classReader(name, context, source.origin)

        // Analyse the class if not matching the whitelist.
        val readClassName = reader.className
        if (!configuration.analysisConfiguration.whitelist.matches(readClassName)) {
            logger.trace("Class {} does not match with the whitelist", source.qualifiedClassName)
            if (configuration.analysisConfiguration.analyzePinnedClasses || !pinnedClasses.matches(readClassName)) {
                logger.trace("Analyzing class {}...", source.qualifiedClassName)
                analyzer.analyze(reader, context)
            }
        }

        // Check if the class should be left untouched.
        val qualifiedName = name.replace('.', '/')
        if (pinnedClasses.matches(qualifiedName)) {
            logger.trace("Class {} is marked as pinned", source.qualifiedClassName)
            val pinnedClasses = LoadedClass(
                    supportingClassLoader.loadClass(name),
                    ByteCode(ByteArray(0), false)
            )
            loadedClasses[name] = pinnedClasses
            return pinnedClasses
        }

        // Check if any errors were found during analysis.
        if (context.messages.errorCount > 0) {
            logger.trace("Errors detected after analyzing class {}", source.qualifiedClassName)
            throw SandboxClassLoadingException(context.messages, context.classes)
        }

        // Transform the class definition and byte code in accordance with provided rules.
        val byteCode = rewriter.rewrite(reader, context)

        // Try to define the transformed class.
        val clazz = defineClass(resolvedName, byteCode.bytes, 0, byteCode.bytes.size)

        // Cache transformed class.
        val classWithByteCode = LoadedClass(clazz, byteCode)
        loadedClasses[name] = classWithByteCode

        logger.trace("Loaded class {}, bytes={}, isModified={}",
                source.qualifiedClassName, byteCode.bytes.size, byteCode.isModified)

        return classWithByteCode
    }

    private companion object {
        private val logger = loggerFor<SandboxClassLoader>()
    }

}
