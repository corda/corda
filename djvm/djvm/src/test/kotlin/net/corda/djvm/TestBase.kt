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
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.rules.Rule
import net.corda.djvm.rules.implementation.*
import net.corda.djvm.source.BootstrapClassLoader
import net.corda.djvm.source.ClassSource
import net.corda.djvm.source.SandboxSourceClassLoader
import net.corda.djvm.utilities.Discovery
import net.corda.djvm.validation.RuleValidator
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
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

        // We need at least these emitters to handle the Java API classes.
        @JvmField
        val BASIC_EMITTERS: List<Emitter> = listOf(
            AlwaysInheritFromSandboxedObject(),
            ArgumentUnwrapper(),
            HandleExceptionUnwrapper(),
            ReturnTypeWrapper(),
            RewriteClassMethods(),
            RewriteObjectMethods(),
            StringConstantWrapper(),
            ThrowExceptionWrapper()
        )

        val ALL_DEFINITION_PROVIDERS = Discovery.find<DefinitionProvider>()

        // We need at least these providers to handle the Java API classes.
        @JvmField
        val BASIC_DEFINITION_PROVIDERS: List<DefinitionProvider> = listOf(
            AlwaysInheritFromSandboxedObject(),
            StaticConstantRemover()
        )

        @JvmField
        val BLANK = emptySet<Any>()

        @JvmField
        val DEFAULT = (ALL_RULES + ALL_EMITTERS + ALL_DEFINITION_PROVIDERS).distinctBy(Any::javaClass)

        @JvmField
        val DETERMINISTIC_RT: Path = Paths.get(
                System.getProperty("deterministic-rt.path") ?: throw AssertionError("deterministic-rt.path property not set"))

        private lateinit var parentConfiguration: SandboxConfiguration
        lateinit var parentClassLoader: SandboxClassLoader

        /**
         * Get the full name of type [T].
         */
        inline fun <reified T> nameOf(prefix: String = "") = "$prefix${Type.getInternalName(T::class.java)}"

        @BeforeClass
        @JvmStatic
        fun setupParentClassLoader() {
            val rootConfiguration = AnalysisConfiguration.createRoot(
                Whitelist.MINIMAL,
                bootstrapClassLoader = BootstrapClassLoader(DETERMINISTIC_RT),
                sourceClassLoaderFactory = { classResolver, bootstrapClassLoader ->
                    SandboxSourceClassLoader(classResolver,  bootstrapClassLoader!!)
                },
                additionalPinnedClasses = setOf(
                    Utilities::class.java
                ).map(Type::getInternalName).toSet()
            )
            parentConfiguration = SandboxConfiguration.of(
                ExecutionProfile.UNLIMITED,
                ALL_RULES,
                ALL_EMITTERS,
                ALL_DEFINITION_PROVIDERS,
                true,
                rootConfiguration
            )
            parentClassLoader = SandboxClassLoader.createFor(parentConfiguration)
        }

        @AfterClass
        @JvmStatic
        fun destroyRootContext() {
            parentConfiguration.analysisConfiguration.close()
        }
    }

    /**
     * Default analysis configuration.
     */
    val configuration = AnalysisConfiguration.createRoot(
        Whitelist.MINIMAL,
        bootstrapClassLoader = BootstrapClassLoader(DETERMINISTIC_RT)
    )

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
        AnalysisConfiguration.createRoot(
            minimumSeverityLevel = minimumSeverityLevel,
            bootstrapClassLoader = BootstrapClassLoader(DETERMINISTIC_RT)
        ).use { analysisConfiguration ->
            val validator = RuleValidator(ALL_RULES, analysisConfiguration)
            val context = AnalysisContext.fromConfiguration(analysisConfiguration)
            validator.analyze(reader, context)
            block(validator, context)
        }
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
        val emitters = mutableListOf<Emitter>().apply { addAll(BASIC_EMITTERS) }
        val definitionProviders = mutableListOf<DefinitionProvider>().apply { addAll(BASIC_DEFINITION_PROVIDERS) }
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
                AnalysisConfiguration.createRoot(
                    whitelist = whitelist,
                    additionalPinnedClasses = pinnedTestClasses,
                    minimumSeverityLevel = minimumSeverityLevel,
                    bootstrapClassLoader = BootstrapClassLoader(DETERMINISTIC_RT)
                ).use { analysisConfiguration ->
                    SandboxRuntimeContext(SandboxConfiguration.of(
                        executionProfile,
                        rules.distinctBy(Any::javaClass),
                        emitters.distinctBy(Any::javaClass),
                        definitionProviders.distinctBy(Any::javaClass),
                        enableTracing,
                        analysisConfiguration
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

    fun parentedSandbox(
        minimumSeverityLevel: Severity = Severity.WARNING,
        enableTracing: Boolean = true,
        action: SandboxRuntimeContext.() -> Unit
    ) {
        var thrownException: Throwable? = null
        thread {
            try {
                parentConfiguration.analysisConfiguration.createChild(
                    newMinimumSeverityLevel = minimumSeverityLevel
                ).use { analysisConfiguration ->
                    SandboxRuntimeContext(SandboxConfiguration.of(
                        parentConfiguration.executionProfile,
                        parentConfiguration.rules,
                        parentConfiguration.emitters,
                        parentConfiguration.definitionProviders,
                        enableTracing,
                        analysisConfiguration,
                        parentClassLoader
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

    fun SandboxRuntimeContext.loadClass(className: String): LoadedClass = classLoader.loadForSandbox(className)

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

    @Suppress("MemberVisibilityCanBePrivate")
    protected class DJVM(private val classLoader: ClassLoader) {
        private val djvm: Class<*> = classFor("sandbox.java.lang.DJVM")
        val objectClass: Class<*> by lazy { classFor("sandbox.java.lang.Object") }
        val stringClass: Class<*> by lazy { classFor("sandbox.java.lang.String") }
        val longClass: Class<*> by lazy { classFor("sandbox.java.lang.Long") }
        val integerClass: Class<*> by lazy { classFor("sandbox.java.lang.Integer") }
        val shortClass: Class<*> by lazy { classFor("sandbox.java.lang.Short") }
        val byteClass: Class<*> by lazy { classFor("sandbox.java.lang.Byte") }
        val characterClass: Class<*> by lazy { classFor("sandbox.java.lang.Character") }
        val booleanClass: Class<*> by lazy { classFor("sandbox.java.lang.Boolean") }
        val doubleClass: Class<*> by lazy { classFor("sandbox.java.lang.Double") }
        val floatClass: Class<*> by lazy { classFor("sandbox.java.lang.Float") }
        val throwableClass: Class<*> by lazy { classFor("sandbox.java.lang.Throwable") }
        val stackTraceElementClass: Class<*> by lazy { classFor("sandbox.java.lang.StackTraceElement") }

        fun classFor(className: String): Class<*> = Class.forName(className, false, classLoader)

        fun sandbox(obj: Any): Any {
            return djvm.getMethod("sandbox", Any::class.java).invoke(null, obj)
        }

        fun unsandbox(obj: Any): Any {
            return djvm.getMethod("unsandbox", Any::class.java).invoke(null, obj)
        }

        fun stringOf(str: String): Any {
            return stringClass.getMethod("toDJVM", String::class.java).invoke(null, str)
        }

        fun longOf(l: Long): Any {
            return longClass.getMethod("toDJVM", Long::class.javaObjectType).invoke(null, l)
        }

        fun intOf(i: Int): Any {
            return integerClass.getMethod("toDJVM", Int::class.javaObjectType).invoke(null, i)
        }

        fun shortOf(i: Int): Any {
            return shortClass.getMethod("toDJVM", Short::class.javaObjectType).invoke(null, i.toShort())
        }

        fun byteOf(i: Int): Any {
            return byteClass.getMethod("toDJVM", Byte::class.javaObjectType).invoke(null, i.toByte())
        }

        fun charOf(c: Char): Any {
            return characterClass.getMethod("toDJVM", Char::class.javaObjectType).invoke(null, c)
        }

        fun booleanOf(bool: Boolean): Any {
            return booleanClass.getMethod("toDJVM", Boolean::class.javaObjectType).invoke(null, bool)
        }

        fun doubleOf(d: Double): Any {
            return doubleClass.getMethod("toDJVM", Double::class.javaObjectType).invoke(null, d)
        }

        fun floatOf(f: Float): Any {
            return floatClass.getMethod("toDJVM", Float::class.javaObjectType).invoke(null, f)
        }

        fun objectArrayOf(vararg objs: Any): Array<in Any> {
            @Suppress("unchecked_cast")
            return (java.lang.reflect.Array.newInstance(objectClass, objs.size) as Array<in Any>).also {
                for (i in 0 until objs.size) {
                    it[i] = objectClass.cast(objs[i])
                }
            }
        }
    }

    fun Any.getArray(methodName: String): Array<*> = javaClass.getMethod(methodName).invoke(this) as Array<*>
}
