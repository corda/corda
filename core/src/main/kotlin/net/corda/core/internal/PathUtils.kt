package net.corda.core.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.deserialize
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.CopyOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.readSymbolicLink

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
    val targetFile = targetDir / name
    Files.copy(this, targetFile, *options)
    return targetFile
}

/** See overload of [Files.copy] which takes in an [InputStream]. */
fun Path.copyTo(out: OutputStream): Long = Files.copy(this, out)

/** @see Files.readAttributes */
fun Path.attributes(vararg options: LinkOption): BasicFileAttributes = Files.readAttributes(this, BasicFileAttributes::class.java, *options)

/** Deletes this path (if it exists) and if it's a directory, all its child paths recursively. */
fun Path.deleteRecursively() {
    if (!exists()) return
    Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            file.deleteIfExists()
            return FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(dir: Path, exception: IOException?): FileVisitResult {
            dir.deleteIfExists()
            return FileVisitResult.CONTINUE
        }
    })
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
 * Read in this file as an AMQP serialised blob of type [T].
 * @see [deserialize]
 */
inline fun <reified T : Any> Path.readObject(): T = readBytes().deserialize()

/** Calculate the hash of the contents of this file. */
inline val Path.hash: SecureHash.SHA256 get() = read { it.hash() }

/* Check if the Path is symbolic link */
fun Path.safeSymbolicRead(): Path = if (isSymbolicLink()) readSymbolicLink() else this
