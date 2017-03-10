package net.corda.demobench.model

import java.nio.file.Path

interface HasPlugins {
    val pluginDir: Path
}
