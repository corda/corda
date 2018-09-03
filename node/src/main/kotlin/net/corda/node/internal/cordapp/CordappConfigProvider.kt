package net.corda.node.internal.cordapp

import com.typesafe.config.Config

interface CordappConfigProvider {
    fun getConfigByName(name: String): Config
}