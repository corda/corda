package net.corda.gradle.jarfilter.annotations

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.PROPERTY

@Retention(BINARY)
@Target(PROPERTY)
annotation class Deletable
