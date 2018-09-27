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
import org.junit.After
import org.junit.Assert.assertEquals
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.reflect.jvm.jvmName

abstract class TestBase {

    companion object {

        val ALL_RULES = Discovery.find<Rule>()

        val ALL_EMITTERS = Discovery.find<Emitter>()

        val ALL_DEFINITION_PROVIDERS = Discovery.find<DefinitionProvider>()

        val BLANK = emptySet<Any>()

        val DEFAULT = (ALL_RULES + ALL_EMITTERS + ALL_DEFINITION_PROVIDERS).distinctBy(Any::javaClass)

        val DETERMINISTIC_RT: Path = Paths.get(
                System.getProperty("deterministic-rt.path") ?: throw AssertionError("deterministic-rt.path property not set"))

        /**
         * Get the full name of type [T].
         */
        inline fun <reified T> nameOf(prefix: String = "") = "$prefix${Type.getInternalName(T::class.java)}"

    }

    /**
     * Default analysis configuration.
     */
    val configuration = AnalysisConfiguration(Whitelist.MINIMAL, bootstrapJar = DETERMINISTIC_RT)

    /**
     * Default analysis context
     */
    val context: AnalysisContext
        get() = AnalysisContext.fromConfiguration(configuration)

    @After
    fun destroy() {
        configuration.close()
    }

    /**
     * Short-hand for analysing and validating a class.
     */
    inline fun <reified T> validate(
            minimumSeverityLevel: Severity = Severity.INFORMATIONAL,
            noinline block: (RuleValidator.(AnalysisContext) -> Unit)
    ) {
        val reader = ClassReader(T::class.java.name)
        AnalysisConfiguration(
            minimumSeverityLevel = minimumSeverityLevel,
            classPath = listOf(DETERMINISTIC_RT)
        ).use { analysisConfiguration ->
            val validator = RuleValidator(ALL_RULES, analysisConfiguration)
            val context = AnalysisContext.fromConfiguration(analysisConfiguration)
            validator.analyze(reader, context)
            block(validator, context)
        }
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
        var whitelist = Whitelist.MINIMAL
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
        thread {
            try {
                val pinnedTestClasses = pinnedClasses.map(Type::getInternalName).toSet()
                AnalysisConfiguration(
                    whitelist = whitelist,
                    bootstrapJar = DETERMINISTIC_RT,
                    additionalPinnedClasses = pinnedTestClasses,
                    minimumSeverityLevel = minimumSeverityLevel
                ).use { analysisConfiguration ->
                    SandboxRuntimeContext(SandboxConfiguration.of(
                            executionProfile, rules, emitters, definitionProviders, enableTracing, analysisConfiguration
                    )).use {
                        assertThat(runtimeCosts).areZero()
                        action(this)
                    }
                }
            } catch (exception: Throwable) {
                thrownException = exception
            }
        }.join()
        throw thrownException ?: return
    }

    /**
     * Get a class reference from a class hierarchy based on [T].
     */
    inline fun <reified T> ClassHierarchy.get() = this[nameOf<T>()]!!

    /**
     * Create a new instance of a class using the sandbox class loader.
     */
    inline fun <reified T : Callable> SandboxRuntimeContext.newCallable(): LoadedClass = loadClass<T>()

    inline fun <reified T : Any> SandboxRuntimeContext.loadClass(): LoadedClass = loadClass(T::class.jvmName)

    fun SandboxRuntimeContext.loadClass(className: String): LoadedClass =
            classLoader.loadClassAndBytes(ClassSource.fromClassName(className), context)

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
    protected class Writer : ClassWriter(COMPUTE_FRAMES or COMPUTE_MAXS) {
        init {
            assertEquals(ClassAndMemberVisitor.API_VERSION, api)
        }
    }

}
