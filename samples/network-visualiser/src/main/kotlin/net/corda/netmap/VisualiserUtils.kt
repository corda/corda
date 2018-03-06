/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.netmap

import javafx.scene.paint.Color

internal
fun colorToRgb(color: Color): String {
    val builder = StringBuilder()

    builder.append("rgb(")
    builder.append(Math.round(color.red * 256))
    builder.append(",")
    builder.append(Math.round(color.green * 256))
    builder.append(",")
    builder.append(Math.round(color.blue * 256))
    builder.append(")")

    return builder.toString()
}