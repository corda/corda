package net.corda.gradle.jarfilter

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*
import kotlin.annotation.Retention
import kotlin.annotation.Target

@Target(
    FILE,
    CLASS,
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY,
    PROPERTY_GETTER,
    PROPERTY_SETTER,
    FIELD
)
@Retention(RUNTIME)
annotation class RemoveMe
