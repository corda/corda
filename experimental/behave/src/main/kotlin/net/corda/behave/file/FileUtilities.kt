package net.corda.behave.file

import java.io.File

val currentDirectory: File
    get() = File(System.getProperty("user.dir"))

val scriptsDirectory: File
    get() = currentDirectory / "src/scripts"

val doormanScriptsDirectory: File
    get() = scriptsDirectory / "doorman"

val doormanConfigDirectory: File
    get() = currentDirectory / "src/main/resources/doorman"

operator fun File.div(relative: String): File = this.resolve(relative)
