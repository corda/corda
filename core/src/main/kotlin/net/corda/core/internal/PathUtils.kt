@file:DeleteForDJVM
package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.deserialize
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileTime
import java.util.stream.Stream
import kotlin.streams.toList

/**
 * Allows you to write code like: Paths.get("someDir") / "subdir" / "filename" but using the Paths API to avoid platform
 * separator problems.
 * @see Path.resolve
 */
operator fun Path.div(other: String): Path = resolve(other)

/**
 * Allows you to write code like: "someDir" / "subdir" / "filename" but using the Paths API to avoid platform
 * separator problems.
 * @see Path.resolve
 */
operator fun String.div(other: String): Path = Paths.get(this) / other

/** @see Files.createFile */
fun Path.createFile(vararg attrs: FileAttribute<*>): Path = Files.createFile(this, *attrs)

/** @see Files.createDirectory */
fun Path.createDirectory(vararg attrs: FileAttribute<*>): Path = Files.createDirectory(this, *attrs)

/** @see Files.createDirectories */
fun Path.createDirectories(vararg attrs: FileAttribute<*>): Path = Files.createDirectories(this, *attrs)

/** @see Files.exists */
fun Path.exists(vararg options: LinkOption): Boolean = Files.exists(this, *options)

/** Copy the file into the target directory using [Files.copy]. */
fun Path.copyToDirectory(targetDir: Path, vararg options: CopyOption): Path {
    require(targetDir.isDirectory()) { "$targetDir is not a directory" }
    /*
     * We must use fileName.toString() here because resolve(Path)
     * will throw ProviderMismatchException if the Path parameter
     * and targetDir have different Path providers, e.g. a file
     * on the filesystem vs an entry in a ZIP file.
     *
     * Path.toString() is assumed safe because fileName should
     * not include any path separator characters.
     */
    val targetFile = targetDir.resolve(fileName.toString())
    Files.copy(this, targetFile, *options)
    return targetFile
}

/** @see Files.copy */
fun Path.copyTo(target: Path, vararg options: CopyOption): Path = Files.copy(this, target, *options)

/** @see Files.move */
fun Path.moveTo(target: Path, vararg options: CopyOption): Path = Files.move(this, target, *options)

/** See overload of [Files.copy] which takes in an [InputStream]. */
fun Path.copyTo(out: OutputStream): Long = Files.copy(this, out)

/** @see Files.isRegularFile */
fun Path.isRegularFile(vararg options: LinkOption): Boolean = Files.isRegularFile(this, *options)

/** @see Files.isReadable */
inline val Path.isReadable: Boolean get() = Files.isReadable(this)

/** @see Files.size */
inline val Path.size: Long get() = Files.size(this)

/** @see Files.getLastModifiedTime */
fun Path.lastModifiedTime(vararg options: LinkOption): FileTime = Files.getLastModifiedTime(this, *options)

/** @see Files.isDirectory */
fun Path.isDirectory(vararg options: LinkOption): Boolean = Files.isDirectory(this, *options)

/**
 * Same as [Files.list] except it also closes the [Stream].
 * @return the output of [block]
 */
inline fun <R> Path.list(block: (Stream<Path>) -> R): R = Files.list(this).use(block)

/** Same as [list] but materialises all the entiries into a list. */
fun Path.list(): List<Path> = list { it.toList() }

/** @see Files.walk */
inline fun <R> Path.walk(maxDepth: Int = Int.MAX_VALUE, vararg options: FileVisitOption, block: (Stream<Path>) -> R): R {
    return Files.walk(this, maxDepth, *options).use(block)
}

/** @see Files.delete */
fun Path.delete(): Unit = Files.delete(this)

/** @see Files.deleteIfExists */
fun Path.deleteIfExists(): Boolean = Files.deleteIfExists(this)

/** Deletes this path (if it exists) and if it's a directory, all its child paths recursively. */
fun Path.deleteRecursively() {
    if (!exists()) return
    Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            file.delete()
            return FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(dir: Path, exception: IOException?): FileVisitResult {
            dir.delete()
            return FileVisitResult.CONTINUE
        }
    })
}

/** @see Files.newOutputStream */
fun Path.outputStream(vararg options: OpenOption): OutputStream = Files.newOutputStream(this, *options)

/** @see Files.newInputStream */
fun Path.inputStream(vararg options: OpenOption): InputStream = Files.newInputStream(this, *options)

/** @see Files.newBufferedReader */
fun Path.reader(charset: Charset = UTF_8): BufferedReader = Files.newBufferedReader(this, charset)

/** @see Files.newBufferedWriter */
fun Path.writer(charset: Charset = UTF_8, vararg options: OpenOption): BufferedWriter {
    return Files.newBufferedWriter(this, charset, *options)
}

/** @see Files.readAllBytes */
fun Path.readAll(): ByteArray = Files.readAllBytes(this)

/** Read in this entire file as a string using the given encoding. */
fun Path.readText(charset: Charset = UTF_8): String = reader(charset).use(Reader::readText)

/** @see Files.write */
fun Path.write(bytes: ByteArray, vararg options: OpenOption): Path = Files.write(this, bytes, *options)

/** Write the given string to this file. */
fun Path.writeText(text: String, charset: Charset = UTF_8, vararg options: OpenOption) {
    writer(charset, *options).use { it.write(text) }
}

/**
 * Same as [inputStream] except it also closes the [InputStream].
 * @return the output of [block]
 */
inline fun <R> Path.read(vararg options: OpenOption, block: (InputStream) -> R): R = inputStream(*options).use(block)

/**
 * Same as [outputStream] except it also closes the [OutputStream].
 * @param createDirs if true then the parent directory of this file is created. Defaults to false.
 * @return the output of [block]
 */
inline fun Path.write(createDirs: Boolean = false, vararg options: OpenOption = emptyArray(), block: (OutputStream) -> Unit) {
    if (createDirs) {
        normalize().parent?.createDirectories()
    }
    outputStream(*options).use(block)
}

/**
 * Same as [Files.lines] except it also closes the [Stream]
 * @return the output of [block]
 */
inline fun <R> Path.readLines(charset: Charset = UTF_8, block: (Stream<String>) -> R): R {
    return Files.lines(this, charset).use(block)
}

/** @see Files.readAllLines */
fun Path.readAllLines(charset: Charset = UTF_8): List<String> = Files.readAllLines(this, charset)

fun Path.writeLines(lines: Iterable<CharSequence>, charset: Charset = UTF_8, vararg options: OpenOption): Path {
    return Files.write(this, lines, charset, *options)
}

/**
 * Read in this file as an AMQP serialised blob of type [T].
 * @see [deserialize]
 */
inline fun <reified T : Any> Path.readObject(): T = readAll().deserialize()

/** Calculate the hash of the contents of this file. */
inline val Path.hash: SecureHash get() = read { it.hash() }
