/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import java.util.*
import kotlin.reflect.declaredMemberProperties

interface NodeConfiguration {
    val myLegalName: String
    val exportJMXto: String
}

object DefaultConfiguration : NodeConfiguration {
    override val myLegalName: String = "Vast Global MegaCorp"
    override val exportJMXto: String = ""   // can be "http" or empty

    fun toProperties(): Properties {
        val settings = DefaultConfiguration::class.declaredMemberProperties.map { it.name to it.get(this@DefaultConfiguration).toString() }
        val p = Properties().apply {
            for (setting in settings) {
                setProperty(setting.first, setting.second)
            }
        }
        return p
    }
}

/**
 * A simple wrapper around a plain old Java .properties file. The keys have the same name as in the source code.
 *
 * TODO: Replace Java properties file with a better config file format (maybe yaml).
 * We want to be able to configure via a GUI too, so an ability to round-trip whitespace, comments etc when machine
 * editing the file is a must-have.
 */
class NodeConfigurationFromProperties(private val properties: Properties) : NodeConfiguration {
    override val myLegalName: String get() = properties.getProperty("myLegalName")
    override val exportJMXto: String get() = properties.getProperty("exportJMXto")
}