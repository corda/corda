package net.corda.node.utilities

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import java.nio.file.Path
import java.nio.file.Paths

fun Config.getHostAndPort(name: String): HostAndPort = HostAndPort.fromString(getString(name))
fun Config.getPath(name: String): Path = Paths.get(getString(name))