package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.PlatformEdition
import net.corda.tools.error.codes.server.domain.ReleaseVersion

internal data class ErrorCoordinates(val code: ErrorCode, val releaseVersion: ReleaseVersion, val platformEdition: PlatformEdition)