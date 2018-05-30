package net.corda.gradle.jarfilter

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*
import kotlin.annotation.Retention
import kotlin.annotation.Target

@Target(
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY_GETTER,
    PROPERTY_SETTER
)
@Retention(RUNTIME)
annotation class StubMeOut