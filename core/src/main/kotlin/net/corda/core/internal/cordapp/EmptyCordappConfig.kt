package net.corda.core.internal.cordapp

import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappConfigException
import java.lang.UnsupportedOperationException

object EmptyCordappConfig : CordappConfig {
    override fun exists(path: String): Boolean {
        return false
    }

    override fun get(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

    override fun getInt(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

    override fun getLong(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

    override fun getFloat(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

    override fun getDouble(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

    override fun getNumber(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

    override fun getString(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

    override fun getBoolean(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())
}