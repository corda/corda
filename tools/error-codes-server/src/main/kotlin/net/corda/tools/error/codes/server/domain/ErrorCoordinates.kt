package net.corda.tools.error.codes.server.domain

data class ErrorCoordinates(val code: ErrorCode, val releaseVersion: ReleaseVersion, val platformEdition: PlatformEdition)