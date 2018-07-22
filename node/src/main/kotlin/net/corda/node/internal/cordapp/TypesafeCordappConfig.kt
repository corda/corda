/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.cordapp

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappConfigException
import net.corda.nodeapi.internal.config.getBooleanCaseInsensitive

/**
 * Provides configuration from a typesafe config source
 */
class TypesafeCordappConfig(private val cordappConfig: Config) : CordappConfig {
    override fun exists(path: String): Boolean {
        return cordappConfig.hasPath(path)
    }

    override fun get(path: String): Any {
        try {
            return cordappConfig.getAnyRef(path)
        } catch (e: ConfigException) {
            throw CordappConfigException("Cordapp configuration is incorrect due to exception", e)
        }
    }

    override fun getInt(path: String): Int {
        try {
            return cordappConfig.getInt(path)
        } catch (e: ConfigException) {
            throw CordappConfigException("Cordapp configuration is incorrect due to exception", e)
        }
    }

    override fun getLong(path: String): Long {
        try {
            return cordappConfig.getLong(path)
        } catch (e: ConfigException) {
            throw CordappConfigException("Cordapp configuration is incorrect due to exception", e)
        }
    }

    override fun getFloat(path: String): Float {
        try {
            return cordappConfig.getDouble(path).toFloat()
        } catch (e: ConfigException) {
            throw CordappConfigException("Cordapp configuration is incorrect due to exception", e)
        }
    }

    override fun getDouble(path: String): Double {
        try {
            return cordappConfig.getDouble(path)
        } catch (e: ConfigException) {
            throw CordappConfigException("Cordapp configuration is incorrect due to exception", e)
        }
    }

    override fun getNumber(path: String): Number {
        try {
            return cordappConfig.getNumber(path)
        } catch (e: ConfigException) {
            throw CordappConfigException("Cordapp configuration is incorrect due to exception", e)
        }
    }

    override fun getString(path: String): String {
        try {
            return cordappConfig.getString(path)
        } catch (e: ConfigException) {
            throw CordappConfigException("Cordapp configuration is incorrect due to exception", e)
        }
    }

    override fun getBoolean(path: String): Boolean {
        try {
            return cordappConfig.getBooleanCaseInsensitive(path)
        } catch (e: ConfigException) {
            throw CordappConfigException("Cordapp configuration is incorrect due to exception", e)
        }
    }
}