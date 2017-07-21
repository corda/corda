package net.corda.testing

import org.junit.After
import org.junit.Before

/**
 * The beginnings of somewhere to inject implementations for unit tests.
 */
abstract class TestDependencyInjectionBase {
    @Before
    fun initialiseSerialization() {
        initialiseTestSerialization()
    }

    @After
    fun resetInitialisation() {
        resetTestSerialization()
    }
}