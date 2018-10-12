package net.corda.djvm.rewiring

import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.analysis.ClassAndMemberVisitor
import net.corda.djvm.analysis.ExceptionResolver.Companion.getDJVMExceptionOwner
import net.corda.djvm.analysis.ExceptionResolver.Companion.isDJVMException
import net.corda.djvm.code.asPackagePath
import net.corda.djvm.code.asResourcePath
import net.corda.djvm.references.ClassReference
import net.corda.djvm.source.ClassSource
import net.corda.djvm.utilities.loggerFor
import net.corda.djvm.validation.RuleValidator

/**
 * Class loader that enables registration of rewired classes.
 *
 * @param configuration The configuration to use for the sandbox.
 * @property context The context in which analysis and processing is performed.
 */
class SandboxClassLoader(
        configuration: SandboxConfiguration,
        private val context: AnalysisContext
) : ClassLoader() {

    private val analysisConfiguration = configuration.analysisConfiguration

    /**
     * The instance used to validate that any loaded class complies with the specified rules.
     */
    private val ruleValidator: RuleValidator = RuleValidator(
            rules = configuration.rules,
            configuration = analysisConfiguration
    )

    /**
     * The analyzer used to traverse the class hierarchy.
     */
    private val analyzer: ClassAndMemberVisitor
        get() = ruleValidator

    /**
     * Set of classes that should be left untouched due to whitelisting.
     */
    private val whitelistedClasses = analysisConfiguration.whitelist

    /**
     * Cache of loaded classes.
     */
    private val loadedClasses = mutableMapOf<String, LoadedClass>()

    /**
     * The class loader used to find classes on the extended class path.
     */
    private val supportingClassLoader = analysisConfiguration.supportingClassLoader

    /**
     * The re-writer to use for registered classes.
     */
    private val rewriter: ClassRewriter = ClassRewriter(configuration, supportingClassLoader)

    /**
     * We need to load this class up front, so that we can identify sandboxed exception classes.
     */
    private val throwableClass: Class<*>

    init {
        // Bootstrap the loading of the sandboxed Throwable class.
        loadClassAndBytes(ClassSource.fromClassName("sandbox.java.lang.Object"), context)
        loadClassAndBytes(ClassSource.fromClassName("sandbox.java.lang.StackTraceElement"), context)
        throwableClass = loadClassAndBytes(ClassSource.fromClassName("sandbox.java.lang.Throwable"), context).type
    }

    /**
     * Given a class name, provide its corresponding [LoadedClass] for the sandbox.
     */
    fun loadForSandbox(name: String, context: AnalysisContext): LoadedClass {
        return loadClassAndBytes(ClassSource.fromClassName(analysisConfiguration.classResolver.resolveNormalized(name)), context)
    }

    fun loadForSandbox(source: ClassSource, context: AnalysisContext): LoadedClass {
        return loadForSandbox(source.qualifiedClassName, context)
    }

    /**
     * Load the class with the specified binary name.
     *
     * @param name The binary name of the class.
     * @param resolve If `true` then resolve the class.
     *
     * @return The resulting <tt>Class</tt> object.
     */
    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        val source = ClassSource.fromClassName(name)
        return if (analysisConfiguration.isSandboxClass(source.internalClassName)) {
            loadSandboxClass(source, context).type
        } else {
            super.loadClass(name, resolve)
        }
    }

    private fun loadSandboxClass(source: ClassSource, context: AnalysisContext): LoadedClass {
        return if (isDJVMException(source.internalClassName)) {
            /**
             * We need to load a DJVMException's owner class before we can create
             * its wrapper exception. And loading the owner should also create the
             * wrapper class automatically.
             */
            loadedClasses.getOrElse(source.internalClassName) {
                loadSandboxClass(ClassSource.fromClassName(getDJVMExceptionOwner(source.qualifiedClassName)), context)
                loadedClasses[source.internalClassName]
            } ?: throw ClassNotFoundException(source.qualifiedClassName)
        } else {
            loadClassAndBytes(source, context).also { clazz ->
                /**
                 * Check whether we've just loaded an unpinned sandboxed throwable class.
                 * If we have, we may also need to synthesise a throwable wrapper for it.
                 */
                if (throwableClass.isAssignableFrom(clazz.type) && !analysisConfiguration.isJvmException(source.internalClassName)) {
                    logger.debug("Generating synthetic throwable for ${source.qualifiedClassName}")
                    loadWrapperFor(clazz.type)
                }
            }
        }
    }

    /**
     * Load the class with the specified binary name.
     *
     * @param request The class request, including the binary name of the class.
     * @param context The context in which the analysis is conducted.
     *
     * @return The resulting <tt>Class</tt> object and its byte code representation.
     */
    private fun loadClassAndBytes(request: ClassSource, context: AnalysisContext): LoadedClass {
        logger.debug("Loading class {}, origin={}...", request.qualifiedClassName, request.origin)
        val requestedPath = request.internalClassName
        val sourceName = analysisConfiguration.classResolver.reverseNormalized(request.qualifiedClassName)
        val resolvedName = analysisConfiguration.classResolver.resolveNormalized(sourceName)

        // Check if the class has already been loaded.
        val loadedClass = loadedClasses[requestedPath]
        if (loadedClass != null) {
            logger.trace("Class {} already loaded", request.qualifiedClassName)
            return loadedClass
        } else if (analysisConfiguration.isPinnedClass(requestedPath)) {
            logger.debug("Class {} is loaded unmodified", request.qualifiedClassName)
            return loadUnmodifiedClass(requestedPath)
        }

        val byteCode = if (analysisConfiguration.isTemplateClass(requestedPath)) {
            loadUnmodifiedByteCode(requestedPath)
        } else {
            // Load the byte code for the specified class.
            val reader = supportingClassLoader.classReader(sourceName, context, request.origin)

            // Analyse the class if not matching the whitelist.
            val readClassName = reader.className
            if (!analysisConfiguration.whitelist.matches(readClassName)) {
                logger.trace("Class {} does not match with the whitelist", request.qualifiedClassName)
                logger.trace("Analyzing class {}...", request.qualifiedClassName)
                analyzer.analyze(reader, context)
            }

            // Check if any errors were found during analysis.
            if (context.messages.errorCount > 0) {
                logger.debug("Errors detected after analyzing class {}", request.qualifiedClassName)
                throw SandboxClassLoadingException(context)
            }

            // Transform the class definition and byte code in accordance with provided rules.
            rewriter.rewrite(reader, context)
        }

        // Try to define the transformed class.
        val clazz: Class<*> = try {
            when {
                whitelistedClasses.matches(sourceName.asResourcePath) -> supportingClassLoader.loadClass(sourceName)
                else -> defineClass(resolvedName, byteCode.bytes, 0, byteCode.bytes.size)
            }
        } catch (exception: SecurityException) {
            throw SecurityException("Cannot redefine class '$resolvedName'", exception)
        }

        // Cache transformed class.
        val classWithByteCode = LoadedClass(clazz, byteCode)
        loadedClasses[requestedPath] = classWithByteCode
        if (request.origin != null) {
            context.recordClassOrigin(sourceName, ClassReference(request.origin))
        }

        logger.debug("Loaded class {}, bytes={}, isModified={}",
                request.qualifiedClassName, byteCode.bytes.size, byteCode.isModified)

        return classWithByteCode
    }

    private fun loadUnmodifiedByteCode(internalClassName: String): ByteCode {
        return ByteCode((getSystemClassLoader().getResourceAsStream("$internalClassName.class")
                ?: throw ClassNotFoundException(internalClassName)).readBytes(), false)
    }

    private fun loadUnmodifiedClass(className: String): LoadedClass {
        return LoadedClass(supportingClassLoader.loadClass(className), UNMODIFIED).apply {
            loadedClasses[className] = this
        }
    }

    private fun loadWrapperFor(throwable: Class<*>): LoadedClass {
        val className = analysisConfiguration.exceptionResolver.getThrowableName(throwable)
        return loadedClasses.getOrPut(className) {
            val superName = analysisConfiguration.exceptionResolver.getThrowableSuperName(throwable)
            val byteCode = ThrowableWrapperFactory.toByteCode(className, superName)
            LoadedClass(defineClass(className.asPackagePath, byteCode.bytes, 0, byteCode.bytes.size), byteCode)
        }
    }

    private companion object {
        private val logger = loggerFor<SandboxClassLoader>()
        private val UNMODIFIED = ByteCode(ByteArray(0), false)
    }

}
