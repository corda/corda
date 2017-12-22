@file:JvmName("JsonUtils")
package net.corda.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream

inline fun <reified T : Any> ObjectMapper.readValue(input: InputStream): T = readValue(input, T::class.java)
inline fun <reified T : Any> ObjectMapper.readValue(input: String): T = readValue(input, T::class.java)