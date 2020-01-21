package net.corda.nodeapi.internal.lifecycle

import net.corda.common.configuration.parsing.internal.ConfigurationWithOptionsContainer

/**
 * Bare minimum information which will be available even before node fully started-up.
 */
interface NodeInitialContext : ConfigurationWithOptionsContainer {
    val platformVersion: Int
}