package net.corda.core.internal

import kotlin.streams.toList

object CountryCodes {
    val iso3166TwoLetterCodes: Set<String> by lazy {
        javaClass.getResourceAsStream("iso3166.txt").bufferedReader().lines()
                .filter { !it.startsWith("#") }
                .map { line ->
                    val parts = line.split(" ")
                    if (parts.size >= 4) {
                        parts[0]
                    } else {
                        null
                    }
                }.toList().filterNotNull().toSet()
    }
}
