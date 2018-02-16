package net.corda.behave.file

import java.io.File

val currentDirectory: File
    get() = File(System.getProperty("user.dir"))

// location of Corda distributions and Drivers dependencies
val stagingRoot: File
    get() = if (System.getProperty("STAGING_ROOT") != null)
                File(System.getProperty("STAGING_ROOT"))
            else currentDirectory

operator fun File.div(relative: String): File = this.resolve(relative)
