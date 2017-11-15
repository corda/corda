package net.corda.core.internal.cordapp

import com.typesafe.config.Config

interface CordappConfigProvider {
    fun getConfigByName(name: String): Config
}