package net.corda.errorPageBuilder

import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Path

class ErrorToolUtilities {
    companion object {
        fun checkDirectory(dir: Path?, expectedContents: String) : Path {
            return dir?.also {
                require(Files.exists(it)) {
                    "Directory $it does not exist. Please specify a valid direction for $expectedContents"
                }
            } ?: throw IllegalArgumentException("No location specified for $expectedContents. Please specify a directory for $expectedContents.")
        }
    }
}