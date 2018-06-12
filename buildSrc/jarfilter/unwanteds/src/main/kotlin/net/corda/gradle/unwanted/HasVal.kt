@file:JvmName("HasVal")
@file:Suppress("UNUSED")
package net.corda.gradle.unwanted

interface HasStringVal {
    val stringVal: String
}

interface HasLongVal {
    val longVal: Long
}

interface HasIntVal {
    val intVal: Int
}

interface HasAllVal : HasIntVal, HasLongVal, HasStringVal