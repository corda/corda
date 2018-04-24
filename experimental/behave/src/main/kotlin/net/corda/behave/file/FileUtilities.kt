package net.corda.behave.file

import java.nio.file.Path
import java.nio.file.Paths

val currentDirectory: Path
    get() = Paths.get(System.getProperty("user.dir"))

// location of Corda distributions and Drivers dependencies
val stagingRoot: Path
    get() = System.getProperty("STAGING_ROOT")?.let { Paths.get(it) } ?: currentDirectory
