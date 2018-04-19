package net.corda.behave.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

// TODO Already available in corda core

inline fun <reified T> getLogger(): Logger =
        LoggerFactory.getLogger(T::class.java)
