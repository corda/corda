package net.corda.sandbox.execution

import net.corda.sandbox.SandboxConfiguration
import net.corda.sandbox.analysis.AnalysisContext
import net.corda.sandbox.messages.Message
import net.corda.sandbox.references.ClassReference
import net.corda.sandbox.rewiring.LoadedClass
import net.corda.sandbox.rewiring.SandboxClassLoader
import net.corda.sandbox.rewiring.SandboxClassLoadingException
import net.corda.sandbox.source.ClassSource
import net.corda.sandbox.utilities.loggerFor
import net.corda.sandbox.validation.ReferenceValidationSummary
import net.corda.sandbox.validation.ReferenceValidator
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
        logger.trace("Executing {} with input {}...", runnableClass, input)
        val classSources = listOf(runnableClass)
        val context = AnalysisContext.fromConfiguration(configuration.analysisConfiguration, classSources)
        val result = IsolatedRunnable(runnableClass.qualifiedClassName, configuration, context).run {
            validate(context, classLoader, classSources)
            val loadedClass = classLoader.loadClassAndBytes(runnableClass, context)
            val instance = loadedClass.type.newInstance()
            val method = loadedClass.type.getMethod("run", Any::class.java)
            try {
                @Suppress("UNCHECKED_CAST")
                method.invoke(instance, input) as? TOutput?
            } catch (ex: InvocationTargetException) {
                when (ex.targetException) {
                    is StackOverflowError -> throw StackOverflowError("Stack overflow")
                    else -> throw ex.targetException
                }
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
        val result = IsolatedRunnable("LoadClass", configuration, context).run {
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
        val result = IsolatedRunnable("Validation", configuration, context).run {
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
            try {
                classLoader.loadClassAndBytes(classSource, context)
            } catch (exception: SandboxClassLoadingException) {
                // Continue; all warnings and errors are captured in [context.messages]
            }
            context.classes[className]?.apply {
                context.references.referencesFromLocation(className)
                        .map { it.reference }
                        .filterIsInstance<ClassReference>()
                        .distinct()
                        .map { ClassSource.fromClassName(it.className, className) }
                        .forEach { enqueue(it) }
            }
        }

        // Validate all references in class hierarchy before proceeding.
        referenceValidator.validate(context, classLoader.analyzer)

        if (context.messages.errorCount > 0) {
            throw SandboxClassLoadingException(context.messages, context.classes)
        }

        return ReferenceValidationSummary(context.classes, context.messages)
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

    private val logger = loggerFor<SandboxExecutor<TInput, TOutput>>()

}
