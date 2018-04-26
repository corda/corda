package net.corda.testing.common.internal

import net.corda.core.internal.div
import net.corda.core.internal.isDirectory
import net.corda.core.internal.toPath
import java.nio.file.Path

object ProjectStructure {
    val projectRootDir: Path = run {
        var dir = javaClass.getResource("/").toPath()
        while (!(dir / ".git").isDirectory()) {
            dir = dir.parent
        }
        dir
    }
}
