package net.corda.core.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div

class PathUtilsTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test(timeout=300_000)
	fun `deleteRecursively - non-existent path`() {
        val path = tempFolder.root.toPath() / "non-existent"
        path.deleteRecursively()
        assertThat(path).doesNotExist()
    }

    @Test(timeout=300_000)
	fun `deleteRecursively - file`() {
        val file = (tempFolder.root.toPath() / "file").createFile()
        file.deleteRecursively()
        assertThat(file).doesNotExist()
    }

    @Test(timeout=300_000)
	fun `deleteRecursively - empty folder`() {
        val emptyDir = (tempFolder.root.toPath() / "empty").createDirectories()
        emptyDir.deleteRecursively()
        assertThat(emptyDir).doesNotExist()
    }

    @Test(timeout=300_000)
	fun `deleteRecursively - dir with single file`() {
        val dir = (tempFolder.root.toPath() / "dir").createDirectories()
        (dir / "file").createFile()
        dir.deleteRecursively()
        assertThat(dir).doesNotExist()
    }

    @Test(timeout=300_000)
	fun `deleteRecursively - nested single file`() {
        val dir = (tempFolder.root.toPath() / "dir").createDirectories()
        val dir2 = (dir / "dir2").createDirectories()
        (dir2 / "file").createFile()
        dir.deleteRecursively()
        assertThat(dir).doesNotExist()
    }

    @Test(timeout=300_000)
	fun `deleteRecursively - complex`() {
        val dir = (tempFolder.root.toPath() / "dir").createDirectories()
        (dir / "file1").createFile()
        val dir2 = (dir / "dir2").createDirectories()
        (dir2 / "file2").createFile()
        (dir2 / "file3").createFile()
        (dir2 / "dir3").createDirectories()
        dir.deleteRecursively()
        assertThat(dir).doesNotExist()
    }

    @Test(timeout=300_000)
	fun `copyToDirectory - copy into zip directory`() {
        val source: Path = tempFolder.newFile("source.txt").let {
            it.writeText("Example Text")
            it.toPath()
        }
        val target = tempFolder.root.toPath() / "target.zip"
        FileSystems.newFileSystem(URI.create("jar:${target.toUri()}"), mapOf("create" to "true")).use { fs ->
            val dir = fs.getPath("dir").createDirectories()
            val result = source.copyToDirectory(dir)
            assertThat(result)
                .isRegularFile()
                .hasParent(dir)
                .hasSameContentAs(source)
        }
    }
}