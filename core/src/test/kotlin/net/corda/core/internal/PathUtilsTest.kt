package net.corda.core.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PathUtilsTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `deleteRecursively - non-existent path`() {
        val path = tempFolder.root.toPath() / "non-existent"
        path.deleteRecursively()
        assertThat(path).doesNotExist()
    }

    @Test
    fun `deleteRecursively - file`() {
        val file = (tempFolder.root.toPath() / "file").createFile()
        file.deleteRecursively()
        assertThat(file).doesNotExist()
    }

    @Test
    fun `deleteRecursively - empty folder`() {
        val emptyDir = (tempFolder.root.toPath() / "empty").createDirectories()
        emptyDir.deleteRecursively()
        assertThat(emptyDir).doesNotExist()
    }

    @Test
    fun `deleteRecursively - dir with single file`() {
        val dir = (tempFolder.root.toPath() / "dir").createDirectories()
        (dir / "file").createFile()
        dir.deleteRecursively()
        assertThat(dir).doesNotExist()
    }

    @Test
    fun `deleteRecursively - nested single file`() {
        val dir = (tempFolder.root.toPath() / "dir").createDirectories()
        val dir2 = (dir / "dir2").createDirectories()
        (dir2 / "file").createFile()
        dir.deleteRecursively()
        assertThat(dir).doesNotExist()
    }

    @Test
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
}