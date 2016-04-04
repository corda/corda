package core.math

import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InterpolatorsTest {

    @Test
    fun `throws when key to interpolate is outside the data set`() {
        val xs = doubleArrayOf(1.0, 2.0, 4.0, 5.0)
        val interpolator = CubicSplineInterpolator(xs, ys = xs)
        assertFailsWith<IllegalArgumentException> { interpolator.interpolate(0.0) }
        assertFailsWith<IllegalArgumentException> { interpolator.interpolate(6.0) }
    }

    @Test
    fun `throws when data set is less than 3 points`() {
        val xs = doubleArrayOf(1.0, 2.0)
        assertFailsWith<IllegalArgumentException> { CubicSplineInterpolator(xs, ys = xs) }
    }

    @Test
    fun `returns existing value when key is in data set`() {
        val xs = doubleArrayOf(1.0, 2.0, 4.0, 5.0)
        val interpolatedValue = CubicSplineInterpolator(xs, ys = xs).interpolate(2.0)
        assertEquals(2.0, interpolatedValue)
    }

    @Test
    fun `interpolates missing values correctly`() {
        val xs = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val ys = doubleArrayOf(2.0, 4.0, 5.0, 11.0, 10.0)
        val toInterpolate = doubleArrayOf(1.5, 2.5, 2.8, 3.3, 3.7, 4.3, 4.7)
        // Expected values generated using R's splinefun (package stats v3.2.4), "natural" method
        val expected = doubleArrayOf(3.28, 4.03, 4.37, 6.7, 9.46, 11.5, 10.91)

        val interpolator = CubicSplineInterpolator(xs, ys)
        val actual = toInterpolate.map { interpolator.interpolate(it) }.toDoubleArray()
        Assert.assertArrayEquals(expected, actual, 0.01)
    }
}