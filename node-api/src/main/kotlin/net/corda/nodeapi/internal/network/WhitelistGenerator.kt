package net.corda.nodeapi.internal.network

import net.corda.core.contracts.ContractClassName
import net.corda.core.internal.*
import net.corda.core.node.NetworkParameters
import net.corda.core.node.services.AttachmentId
import net.corda.nodeapi.internal.ContractsJar
import org.slf4j.LoggerFactory
import java.nio.file.Path

private const val EXCLUDE_WHITELIST_FILE_NAME = "exclude_whitelist.txt"
private const val INCLUDE_WHITELIST_FILE_NAME = "include_whitelist.txt"
private val logger = LoggerFactory.getLogger("net.corda.nodeapi.internal.network.WhitelistGenerator")

fun generateWhitelist(networkParameters: NetworkParameters?,
                      excludeContracts: List<ContractClassName>,
                      cordappJars: List<ContractsJar>,
                      includeContracts: List<ContractClassName>,
                      optionalCordappJars: List<ContractsJar>): Map<ContractClassName, List<AttachmentId>> {
    val existingWhitelist = networkParameters?.whitelistedContractImplementations ?: emptyMap()

    if (excludeContracts.isNotEmpty()) {
        logger.info("Exclude contracts from $EXCLUDE_WHITELIST_FILE_NAME: ${excludeContracts.joinToString()}")
        existingWhitelist.keys.forEach {
            require(it !in excludeContracts) { "$it is already part of the existing whitelist and cannot be excluded." }
        }
    }

    val newWhiteList = cordappJars
            .flatMap { jar -> (jar.scan() - excludeContracts).map { it to jar.hash } }
            .toMultiMap()

    if (includeContracts.isNotEmpty())
        logger.info("Include contracts from $INCLUDE_WHITELIST_FILE_NAME: ${includeContracts.joinToString()} present in JARs: $optionalCordappJars.")

    val newSignedJarsWhiteList = optionalCordappJars
            .flatMap { jar -> (jar.scan()).filter { includeContracts.contains(it) }.map { it to jar.hash } }
            .toMultiMap()

    return (newWhiteList.keys + existingWhitelist.keys + newSignedJarsWhiteList.keys).associateBy({ it }) {
        val existingHashes = existingWhitelist[it] ?: emptyList()
        val newHashes = newWhiteList[it] ?: emptyList()
        val newHashesFormSignedJar = newSignedJarsWhiteList[it] ?: emptyList()
        (existingHashes + newHashes + newHashesFormSignedJar).distinct()
    }
}

fun readExcludeWhitelist(directory: Path): List<String> = readAllLines(directory / EXCLUDE_WHITELIST_FILE_NAME)

fun readIncludeWhitelist(directory: Path): List<String> = readAllLines(directory / INCLUDE_WHITELIST_FILE_NAME)

private fun readAllLines(path: Path) : List<String> = if (path.exists()) path.readAllLines().map(String::trim) else emptyList()
