// Implement the new post-1.2 APIs which are used by core and serialization
@file:Suppress("unused", "SpreadOperator", "NOTHING_TO_INLINE")

package kotlin.io.path

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute

inline operator fun Path.div(other: String): Path = this.resolve(other)

fun Path.listDirectoryEntries(glob: String = "*"): List<Path> = Files.newDirectoryStream(this, glob).use { it.toList() }

inline fun Path.createDirectories(vararg attributes: FileAttribute<*>): Path = Files.createDirectories(this, *attributes)

inline fun Path.deleteIfExists(): Boolean = Files.deleteIfExists(this)

inline fun Path.exists(vararg options: LinkOption): Boolean = Files.exists(this, *options)

inline fun Path.inputStream(vararg options: OpenOption): InputStream = Files.newInputStream(this, *options)

inline fun Path.outputStream(vararg options: OpenOption): OutputStream = Files.newOutputStream(this, *options)

inline fun Path.isDirectory(vararg options: LinkOption): Boolean = Files.isDirectory(this, *options)

inline fun Path.isSymbolicLink(): Boolean = Files.isSymbolicLink(this)

inline fun Path.readSymbolicLink(): Path = Files.readSymbolicLink(this)

val Path.name: String
    get() = fileName?.toString().orEmpty()

inline fun Path.readBytes(): ByteArray = Files.readAllBytes(this)
