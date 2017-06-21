package net.corda.testing

import net.corda.core.div
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ProjectStructure {
    val projectRootDir: Path = run {
        var dir = Paths.get(javaClass.getResource("/").toURI())
        while (!Files.isDirectory(dir / ".git")) {
            dir = dir.parent
        }
        dir
    }
}
