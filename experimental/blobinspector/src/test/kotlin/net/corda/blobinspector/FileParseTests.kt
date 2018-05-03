package net.corda.blobinspector

import java.net.URI

import org.junit.Test
import net.corda.testing.common.internal.ProjectStructure.projectRootDir


class FileParseTests {
    @Suppress("UNUSED")
    var localPath : URI = projectRootDir.toUri().resolve(
            "tools/blobinspector/src/test/resources/net/corda/blobinspector")

    fun setupArgsWithFile(path: String)  = Array<String>(5) {
        when (it) {
            0 -> "-m"
            1 -> "file"
            2 -> "-f"
            3 -> path
            4 -> "-d"
            else -> "error"
        }
    }

    private val filesToTest = listOf (
            "FileParseTests.1Int",
            "FileParseTests.2Int",
            "FileParseTests.3Int",
            "FileParseTests.1String",
            "FileParseTests.1Composite",
            "FileParseTests.2Composite",
            "FileParseTests.IntList",
            "FileParseTests.StringList",
            "FileParseTests.MapIntString",
            "FileParseTests.MapIntClass"
            )

    fun testFile(file : String) {
        val path = FileParseTests::class.java.getResource(file)
        val args = setupArgsWithFile(path.toString())

        val handler = getMode(args).let { mode ->
            loadModeSpecificOptions(mode, args)
            BlobHandler.make(mode)
        }

        inspectBlob(handler.config, handler.getBytes())
    }

    @Test
    fun simpleFiles() {
        filesToTest.forEach { testFile(it) }
    }

    @Test
    fun specificTest() {
        testFile(filesToTest[4])
        testFile(filesToTest[5])
        testFile(filesToTest[6])
    }

    @Test
    fun networkParams() {
        val file = "networkParams"
        val path = FileParseTests::class.java.getResource(file)
        val verbose = false

        val args = verbose.let {
            if (it)
                Array(4) { when (it) { 0 -> "-f" ; 1 -> path.toString(); 2 -> "-d"; 3 -> "-vs"; else -> "error" } }
            else
                Array(3) { when (it) { 0 -> "-f" ; 1 -> path.toString(); 2 -> "-d"; else -> "error" } }
        }

        val handler = getMode(args).let { mode ->
            loadModeSpecificOptions(mode, args)
            BlobHandler.make(mode)
        }

        inspectBlob(handler.config, handler.getBytes())

    }

}
