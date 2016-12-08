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