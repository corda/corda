package net.corda.example.kotlin

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*

@Target(FILE, CLASS, FUNCTION)
@Retention(BINARY)
@CordaInternal
annotation class InvisibleAnnotation