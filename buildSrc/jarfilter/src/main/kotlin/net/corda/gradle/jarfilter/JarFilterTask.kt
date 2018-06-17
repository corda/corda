package net.corda.gradle.jarfilter

import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.StandardCopyOption.*
import java.util.zip.Deflater.BEST_COMPRESSION
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.max

@Suppress("Unused", "MemberVisibilityCanBePrivate")
open class JarFilterTask : DefaultTask() {
    private companion object {
        private const val DEFAULT_MAX_PASSES = 5
    }

    private val _jars: ConfigurableFileCollection = project.files()
    @get:SkipWhenEmpty
    @get:InputFiles
    val jars: FileCollection get() = _jars

    fun setJars(inputs: Any?) {
        val files = inputs ?: return
        _jars.setFrom(files)
    }

    fun jars(inputs: Any?) = setJars(inputs)

    @get:Input
    protected var forDelete: Set<String> = emptySet()

    @get:Input
    protected var forStub: Set<String> = emptySet()

    @get:Input
    protected var forRemove: Set<String> = emptySet()

    fun annotations(assign: Closure<List<String>>) {
        assign.call()
    }

    @get:Console
    var verbose: Boolean = false

    @get:Input
    var maxPasses: Int = DEFAULT_MAX_PASSES
        set(value) {
            field = max(value, 1)
        }

    @get:Input
    var preserveTimestamps: Boolean = true

    private var _outputDir = project.buildDir.resolve("filtered-libs")
    @get:Internal
    val outputDir: File get() = _outputDir

    fun setOutputDir(d: File?) {
        val dir = d ?: return
        _outputDir = dir
    }

    fun outputDir(dir: File?) = setOutputDir(dir)

    @get:OutputFiles
    val filtered: FileCollection get() = project.files(jars.files.map(this::toFiltered))

    private fun toFiltered(source: File) = File(outputDir, source.name.replace(JAR_PATTERN, "-filtered\$1"))

    @TaskAction
    fun filterJars() {
        logger.info("JarFiltering:")
        if (forDelete.isNotEmpty()) {
            logger.info("- Elements annotated with one of '{}' will be deleted", forDelete.joinToString())
        }
        if (forStub.isNotEmpty()) {
            logger.info("- Methods annotated with one of '{}' will be stubbed out", forStub.joinToString())
        }
        if (forRemove.isNotEmpty()) {
            logger.info("- Annotations '{}' will be removed entirely", forRemove.joinToString())
        }
        checkDistinctAnnotations()
        try {
            jars.forEach { jar ->
                logger.info("Filtering {}", jar)
                Filter(jar).run()
            }
        } catch (e: Exception) {
            rethrowAsUncheckedException(e)
        }
    }

    private fun checkDistinctAnnotations() {
        logger.info("Checking that all annotations are distinct.")
        val annotations = forRemove.toHashSet().apply {
            addAll(forDelete)
            addAll(forStub)
            removeAll(forRemove)
        }
        forDelete.forEach {
            if (!annotations.remove(it)) {
                failWith("Annotation '$it' also appears in JarFilter 'forDelete' section")
            }
        }
        forStub.forEach {
            if (!annotations.remove(it)) {
                failWith("Annotation '$it' also appears in JarFilter 'forStub' section")
            }
        }
        if (!annotations.isEmpty()) {
            failWith("SHOULDN'T HAPPEN - Martian annotations! '${annotations.joinToString()}'")
        }
    }

    private fun failWith(message: String): Nothing = throw InvalidUserDataException(message)

    private fun verbose(format: String, vararg objects: Any) {
        if (verbose) {
            logger.info(format, *objects)
        }
    }

    private inner class Filter(inFile: File) {
        private val unwantedClasses: MutableSet<String> = mutableSetOf()
        private val source: Path = inFile.toPath()
        private val target: Path = toFiltered(inFile).toPath()

        init {
            Files.deleteIfExists(target)
        }

        fun run() {
            logger.info("Filtering to: {}", target)
            var input = source

            try {
                var passes = 1
                while (true) {
                    verbose("Pass {}", passes)
                    val isModified = Pass(input).use { it.run() }

                    if (!isModified) {
                        logger.info("No changes after latest pass - exiting.")
                        break
                    } else if (++passes > maxPasses) {
                        break
                    }

                    input = Files.move(
                        target, Files.createTempFile(target.parent, "filter-", ".tmp"), REPLACE_EXISTING)
                    verbose("New input JAR: {}", input)
                }
            } catch (e: Exception) {
                logger.error("Error filtering '{}' elements from {}", ArrayList(forRemove).apply { addAll(forDelete); addAll(forStub) }, input)
                throw e
            }
        }

        private inner class Pass(input: Path): Closeable {
            /**
             * Use [ZipFile] instead of [java.util.jar.JarInputStream] because
             * JarInputStream consumes MANIFEST.MF when it's the first or second entry.
             */
            private val inJar = ZipFile(input.toFile())
            private val outJar = ZipOutputStream(Files.newOutputStream(target))
            private var isModified = false

            @Throws(IOException::class)
            override fun close() {
                inJar.use {
                    outJar.close()
                }
            }

            fun run(): Boolean {
                outJar.setLevel(BEST_COMPRESSION)
                outJar.setComment(inJar.comment)

                for (entry in inJar.entries()) {
                    val entryData = inJar.getInputStream(entry)

                    if (entry.isDirectory || !entry.name.endsWith(".class")) {
                        // This entry's byte contents have not changed,
                        // but may still need to be recompressed.
                        outJar.putNextEntry(entry.copy().withFileTimestamps(preserveTimestamps))
                        entryData.copyTo(outJar)
                    } else {
                        val classData = transform(entryData.readBytes())
                        if (classData.isNotEmpty()) {
                            // This entry's byte contents have almost certainly
                            // changed, and will be stored compressed.
                            outJar.putNextEntry(entry.asCompressed().withFileTimestamps(preserveTimestamps))
                            outJar.write(classData)
                        }
                    }
                }
                return isModified
            }

            private fun transform(inBytes: ByteArray): ByteArray {
                var reader = ClassReader(inBytes)
                var writer = ClassWriter(COMPUTE_MAXS)
                var transformer = ClassTransformer(
                    visitor = writer,
                    logger = logger,
                    removeAnnotations = toDescriptors(forRemove),
                    deleteAnnotations = toDescriptors(forDelete),
                    stubAnnotations = toDescriptors(forStub),
                    unwantedClasses = unwantedClasses
                )

                /*
                 * First pass: This might not find anything to remove!
                 */
                reader.accept(transformer, 0)

                if (transformer.isUnwantedClass || transformer.hasUnwantedElements) {
                    isModified = true

                    do {
                        /*
                         * Rewrite the class without any of the unwanted elements.
                         * If we're deleting the class then make sure we identify all of
                         * its inner classes too, for the next filter pass to delete.
                         */
                        reader = ClassReader(writer.toByteArray())
                        writer = ClassWriter(COMPUTE_MAXS)
                        transformer = transformer.recreate(writer)
                        reader.accept(transformer, 0)
                    } while (!transformer.isUnwantedClass && transformer.hasUnwantedElements)
                }

                return if (transformer.isUnwantedClass) {
                    // The entire class is unwanted, so don't write it out.
                    logger.info("Deleting class {}", transformer.className)
                    byteArrayOf()
                } else {
                    writer.toByteArray()
                }
            }
        }
    }
}
