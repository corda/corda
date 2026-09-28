package net.corda.testing.common.internal

import net.corda.core.internal.toPath
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory

object ProjectStructure {
    val projectRootDir: Path = run {
        var dir = javaClass.getResource("/")!!.toPath()
        while (!(dir / ".git").isDirectory()) {
            dir = dir.parent
        }
        dir
    }
}
