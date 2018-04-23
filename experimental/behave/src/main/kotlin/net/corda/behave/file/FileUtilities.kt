/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.file

import java.nio.file.Path
import java.nio.file.Paths

val currentDirectory: Path
    get() = Paths.get(System.getProperty("user.dir"))

// location of Corda distributions and Drivers dependencies
val stagingRoot: Path
    get() = System.getProperty("STAGING_ROOT")?.let { Paths.get(it) } ?: currentDirectory
