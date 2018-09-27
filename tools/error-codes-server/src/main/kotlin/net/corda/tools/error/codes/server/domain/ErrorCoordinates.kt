package net.corda.tools.error.codes.server.domain

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.PlatformEdition
import net.corda.tools.error.codes.server.domain.ReleaseVersion

data class ErrorCoordinates(val code: ErrorCode, val releaseVersion: ReleaseVersion, val platformEdition: PlatformEdition)