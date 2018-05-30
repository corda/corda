@file:JvmName("HasData")
package net.corda.gradle.unwanted

interface HasString {
    fun stringData(): String
}

interface HasLong {
    fun longData(): Long
}

interface HasInt {
    fun intData(): Int
}

interface HasAll : HasInt, HasLong, HasString