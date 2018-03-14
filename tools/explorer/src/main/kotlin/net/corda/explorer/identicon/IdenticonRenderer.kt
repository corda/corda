/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer.identicon

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.base.Splitter
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import net.corda.core.crypto.SecureHash

/**
 *  (The MIT License)
 *  Copyright (c) 2007-2012 Don Park <donpark@docuverse.com>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  'Software'), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 *  IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 *  CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 *  TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *  The code originated from : https://github.com/donpark/identicon
 *  And has been modified to Kotlin and JavaFX instead of Java code using AWT
 */

object IdenticonRenderer {
    /**
     * Each patch is a polygon created from a list of vertices on a 5 by 5 grid.
     * Vertices are numbered from 0 to 24, starting from top-left corner of the
     * grid, moving left to right and top to bottom.
     */
    private val patchTypes = arrayOf(
            byteArrayOf(0, 4, 24, 20, 0),
            byteArrayOf(0, 4, 20, 0),
            byteArrayOf(2, 24, 20, 2),
            byteArrayOf(0, 2, 20, 22, 0),
            byteArrayOf(2, 14, 22, 10, 2),
            byteArrayOf(0, 14, 24, 22, 0),
            byteArrayOf(2, 24, 22, 13, 11, 22, 20, 2),
            byteArrayOf(0, 14, 22, 0),
            byteArrayOf(6, 8, 18, 16, 6),
            byteArrayOf(4, 20, 10, 12, 2, 4),
            byteArrayOf(0, 2, 12, 10, 0),
            byteArrayOf(10, 14, 22, 10),
            byteArrayOf(20, 12, 24, 20),
            byteArrayOf(10, 2, 12, 10),
            byteArrayOf(0, 2, 10, 0),
            byteArrayOf(0, 4, 24, 20, 0)).map(::Patch)

    private val PATCH_CELLS = 4
    private val PATCH_GRIDS = PATCH_CELLS + 1
    private val PATCH_SYMMETRIC: Byte = 1
    private val PATCH_INVERTED: Byte = 2

    private val patchFlags = byteArrayOf(PATCH_SYMMETRIC, 0, 0, 0, PATCH_SYMMETRIC, 0, 0, 0, PATCH_SYMMETRIC, 0, 0, 0, 0, 0, 0, (PATCH_SYMMETRIC + PATCH_INVERTED).toByte())

    private val renderingSize = 30.0

    private val cache = Caffeine.newBuilder().build(CacheLoader<SecureHash, Image> { key ->
        key?.let { render(key.hashCode(), renderingSize) }
    })

    private class Patch(private val byteArray: ByteArray) {
        fun x(patchSize: Double): DoubleArray {
            return byteArray.map(Byte::toInt).map { it % PATCH_GRIDS * (patchSize / PATCH_CELLS) - patchSize / 2 }.toDoubleArray()
        }

        fun y(patchSize: Double): DoubleArray {
            return byteArray.map(Byte::toInt).map { it / PATCH_GRIDS * (patchSize / PATCH_CELLS) - patchSize / 2 }.toDoubleArray()
        }

        val size = byteArray.size
    }

    fun getIdenticon(hash: SecureHash): Image {
        return cache.get(hash)!!
    }

    /**
     * Returns rendered identicon image for given identicon code.
     * Size of the returned identicon image is determined by patchSize set using
     * [setPatchSize]. Since a 9-block identicon consists of 3x3 patches,
     * width and height will be 3 times the patch size.
     */
    private fun render(code: Int, patchSize: Double, backgroundColor: Color = Color.WHITE): Image {
        // decode the code into parts
        val middleType = intArrayOf(0, 4, 8, 15)[code and 0x3]      // bit 0-1: middle patch type
        val middleInvert = code shr 2 and 0x1 != 0                  // bit 2: middle invert
        val cornerType = code shr 3 and 0x0f                        // bit 3-6: corner patch type
        val cornerInvert = code shr 7 and 0x1 != 0                  // bit 7: corner invert
        val cornerTurn = code shr 8 and 0x3                         // bit 8-9: corner turns
        val sideType = code shr 10 and 0x0f                         // bit 10-13: side patch type
        val sideInvert = code shr 14 and 0x1 != 0                   // bit 14: side invert
        val sideTurn = code shr 15 and 0x3                          // bit 15: corner turns
        val blue = code shr 16 and 0x01f                    // bit 16-20: blue color component
        val green = code shr 21 and 0x01f                   // bit 21-26: green color component
        val red = code shr 27 and 0x01f                     // bit 27-31: red color component

        // color components are used at top of the range for color difference
        // use white background for now.
        // TODO: support transparency.
        val fillColor = Color.rgb(red shl 3, green shl 3, blue shl 3)
        // outline shapes with a noticeable color (complementary will do) if
        // shape color and background color are too similar (measured by color
        // distance).
        val strokeColor = if (getColorDistance(fillColor, backgroundColor) < 32.0f) fillColor.invert() else null

        val sourceSize = patchSize * 3
        val canvas = Canvas(sourceSize, sourceSize)
        val g = canvas.graphicsContext2D
        /** Rendering Order:
         *     6 2 7
         *     5 1 3
         *     9 4 8      */
        val color = PatchColor(fillColor, strokeColor, backgroundColor)
        drawPatch(g, patchSize, patchSize, middleType, 0, patchSize, middleInvert, color)
        drawPatch(g, patchSize, 0.0, sideType, sideTurn, patchSize, sideInvert, color)
        drawPatch(g, patchSize * 2, patchSize, sideType, sideTurn + 1, patchSize, sideInvert, color)
        drawPatch(g, patchSize, patchSize * 2, sideType, sideTurn + 2, patchSize, sideInvert, color)
        drawPatch(g, 0.0, patchSize, sideType, sideTurn + 3, patchSize, sideInvert, color)
        drawPatch(g, 0.0, 0.0, cornerType, cornerTurn, patchSize, cornerInvert, color)
        drawPatch(g, patchSize * 2, 0.0, cornerType, cornerTurn + 1, patchSize, cornerInvert, color)
        drawPatch(g, patchSize * 2, patchSize * 2, cornerType, cornerTurn + 2, patchSize, cornerInvert, color)
        drawPatch(g, 0.0, patchSize * 2, cornerType, cornerTurn + 3, patchSize, cornerInvert, color)
        return canvas.snapshot(SnapshotParameters(), WritableImage(sourceSize.toInt(), sourceSize.toInt()))
    }

    private class PatchColor(private val fillColor: Color, val strokeColor: Color?, private val backgroundColor: Color) {
        fun background(invert: Boolean) = if (invert) fillColor else backgroundColor
        fun fill(invert: Boolean) = if (invert) backgroundColor else fillColor
    }

    private fun drawPatch(g: GraphicsContext, x: Double, y: Double, patchIndex: Int, turn: Int, patchSize: Double, _invert: Boolean, color: PatchColor) {
        val patch = patchTypes[patchIndex % patchTypes.size]
        val invert = if ((patchFlags[patchIndex].toInt() and PATCH_INVERTED.toInt()) != 0) !_invert else _invert
        g.apply {
            // paint background
            clearRect(x, y, patchSize, patchSize)
            fill = color.background(invert)
            stroke = color.background(invert)
            fillRect(x, y, patchSize, patchSize)
            strokeRect(x, y, patchSize, patchSize)
            // offset and rotate coordinate space by patch position (x, y) and
            // 'turn' before rendering patch shape
            val saved = transform
            translate(x + patchSize / 2, y + patchSize / 2)
            rotate((turn % 4 * 90).toDouble())

            // if stroke color was specified, apply stroke
            // stroke color should be specified if fore color is too close to the
            // back color.
            if (color.strokeColor != null) {
                stroke = color.strokeColor
                strokePolygon(patch.x(patchSize), patch.y(patchSize), patch.size)
            }
            // render rotated patch using fore color (back color if inverted)
            fill = color.fill(invert)
            fillPolygon(patch.x(patchSize), patch.y(patchSize), patch.size)
            // restore rotation
            transform = saved
        }
    }

    /**
     * Returns distance between two colors.
     */
    private fun getColorDistance(c1: Color, c2: Color): Float {
        val dx = (c1.red - c2.red) * 256
        val dy = (c1.green - c2.green) * 256
        val dz = (c1.blue - c2.blue) * 256
        return Math.sqrt(dx * dx + dy * dy + dz * dz).toFloat()
    }
}

fun identicon(secureHash: SecureHash, size: Double): ImageView {
    return ImageView(IdenticonRenderer.getIdenticon(secureHash)).apply {
        isPreserveRatio = true
        fitWidth = size
        styleClass += "identicon"
    }
}

fun identiconToolTip(secureHash: SecureHash, description: String? = null): Tooltip {
    return Tooltip(Splitter.fixedLength(16).split("${description ?: secureHash}").joinToString("\n")).apply {
        contentDisplay = ContentDisplay.TOP
        textAlignment = TextAlignment.CENTER
        graphic = identicon(secureHash, 90.0)
        isAutoHide = false
    }
}