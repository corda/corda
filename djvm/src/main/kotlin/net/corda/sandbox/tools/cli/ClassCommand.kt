package net.corda.sandbox.tools.cli

import net.corda.sandbox.SandboxConfiguration
import net.corda.sandbox.analysis.AnalysisConfiguration
import net.corda.sandbox.analysis.Whitelist
import net.corda.sandbox.execution.*
import net.corda.sandbox.references.ClassModule
import net.corda.sandbox.source.ClassSource
import net.corda.sandbox.source.SourceClassLoader
import net.corda.sandbox.tools.Utilities.find
import net.corda.sandbox.tools.Utilities.onEmpty
import net.corda.sandbox.tools.Utilities.userClassPath
import net.corda.sandbox.utilities.Discovery
import org.objectweb.asm.ClassReader
import picocli.CommandLine.Option
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Suppress("KDocMissingDocumentation", "MemberVisibilityCanBePrivate")
abstract class ClassCommand : CommandBase() {

    @Option(
            names = ["-p", "--profile"],
            description = ["The execution profile to use (DEFAULT, UNLIMITED, DISABLE_BRANCHING or DISABLE_THROWS)."]
    )
    var profile: ExecutionProfile = ExecutionProfile.DEFAULT

    @Option(names = ["--ignore-rules"], description = ["Disable all rules pertaining to the sandbox."])
    var ignoreRules: Boolean = false

    @Option(names = ["--ignore-emitters"], description = ["Disable all emitters defined for the sandbox."])
    var ignoreEmitters: Boolean = false

    @Option(names = ["--ignore-definition-providers"], description = ["Disable all definition providers."])
    var ignoreDefinitionProviders: Boolean = false

    @Option(
            names = ["-w", "--whitelist"],
            description = ["Override the default whitelist. Use provided whitelist instead. If NONE is provided, the " +
                    "whitelist will be ignored. If ALL is provided, all references will be whitelisted. LANG can be " +
                    "used to only whitelist select classes and their members from the java.lang package."]
    )
    var whitelist: Path? = null

    @Option(names = ["-c", "--classpath"], description = ["Additions to the default class path."], split = ":")
    var classPath: Array<Path> = emptyArray()

    @Option(names = ["--disable-tracing"], description = ["Disable tracing in the sandbox."])
    var disableTracing: Boolean = false

    @Option(names = ["--analyze-pinned-classes"], description = ["Analyze all pinned classes as well, unless covered " +
            "by the provided whitelist."])
    var analyzePinnedClasses: Boolean = false

    @Option(names = ["--analyze-annotations"], description = ["Analyze all annotations even if they are not " +
            "explicitly referenced."])
    var analyzeAnnotations: Boolean = false

    @Option(
            names = ["--prefix-filters"],
            description = ["Only record messages matching one of the provided prefixes."],
            split = ":"
    )
    var prefixFilters: Array<String> = emptyArray()

    abstract val filters: Array<String>

    private val classModule = ClassModule()

    private lateinit var classLoader: ClassLoader

    protected var executor = SandboxExecutor<Any, Any>()

    private var derivedWhitelist: Whitelist = Whitelist.DEFAULT

    abstract fun processClasses(classes: List<Class<*>>)

    open fun printSuccess(classes: List<Class<*>>) {}

    override fun validateArguments() = filters.isNotEmpty()

    override fun handleCommand(): Boolean {
        derivedWhitelist = whitelistFromPath(whitelist)
        val configuration = getConfiguration(derivedWhitelist)
        classLoader = SourceClassLoader(getClasspath(), configuration.analysisConfiguration.classResolver)
        createExecutor(configuration)

        val classes = discoverClasses(filters).onEmpty {
            throw Exception("Could not find any classes matching ${filters.joinToString(" ")} on the " +
                    "system class path")
        }

        return try {
            processClasses(classes)
            printSuccess(classes)
            true
        } catch (exception: Throwable) {
            printException(exception)
            if (exception is SandboxException) {
                printCosts(exception.executionSummary.costs)
            }
            false
        }
    }

    protected fun printCosts(costs: Map<String, Long>) {
        if (disableTracing) {
            return
        }
        printInfo("Runtime Cost Summary:")
        for ((name, cost) in costs) {
            printInfo(" - $name = @|yellow $cost|@")
        }
        printInfo()
    }

    private fun discoverClasses(filters: Array<String>): List<Class<*>> {
        return findDiscoverableRunnables(filters) + findReferencedClasses(filters) + findClassesInJars(filters)
    }

    private fun findDiscoverableRunnables(filters: Array<String>): List<Class<*>> {
        val classes = find<DiscoverableRunnable>()
        val applicableFilters = filters
                .filter { !isJarFile(it) && !isFullClassName(it) }
        val filteredClasses = applicableFilters
                .flatMap { filter ->
                    classes.filter { clazz ->
                        clazz.name.contains(filter, true)
                    }
                }

        if (applicableFilters.isNotEmpty() && filteredClasses.isEmpty()) {
            throw Exception("Could not find any classes implementing ${SandboxedRunnable::class.java.simpleName} " +
                    "whose name matches '${applicableFilters.joinToString(" ")}'")
        }

        if (applicableFilters.isNotEmpty()) {
            printVerbose("Class path: $userClassPath")
            printVerbose("Discovered runnables on the class path:")
            for (clazz in classes) {
                printVerbose(" - ${clazz.name}")
            }
            printVerbose()
        }
        return filteredClasses
    }

    private fun findReferencedClasses(filters: Array<String>): List<Class<*>> {
        return filters.filter { !isJarFile(it) && isFullClassName(it) }.map {
            val className = classModule.getFormattedClassName(it)
            printVerbose("Looking up class $className...")
            lookUpClass(className)
        }
    }

    private fun findClassesInJars(filters: Array<String>): List<Class<*>> {
        return filters.filter { isJarFile(it) }.flatMap { jarFile ->
            mutableListOf<Class<*>>().apply {
                ClassSource.fromPath(Paths.get(jarFile)).getStreamIterator().forEach {
                    val reader = ClassReader(it)
                    val className = classModule.getFormattedClassName(reader.className)
                    printVerbose("Looking up class $className in $jarFile...")
                    this.add(lookUpClass(className))
                }
            }
        }
    }

    private fun lookUpClass(className: String): Class<*> {
        return try {
            classLoader.loadClass(className)
        } catch (exception: NoClassDefFoundError) {
            val reference = exception.message?.let {
                "referenced class ${classModule.getFormattedClassName(it)} in "
            } ?: ""
            throw Exception("Unable to load ${reference}type $className (is it present on the class path?)")
        } catch (exception: TypeNotPresentException) {
            val reference = exception.typeName() ?: ""
            throw Exception("Type $reference not present in class $className")
        } catch (exception: Throwable) {
            throw Exception("Unable to load type $className (is it present on the class path?)")
        }
    }

    private fun isJarFile(filter: String) = Files.exists(Paths.get(filter)) && filter.endsWith(".jar", true)

    private fun isFullClassName(filter: String) = filter.count { it == '.' } > 0

    private fun getClasspath() =
            classPath.toList() + filters.filter { it.endsWith(".jar", true) }.map { Paths.get(it) }

    private fun getConfiguration(whitelist: Whitelist): SandboxConfiguration {
        return SandboxConfiguration.of(
                profile = profile,
                rules = if (ignoreRules) { emptyList() } else { Discovery.find() },
                emitters = ignoreEmitters.emptyListIfTrueOtherwiseNull(),
                definitionProviders = if(ignoreDefinitionProviders) { emptyList() } else { Discovery.find() },
                enableTracing = !disableTracing,
                analysisConfiguration = AnalysisConfiguration(
                        whitelist = whitelist,
                        minimumSeverityLevel = level,
                        classPath = getClasspath(),
                        analyzePinnedClasses = analyzePinnedClasses,
                        analyzeAnnotations = analyzeAnnotations,
                        prefixFilters = prefixFilters.toList()
                )
        )
    }

    private fun createExecutor(configuration: SandboxConfiguration) {
        executor = SandboxExecutor(configuration)
    }

    private fun <T> Boolean.emptyListIfTrueOtherwiseNull(): List<T>? = when (this) {
        true -> emptyList()
        false -> null
    }

}