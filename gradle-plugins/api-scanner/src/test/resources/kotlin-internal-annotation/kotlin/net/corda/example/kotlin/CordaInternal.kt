package net.corda.example.kotlin

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*

@Target(FILE, CLASS, FUNCTION, ANNOTATION_CLASS)
@Retention(BINARY)
annotation class CordaInternal