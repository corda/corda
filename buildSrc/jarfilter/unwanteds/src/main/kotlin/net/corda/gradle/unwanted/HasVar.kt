@file:JvmName("HasVar")
@file:Suppress("UNUSED")
package net.corda.gradle.unwanted

interface HasStringVar {
    var stringVar: String
}

interface HasLongVar {
    var longVar: Long
}

interface HasIntVar {
    var intVar: Int
}

interface HasAllVar : HasIntVar, HasLongVar, HasStringVar
