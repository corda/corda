package net.corda.behave.file

import java.io.File

val currentDirectory: File
    get() = File(System.getProperty("user.dir"))

operator fun File.div(relative: String): File = this.resolve(relative)
