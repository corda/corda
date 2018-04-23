/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.model

import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType.ERROR
import javafx.stage.Stage
import net.corda.core.internal.exists
import tornadofx.*
import java.nio.file.Path
import java.nio.file.Paths

class JVMConfig : Controller() {

    val userHome: Path = Paths.get(System.getProperty("user.home")).toAbsolutePath()
    val dataHome: Path = userHome.resolve("demobench")
    val capsuleHome: Path = dataHome.resolve(".capsule")
    val javaPath: Path = Paths.get(System.getProperty("java.home"), "bin", "java")
    val applicationDir: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()

    init {
        log.info("Java executable: $javaPath")
    }

    fun commandFor(jarPath: Path, vararg args: String): List<String> {
        return listOf(javaPath.toString(), "-jar", jarPath.toString(), *args)
    }

    fun processFor(jarPath: Path, vararg args: String): ProcessBuilder {
        return ProcessBuilder(commandFor(jarPath, *args)).apply { setCapsuleCacheDir(environment()) }
    }

    fun setCapsuleCacheDir(env: MutableMap<String, String>): MutableMap<String, String> = env.apply {
        put("CAPSULE_CACHE_DIR", capsuleHome.toString())
    }
}

typealias atRuntime = (Path, String) -> Unit

fun checkExists(path: Path, header: String) {
    if (path.exists()) return

    val alert = Alert(ERROR)
    alert.isResizable = true
    alert.headerText = header
    alert.contentText = "'$path' does not exist.\n" +
            "Please install all of DemoBench's runtime dependencies or run the installer. " +
            "See the documentation for more details."

    val stage = alert.dialogPane.scene.window as Stage
    stage.isAlwaysOnTop = true

    alert.show()
}
