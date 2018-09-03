package net.corda.behave.file

import net.corda.core.internal.div
import java.nio.file.Path
import java.nio.file.Paths

val currentDirectory: Path
    get() = Paths.get(System.getProperty("user.dir"))

// location of Corda distributions and Drivers dependencies
val stagingRoot: Path
    get() = System.getProperty("STAGING_ROOT")?.let { Paths.get(it) } ?: currentDirectory

val doormanConfigDirectory: Path
    get() = currentDirectory / "src" / "main" / "resources" / "doorman"

val tmpDirectory: Path
    get() = System.getProperty("TMPDIR")?.let { Paths.get(it) } ?: Paths.get("/tmp")