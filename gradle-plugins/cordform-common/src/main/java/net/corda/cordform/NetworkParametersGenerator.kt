package net.corda.cordform

import java.nio.file.Path

interface NetworkParametersGenerator {
    fun run(baseDirectory: Path, notaryMap: Map<String, Boolean>)
}