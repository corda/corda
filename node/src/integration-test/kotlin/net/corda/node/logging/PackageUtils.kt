package net.corda.node.logging

import net.corda.core.internal.div
import net.corda.testing.driver.NodeHandle
import java.io.File

fun NodeHandle.logFile(): File = (baseDirectory / "logs").toFile().walk().filter { it.name.startsWith("node-") && it.extension == "log" }.single()