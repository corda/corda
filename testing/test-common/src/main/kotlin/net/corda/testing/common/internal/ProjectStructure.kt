/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.common.internal

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
