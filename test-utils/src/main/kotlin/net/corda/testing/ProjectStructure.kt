package net.corda.testing

import net.corda.core.internal.div
import net.corda.core.internal.isDirectory
import java.nio.file.Path
import java.nio.file.Paths

object ProjectStructure {
    val projectRootDir: Path = run {
        var dir = Paths.get(javaClass.getResource("/").toURI())
        while (!(dir / ".git").isDirectory()) {
            dir = dir.parent
        }
        dir
    }
}
