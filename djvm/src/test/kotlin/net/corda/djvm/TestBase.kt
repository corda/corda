package net.corda.djvm

import foo.bar.sandbox.Callable
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.analysis.ClassAndMemberVisitor
import net.corda.djvm.analysis.Whitelist
import net.corda.djvm.assertions.AssertionExtensions.assertThat
import net.corda.djvm.code.DefinitionProvider
import net.corda.djvm.code.Emitter
import net.corda.djvm.execution.ExecutionProfile
import net.corda.djvm.messages.Severity
import net.corda.djvm.references.ClassHierarchy
import net.corda.djvm.rewiring.LoadedClass
import net.corda.djvm.rules.Rule
import net.corda.djvm.source.ClassSource
import net.corda.djvm.utilities.Discovery
import net.corda.djvm.validation.RuleValidator
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import java.lang.reflect.InvocationTargetException

open class TestBase {

    companion object {

        val ALL_RULES = Discovery.find<Rule>()

        val ALL_EMITTERS = Discovery.find<Emitter>()

        val ALL_DEFINITION_PROVIDERS = Discovery.find<DefinitionProvider>()

        val BLANK = emptySet<Any>()

        val DEFAULT = (ALL_RULES + ALL_EMITTERS + ALL_DEFINITION_PROVIDERS)
                .toSet().distinctBy { it.javaClass }

        // Ignoring these as there's not much point instrumenting the test libraries.
        val PINNED_CLASSES_FOR_TEST = setOf(
                Regex("org/junit/.*"),
                Regex("org/assertj/.*")
        )

        /**
         * Get the full name of type [T].
         */
        inline fun <reified T> nameOf(prefix: String = "") =
                "$prefix${T::class.java.name.replace('.', '/')}"

    }

    /**
     * Default analysis configuration.
     */
    val configuration = AnalysisConfiguration(Whitelist.DEFAULT)

    /**
     * Default analysis context
     */
    val context: AnalysisContext
        get() = AnalysisContext.fromConfiguration(configuration, emptyList())

    /**
     * Short-hand for analysing and validating a class.
     */
    inline fun <reified T> validate(
            minimumSeverityLevel: Severity = Severity.INFORMATIONAL,
            noinline block: (RuleValidator.(AnalysisContext) -> Unit)
    ) {
        val reader = ClassReader(T::class.java.name)
        val configuration = AnalysisConfiguration(minimumSeverityLevel = minimumSeverityLevel)
        val validator = RuleValidator(ALL_RULES, configuration)
        val context = AnalysisContext.fromConfiguration(
                configuration,
                listOf(ClassSource.fromClassName(reader.className))
        )
        validator.analyze(reader, context)
        block(validator, context)
    }

    /**
     * Short-hand for analysing a class.
     */
    inline fun analyze(block: (ClassAndMemberVisitor.(AnalysisContext) -> Unit)) {
        val validator = RuleValidator(emptyList(), configuration)
        block(validator, context)
    }

    /**
     * Run action on a separate thread to ensure that the code is run off a clean slate. The sandbox context is local to
     * the current thread, so this allows inspection of the cost summary object, etc. from within the provided delegate.
     */
    fun sandbox(
            vararg options: Any,
            pinnedClasses: Set<java.lang.Class<*>> = emptySet(),
            minimumSeverityLevel: Severity = Severity.WARNING,
            enableTracing: Boolean = true,
            action: SandboxRuntimeContext.() -> Unit
    ) {
        val rules = mutableListOf<Rule>()
        val emitters = mutableListOf<Emitter>()
        val definitionProviders = mutableListOf<DefinitionProvider>()
        val classSources = mutableListOf<ClassSource>()
        var executionProfile = ExecutionProfile.UNLIMITED
        var whitelist = Whitelist.DEFAULT
        for (option in options) {
            when (option) {
                is Rule -> rules.add(option)
                is Emitter -> emitters.add(option)
                is DefinitionProvider -> definitionProviders.add(option)
                is ExecutionProfile -> executionProfile = option
                is ClassSource -> classSources.add(option)
                is Whitelist -> whitelist = option
                is List<*> -> {
                    rules.addAll(option.filterIsInstance<Rule>())
                    emitters.addAll(option.filterIsInstance<Emitter>())
                    definitionProviders.addAll(option.filterIsInstance<DefinitionProvider>())
                }
            }
        }
        var thrownException: Throwable? = null
        Thread {
            try {
                val pinnedTestClasses = pinnedClasses
                        .map { Regex(it.name.replace('.', '/').replace("$", "\\\$")) }
                        .toSet() + PINNED_CLASSES_FOR_TEST
                val analysisConfiguration = AnalysisConfiguration(
                        whitelist = whitelist,
                        pinnedClasses = whitelist + Whitelist.PINNED_CLASSES + pinnedTestClasses,
                        minimumSeverityLevel = minimumSeverityLevel
                )
                SandboxRuntimeContext(SandboxConfiguration.of(
                        executionProfile, rules, emitters, definitionProviders, enableTracing, analysisConfiguration
                ), classSources).use {
                    assertThat(runtimeCosts).areZero()
                    action(this)
                }
            } catch (exception: Throwable) {
                thrownException = exception
            }
        }.apply {
            start()
            join()
        }
        throw thrownException ?: return
    }

    /**
     * Get a class reference from a class hierarchy based on [T].
     */
    inline fun <reified T> ClassHierarchy.get() = this[nameOf<T>()]!!

    /**
     * Create a new instance of a class using the sandbox class loader.
     */
    inline fun <reified T : Callable> SandboxRuntimeContext.newCallable() =
            classLoader.loadClassAndBytes(ClassSource.fromClassName(T::class.java.name), context)

    /**
     * Run the entry-point of the loaded [Callable] class.
     */
    fun LoadedClass.createAndInvoke(methodName: String = "call") {
        val instance = type.newInstance()
        val method = instance.javaClass.getMethod(methodName)
        try {
            method.invoke(instance)
        } catch (ex: InvocationTargetException) {
            throw ex.targetException
        }
    }

    /**
     * Stub visitor.
     */
    protected class Visitor : ClassVisitor(ClassAndMemberVisitor.API_VERSION)

}
