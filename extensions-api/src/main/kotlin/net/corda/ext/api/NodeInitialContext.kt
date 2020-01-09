package net.corda.ext.api

import net.corda.common.configuration.parsing.internal.ConfigurationWithOptionsContainer

/**
 * Bare minimum information which will be available even before node fully started-up.
 */
interface NodeInitialContext : ConfigurationWithOptionsContainer {
    val platformVersion: Int
}