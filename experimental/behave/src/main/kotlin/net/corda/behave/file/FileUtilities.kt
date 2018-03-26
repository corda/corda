package net.corda.behave.file

import java.io.File

val currentDirectory: File
    get() = File(System.getProperty("user.dir"))

// location of Corda distributions and Drivers dependencies
val stagingRoot: File
//    get() = if (System.getProperty("STAGING_ROOT") != null)
//                File(System.getProperty("STAGING_ROOT"))
//            else currentDirectory
    get() = File("/Users/josecoll/IdeaProjects/corda-reviews/experimental/behave")

val scriptsDirectory: File
    get() = currentDirectory / "src/scripts"

val doormanScriptsDirectory: File
    get() = scriptsDirectory / "doorman"

val doormanConfigDirectory: File
    get() = currentDirectory / "src/main/resources/doorman"

operator fun File.div(relative: String): File = this.resolve(relative)
