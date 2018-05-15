/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts.math

import java.util.*

interface Interpolator {
    fun interpolate(x: Double): Double
}

interface InterpolatorFactory {
    fun create(xs: DoubleArray, ys: DoubleArray): Interpolator
}

/**
 * Interpolates values between the given data points using straight lines.
 */
class LinearInterpolator(private val xs: DoubleArray, private val ys: DoubleArray) : Interpolator {
    init {
        require(xs.size == ys.size) { "x and y dimensions should match: ${xs.size} != ${ys.size}" }
        require(xs.size >= 2) { "At least 2 data points are required for linear interpolation, received: ${xs.size}" }
    }

    companion object Factory : InterpolatorFactory {
        override fun create(xs: DoubleArray, ys: DoubleArray) = LinearInterpolator(xs, ys)
    }

    override fun interpolate(x: Double): Double {
        val x0 = xs.first()
        if (x0 == x) return x0

        require(x > x0) { "Can't interpolate below $x0" }

        for (i in 1 until xs.size) {
            if (xs[i] == x) return xs[i]
            else if (xs[i] > x) return interpolateBetween(x, xs[i - 1], xs[i], ys[i - 1], ys[i])
        }
        throw IllegalArgumentException("Can't interpolate above ${xs.last()}")
    }

    private fun interpolateBetween(x: Double, x1: Double, x2: Double, y1: Double, y2: Double): Double {
        // N.B. The classic y1 + (y2 - y1) * (x - x1) / (x2 - x1) is numerically unstable!!
        val deltaX = (x - x1) / (x2 - x1)
        return y1 * (1.0 - deltaX) + y2 * deltaX
    }
}

/**
 * Interpolates values between the given data points using a [SplineFunction].
 *
 * Implementation uses the Natural Cubic Spline algorithm as described in
 * R. L. Burden and J. D. Faires (2011), *Numerical Analysis*. 9th ed. Boston, MA: Brooks/Cole, Cengage Learning. p149-150.
 */
class CubicSplineInterpolator(private val xs: DoubleArray, private val ys: DoubleArray) : Interpolator {
    init {
        require(xs.size == ys.size) { "x and y dimensions should match: ${xs.size} != ${ys.size}" }
        require(xs.size >= 3) { "At least 3 data points are required for cubic interpolation, received: ${xs.size}" }
    }

    companion object Factory : InterpolatorFactory {
        override fun create(xs: DoubleArray, ys: DoubleArray) = CubicSplineInterpolator(xs, ys)
    }

    private val splineFunction by lazy { computeSplineFunction() }

    override fun interpolate(x: Double): Double {
        require(x >= xs.first() && x <= xs.last()) { "Can't interpolate below ${xs.first()} or above ${xs.last()}" }
        return splineFunction.getValue(x)
    }

    private fun computeSplineFunction(): SplineFunction {
        val n = xs.size - 1

        // Coefficients of polynomial
        val b = DoubleArray(n) // linear
        val c = DoubleArray(n + 1) // quadratic
        val d = DoubleArray(n) // cubic

        // Helpers
        val h = DoubleArray(n)
        val g = DoubleArray(n)

        for (i in 0 until n)
            h[i] = xs[i + 1] - xs[i]
        for (i in 1 until n)
            g[i] = 3 / h[i] * (ys[i + 1] - ys[i]) - 3 / h[i - 1] * (ys[i] - ys[i - 1])

        // Solve tridiagonal linear system (using Crout Factorization)
        val m = DoubleArray(n)
        val z = DoubleArray(n)
        for (i in 1 until n) {
            val l = 2 * (xs[i + 1] - xs[i - 1]) - h[i - 1] * m[i - 1]
            m[i] = h[i] / l
            z[i] = (g[i] - h[i - 1] * z[i - 1]) / l
        }
        for (j in n - 1 downTo 0) {
            c[j] = z[j] - m[j] * c[j + 1]
            b[j] = (ys[j + 1] - ys[j]) / h[j] - h[j] * (c[j + 1] + 2.0 * c[j]) / 3.0
            d[j] = (c[j + 1] - c[j]) / (3.0 * h[j])
        }

        val segmentMap = TreeMap<Double, Polynomial>()
        for (i in 0 until n) {
            val coefficients = doubleArrayOf(ys[i], b[i], c[i], d[i])
            segmentMap[xs[i]] = Polynomial(coefficients)
        }
        return SplineFunction(segmentMap)
    }
}

/**
 * Represents a polynomial function of arbitrary degree.
 * @param coefficients polynomial coefficients in the order of degree (constant first, followed by higher degree term coefficients).
 */
class Polynomial(private val coefficients: DoubleArray) {
    private val reversedCoefficients = coefficients.reversed()

    fun getValue(x: Double) = reversedCoefficients.fold(0.0, { result, c -> result * x + c })
}

/**
 * A *spline* is function piecewise-defined by polynomial functions.
 * Points at which polynomial pieces connect are known as *knots*.
 *
 * @param segmentMap a mapping between a knot and the polynomial that covers the subsequent interval.
 */
class SplineFunction(private val segmentMap: TreeMap<Double, Polynomial>) {
    fun getValue(x: Double): Double {
        val (knot, polynomial) = segmentMap.floorEntry(x)
        return polynomial.getValue(x - knot)
    }
}
