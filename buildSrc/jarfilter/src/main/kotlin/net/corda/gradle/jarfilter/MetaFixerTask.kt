package net.corda.gradle.jarfilter

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.*
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.util.zip.Deflater.BEST_COMPRESSION
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Suppress("Unused", "MemberVisibilityCanBePrivate")
open class MetaFixerTask : DefaultTask() {
    private val _jars: ConfigurableFileCollection = project.files()
    @get:SkipWhenEmpty
    @get:InputFiles
    val jars: FileCollection
        get() = _jars

    fun setJars(inputs: Any?) {
        val files = inputs ?: return
        _jars.setFrom(files)
    }

    fun jars(inputs: Any?) = setJars(inputs)

    private var _outputDir = project.buildDir.resolve("metafixer-libs")
    @get:Internal
    val outputDir: File
        get() = _outputDir

    fun setOutputDir(d: File?) {
        val dir = d ?: return
        _outputDir = dir
    }

    fun outputDir(dir: File?) = setOutputDir(dir)

    private var _suffix: String = "-metafixed"
    @get:Input
    val suffix: String get() = _suffix

    fun setSuffix(input: String?) {
        _suffix = input ?: return
    }

    fun suffix(suffix: String?) = setSuffix(suffix)

    @get:Input
    var preserveTimestamps: Boolean = true

    @TaskAction
    fun fixMetadata() {
        logger.info("Fixing Kotlin @Metadata")
        try {
            jars.forEach { jar ->
                logger.info("Reading from {}", jar)
                MetaFix(jar).use { it.run() }
            }
        } catch (e: Exception) {
            rethrowAsUncheckedException(e)
        }
    }

    @get:OutputFiles
    val metafixed: FileCollection get() = project.files(jars.files.map(this::toMetaFixed))

    private fun toMetaFixed(source: File) = File(outputDir, source.name.replace(JAR_PATTERN, "$suffix\$1"))

    private inner class MetaFix(inFile: File) : Closeable {
        /**
         * Use [ZipFile] instead of [java.util.jar.JarInputStream] because
         * JarInputStream consumes MANIFEST.MF when it's the first or second entry.
         */
        private val target: Path = toMetaFixed(inFile).toPath()
        private val inJar = ZipFile(inFile)
        private val outJar: ZipOutputStream

        init {
            // Default options for newOutputStream() are CREATE, TRUNCATE_EXISTING.
            outJar = ZipOutputStream(Files.newOutputStream(target)).apply {
                setLevel(BEST_COMPRESSION)
            }
        }

        @Throws(IOException::class)
        override fun close() {
            inJar.use {
                outJar.close()
            }
        }

        fun run() {
            logger.info("Writing to {}", target)
            outJar.setComment(inJar.comment)

            for (entry in inJar.entries()) {
                val entryData = inJar.getInputStream(entry)

                if (entry.isDirectory || !entry.name.endsWith(".class")) {
                    // This entry's byte contents have not changed,
                    // but may still need to be recompressed.
                    outJar.putNextEntry(entry.copy().withFileTimestamps(preserveTimestamps))
                    entryData.copyTo(outJar)
                } else {
                    // This entry's byte contents have almost certainly
                    // changed, and will be stored compressed.
                    val classData = entryData.readBytes().fixMetadata(logger)
                    outJar.putNextEntry(entry.asCompressed().withFileTimestamps(preserveTimestamps))
                    outJar.write(classData)
                }
            }
        }
    }
}

fun ByteArray.fixMetadata(logger: Logger): ByteArray = execute({ writer -> MetaFixerVisitor(writer, logger) })
