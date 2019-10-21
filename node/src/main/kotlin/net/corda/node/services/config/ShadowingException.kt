package net.corda.node.services.config

import com.typesafe.config.ConfigException

class ShadowingException(definedProperty : String, convertedProperty : String)
    : ConfigException(
        "Environment variable $definedProperty is shadowing another property transformed to $convertedProperty")