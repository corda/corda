package net.corda.build.legacykotlin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations

import javax.inject.Inject

/**
 * Describes an old Kotlin compiler which is used to build the external verifier for transactions whose contracts were compiled with that
 * version of Kotlin (see `ContractGeneration` in core).
 *
 * The Kotlin Gradle plugin on the build classpath is the current one, and only one version can be loaded, so legacy versions are compiled by
 * invoking the old compiler directly in a forked JVM. These old compilers are not able to run on the current build JDK (they fail to parse
 * newer JDK version strings), so the fork uses [compilerJdkVersion] via Gradle's toolchain support. The output bytecode targets [jvmTarget]
 * and runs on the current JDK.
 *
 * To support a new legacy Kotlin version, add a new constant here and a corresponding `corda.kotlin-<version>.gradle` precompiled script
 * plugin which calls [apply].
 */
enum LegacyKotlinVersion {
    KOTLIN_1_2("1.2.71", "1.2", "1.8", 17, "kotlin-stdlib-jdk8", ["-Xjvm-default=compatibility"]),
    KOTLIN_1_9("1.9.20", "1.9", "17", 17, "kotlin-stdlib", ["-Xjvm-default=all-compatibility"])

    /** The exact version of the Kotlin compiler and stdlib */
    final String compilerVersion
    /** The Kotlin language and API version */
    final String languageVersion
    /** The bytecode target of the compiled code */
    final String jvmTarget
    /** The JDK used to run the old compiler */
    final int compilerJdkVersion
    final String stdlibModule
    final List<String> extraArgs

    LegacyKotlinVersion(String compilerVersion,
                        String languageVersion,
                        String jvmTarget,
                        int compilerJdkVersion,
                        String stdlibModule,
                        List<String> extraArgs) {
        this.compilerVersion = compilerVersion
        this.languageVersion = languageVersion
        this.jvmTarget = jvmTarget
        this.compilerJdkVersion = compilerJdkVersion
        this.stdlibModule = stdlibModule
        this.extraArgs = extraArgs
    }

    /**
     * Apply the legacy Kotlin compilation to the given project. Each source set gets a `src/<name>/kotlin` source directory set, which is
     * compiled by a `compile<Name>Kotlin` task before the Java sources.
     */
    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)

        project.extensions.extraProperties.set("legacy_kotlin_version", compilerVersion)
        // Kept for existing build scripts
        project.extensions.extraProperties.set("kotlin_${languageVersion.replace('.', '_')}_version".toString(), compilerVersion)
        project.dependencies.add("implementation", "org.jetbrains.kotlin:$stdlibModule:$compilerVersion".toString())

        def kotlinCompiler = project.configurations.create("kotlinCompiler") {
            it.canBeConsumed = false
            it.canBeResolved = true
        }
        project.dependencies.add(kotlinCompiler.name, "org.jetbrains.kotlin:kotlin-compiler:$compilerVersion".toString())

        // Gradle configures closures delegate-first, so copy the fields into locals to avoid them resolving to task properties.
        def kotlinVersion = compilerVersion
        def kotlinLanguageVersion = languageVersion
        def kotlinJvmTarget = jvmTarget
        def kotlinExtraArgs = extraArgs
        def toolchains = project.extensions.getByType(JavaToolchainService)
        def compilerLauncher = toolchains.launcherFor {
            it.languageVersion.set(JavaLanguageVersion.of(compilerJdkVersion))
        }

        project.extensions.getByType(JavaPluginExtension).sourceSets.configureEach { SourceSet sourceSet ->
            def kotlinSource = project.objects.sourceDirectorySet("kotlin", "${sourceSet.name} Kotlin source")
            kotlinSource.filter.include("**/*.java", "**/*.kt")
            kotlinSource.srcDir(project.file("src/${sourceSet.name}/kotlin"))
            sourceSet.extensions.add(SourceDirectorySet, "kotlin", kotlinSource)
            sourceSet.allJava.source(kotlinSource)
            sourceSet.allSource.source(kotlinSource)

            def taskSourceSetName = sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME ? "" : sourceSet.name.capitalize()
            def compileKotlin = project.tasks.register("compile${taskSourceSetName}Kotlin", LegacyKotlinCompile) { task ->
                task.description = "Compiles the ${sourceSet.name} Kotlin source with Kotlin $kotlinVersion"
                task.compilerClasspath.from(kotlinCompiler)
                task.javaLauncher.set(compilerLauncher)
                // Paradoxically, the Java sources are also required by the Kotlin compiler. This is so that it can correctly resolve any
                // references the Kotlin code makes to Java code.
                task.sources.from(kotlinSource.matching { it.include("**/*.kt") }, sourceSet.allJava)
                task.classpath.from(sourceSet.compileClasspath)
                task.languageVersion.set(kotlinLanguageVersion)
                task.jvmTarget.set(kotlinJvmTarget)
                task.extraArgs.set(kotlinExtraArgs)
                task.destinationDirectory.set(project.layout.buildDirectory.dir("classes/kotlin/${sourceSet.name}"))
            }
            sourceSet.output.dir(compileKotlin.flatMap { it.destinationDirectory })

            // Compiling the Java code needs the compiled Kotlin code first
            project.tasks.named(sourceSet.compileJavaTaskName, JavaCompile) { task ->
                task.classpath = task.classpath + project.files(compileKotlin.flatMap { it.destinationDirectory })
            }
        }
    }
}

@CacheableTask
abstract class LegacyKotlinCompile extends DefaultTask {
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getSources()

    @Classpath
    abstract ConfigurableFileCollection getClasspath()

    @Classpath
    abstract ConfigurableFileCollection getCompilerClasspath()

    @Input
    abstract Property<String> getLanguageVersion()

    @Input
    abstract Property<String> getJvmTarget()

    @Input
    abstract ListProperty<String> getExtraArgs()

    @Nested
    abstract Property<JavaLauncher> getJavaLauncher()

    @OutputDirectory
    abstract DirectoryProperty getDestinationDirectory()

    @Inject
    abstract ExecOperations getExecOperations()

    @Inject
    abstract FileSystemOperations getFileSystemOperations()

    @TaskAction
    void compile() {
        def destination = destinationDirectory.get().asFile
        fileSystemOperations.delete { it.delete(destination) }
        destination.mkdirs()

        def args = [
                "-jvm-target", jvmTarget.get(),
                "-language-version", languageVersion.get(),
                "-api-version", languageVersion.get(),
                "-java-parameters",
                "-no-stdlib",
                "-Xallow-kotlin-package",  // We may have copies of stdlib APIs (see `core-1.2`)
                "-cp", classpath.asPath,
                "-d", destination.absolutePath
        ]
        args.addAll(extraArgs.get())
        args.addAll(sources.files.collect { it.absolutePath })

        logger.info("Kotlin compiler args: {}", args)

        execOperations.javaexec { spec ->
            spec.executable = javaLauncher.get().executablePath.asFile.absolutePath
            spec.classpath = compilerClasspath
            spec.mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            spec.maxHeapSize = "2g"
            spec.args(args)
        }
    }
}
