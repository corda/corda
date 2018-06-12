@file:JvmName("RuntimeAnnotations")
package net.corda.gradle

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*
import kotlin.annotation.Retention
import kotlin.annotation.Target

@Target(VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class Parameter
