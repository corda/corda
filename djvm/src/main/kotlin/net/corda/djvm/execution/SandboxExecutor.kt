package net.corda.djvm.execution

import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.messages.Message
import net.corda.djvm.references.ClassReference
import net.corda.djvm.references.MemberReference
import net.corda.djvm.rewiring.LoadedClass
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.rewiring.SandboxClassLoadingException
import net.corda.djvm.source.ClassSource
import net.corda.djvm.utilities.loggerFor
import net.corda.djvm.validation.ReferenceValidationSummary
import net.corda.djvm.validation.ReferenceValidator
import java.lang.reflect.InvocationTargetException

/**
 * The executor is responsible for spinning up a sandboxed environment and launching the referenced code block inside
 * it. Any exceptions should be forwarded to the caller of [SandboxExecutor.run]. Similarly, the returned output from
 * the referenced code block should be returned to the caller.
 *
 * @property configuration The configuration of sandbox.
 */
open class SandboxExecutor<in TInput, out TOutput>(
        protected val configuration: SandboxConfiguration = SandboxConfiguration.DEFAULT
) {

    private val classModule = configuration.analysisConfiguration.classModule

    private val classResolver = configuration.analysisConfiguration.classResolver

    private val whitelist = configuration.analysisConfiguration.whitelist

    /**
     * Module used to validate all traversable references before instantiating and executing a [SandboxedRunnable].
     */
    private val referenceValidator = ReferenceValidator(configuration.analysisConfiguration)

    /**
     * Executes a [SandboxedRunnable] implementation.
     *
     * @param runnableClass The entry point of the sandboxed code to run.
     * @param input The input to provide to the sandboxed environment.
     *
     * @returns The output returned from the sandboxed code upon successful completion.
     * @throws SandboxException Any exception thrown inside the sandbox gets wrapped and re-thrown in the context of the
     * caller, with additional information about the sandboxed environment.
     */
    @Throws(Exception::class)
    open fun run(
            runnableClass: ClassSource,
            input: TInput
    ): ExecutionSummaryWithResult<TOutput?> {
        // 1. We first do a breath first traversal of the class hierarchy, starting from the requested class.
        //    The branching is defined by class references from referencesFromLocation.
        // 2. For each class we run validation against defined rules.
        // 3. Since this is hitting the class loader, we are also remapping and rewriting the classes using the provided
        //    emitters and definition providers.
        // 4. While traversing and validating, we build up another queue of references inside the reference validator.
        // 5. We drain this queue by validating class references and member references; this means validating the
        //    existence of these referenced classes and members, and making sure that rule validation has been run on
        //    all reachable code.
        // 6. For execution, we then load the top-level class, implementing the SandboxedRunnable interface, again and
        //    and consequently hit the cache. Once loaded, we can execute the code on the spawned thread, i.e., in an
        //    isolated environment.
        logger.trace("Executing {} with input {}...", runnableClass, input)
        // TODO Class sources can be analyzed in parallel, although this require making the analysis context thread-safe
        // To do so, one could start by batching the first X classes from the class sources and analyse each one in
        // parallel, caching any intermediate state and subsequently process enqueued sources in parallel batches as well.
        // Note that this would require some rework of the [IsolatedTask] and the class loader to bypass the limitation
        // of caching and state preserved in thread-local contexts.
        val classSources = listOf(runnableClass)
        val context = AnalysisContext.fromConfiguration(configuration.analysisConfiguration, classSources)
        val result = IsolatedTask(runnableClass.qualifiedClassName, configuration, context).run {
            validate(context, classLoader, classSources)
            val loadedClass = classLoader.loadClassAndBytes(runnableClass, context)
            val instance = loadedClass.type.newInstance()
            val method = loadedClass.type.getMethod("run", Any::class.java)
            try {
                @Suppress("UNCHECKED_CAST")
                method.invoke(instance, input) as? TOutput?
            } catch (ex: InvocationTargetException) {
                throw ex.targetException
            }
        }
        logger.trace("Execution of {} with input {} resulted in {}", runnableClass, input, result)
        when (result.exception) {
            null -> return ExecutionSummaryWithResult(result.output, result.costs)
            else -> throw SandboxException(
                    Message.getMessageFromException(result.exception),
                    result.identifier,
                    runnableClass,
                    ExecutionSummary(result.costs),
                    result.exception
            )
        }
    }

    /**
     * Load a class source using the sandbox class loader, yielding a [LoadedClass] object with the class' byte code,
     * type and name attached.
     *
     * @param classSource The class source to load.
     *
     * @return A [LoadedClass] with the class' byte code, type and name.
     */
    fun load(classSource: ClassSource): LoadedClass {
        val context = AnalysisContext.fromConfiguration(configuration.analysisConfiguration, listOf(classSource))
        val result = IsolatedTask("LoadClass", configuration, context).run {
            classLoader.loadClassAndBytes(classSource, context)
        }
        return result.output ?: throw ClassNotFoundException(classSource.qualifiedClassName)
    }

    /**
     * Validate the provided class source(s). This method runs the same validation that takes place in [run], except
     * from runtime accounting as the entry point(s) will never be executed.
     *
     * @param classSources The classes that, together with their dependencies, should be validated.
     *
     * @return A collection of loaded classes with their byte code representation for the provided class sources, and a
     * set of messages produced during validation.
     * @throws Exception Upon failure, an exception with details about any rule violations and/or invalid references.
     */
    @Throws(SandboxClassLoadingException::class)
    fun validate(vararg classSources: ClassSource): ReferenceValidationSummary {
        logger.trace("Validating {}...", classSources)
        val context = AnalysisContext.fromConfiguration(configuration.analysisConfiguration, classSources.toList())
        val result = IsolatedTask("Validation", configuration, context).run {
            validate(context, classLoader, classSources.toList())
        }
        logger.trace("Validation of {} resulted in {}", classSources, result)
        when (result.exception) {
            null -> return result.output!!
            else -> throw result.exception
        }
    }

    /**
     * Validate the provided class source(s) using a pre-defined analysis context.
     *
     * @param context The pre-defined analysis context to use during validation.
     * @param classLoader The class loader to use for validation.
     * @param classSources The classes that, together with their dependencies, should be validated.
     *
     * @return A collection of loaded classes with their byte code representation for the provided class sources, and a
     * set of messages produced during validation.
     * @throws Exception Upon failure, an exception with details about any rule violations and/or invalid references.
     */
    private fun validate(
            context: AnalysisContext, classLoader: SandboxClassLoader, classSources: List<ClassSource>
    ): ReferenceValidationSummary {
        processClassQueue(*classSources.toTypedArray()) { classSource, className ->
            val didLoad = try {
                classLoader.loadClassAndBytes(classSource, context)
                true
            } catch (exception: SandboxClassLoadingException) {
                // Continue; all warnings and errors are captured in [context.messages]
                false
            }
            if (didLoad) {
                context.classes[className]?.apply {
                    context.references.referencesFromLocation(className)
                            .map { it.reference }
                            .filterIsInstance<ClassReference>()
                            .filter { it.className != className }
                            .distinct()
                            .map { ClassSource.fromClassName(it.className, className) }
                            .forEach(::enqueue)
                }
            }
        }
        failOnReportedErrorsInContext(context)

        // Validate all references in class hierarchy before proceeding.
        referenceValidator.validate(context, classLoader.analyzer)
        failOnReportedErrorsInContext(context)

        return ReferenceValidationSummary(context.classes, context.messages, context.classOrigins)
    }

    /**
     * Process a dynamic queue of [ClassSource] entries.
     */
    private inline fun processClassQueue(
            vararg elements: ClassSource, action: QueueProcessor<ClassSource>.(ClassSource, String) -> Unit
    ) {
        QueueProcessor({ it.qualifiedClassName }, *elements).process { classSource ->
            val className = classResolver.reverse(classModule.getBinaryClassName(classSource.qualifiedClassName))
            if (!whitelist.matches(className)) {
                action(classSource, className)
            }
        }
    }

    /**
     * Fail if there are reported errors in the current analysis context.
     */
    private fun failOnReportedErrorsInContext(context: AnalysisContext) {
        if (context.messages.errorCount > 0) {
            for (reference in context.references) {
                for (location in context.references.locationsFromReference(reference)) {
                    val originReference = when {
                        location.memberName.isBlank() -> ClassReference(location.className)
                        else -> MemberReference(location.className, location.memberName, location.signature)
                    }
                    context.recordClassOrigin(reference.className, originReference)
                }
            }
            throw SandboxClassLoadingException(context)
        }
    }

    private val logger = loggerFor<SandboxExecutor<TInput, TOutput>>()

}
