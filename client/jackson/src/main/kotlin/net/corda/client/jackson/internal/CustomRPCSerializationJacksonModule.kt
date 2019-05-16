package net.corda.client.jackson.internal

import com.fasterxml.jackson.databind.Module

/**
 * Should be implemented by CorDapps who wish to declare custom serializers.
 * Classes of this type will be autodiscovered and registered.
 */
interface CustomShellSerializationFactory {

    /**
     * The returned [Module] will be registered automatically with the interactive shell.
     */
    fun createJacksonModule(): Module
}