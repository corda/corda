package net.corda.blobinspector

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.test.assertFalse

class ModeParse {
    @Test
    fun fileIsSetToFile() {
        val opts1 = Array<String>(2) {
            when (it) {
                0 -> "-m"
                1 -> "file"
                else -> "error"
            }
        }

        assertEquals(Mode.file, getMode(opts1).mode)
    }

    @Test
    fun nothingIsSetToFile() {
        val opts1 = Array<String>(0) { "" }

        assertEquals(Mode.file, getMode(opts1).mode)
    }

    @Test
    fun filePathIsSet() {
        val opts1 = Array<String>(4) {
            when (it) {
                0 -> "-m"
                1 -> "file"
                2 -> "-f"
                3 -> "path/to/file"
                else -> "error"
            }
        }

        val config = getMode(opts1)
        assertTrue (config is FileConfig)
        assertEquals(Mode.file, config.mode)
        assertEquals("unset", (config as FileConfig).file)

        loadModeSpecificOptions(config, opts1)

        assertEquals("path/to/file", config.file)
    }

    @Test
    fun schemaIsSet() {
        Array(2) { when (it) { 0 -> "-f"; 1 -> "path/to/file"; else -> "error"  } }.let { options ->
            getMode(options).apply {
                loadModeSpecificOptions(this, options)
                assertFalse (schema)
            }
        }

        Array(3) { when (it) { 0 -> "--schema"; 1 -> "-f"; 2 -> "path/to/file"; else -> "error" }  }.let {
            getMode(it).apply {
                loadModeSpecificOptions(this, it)
                assertTrue (schema)
            }
        }

        Array(3) { when (it) { 0 -> "-f"; 1 -> "path/to/file"; 2 -> "-s"; else -> "error" }  }.let {
            getMode(it).apply {
                loadModeSpecificOptions(this, it)
                assertTrue (schema)
            }
        }

    }


}