package net.corda.example

interface KotlinVarargMethod {
    fun action(vararg arg: Int)
}

interface KotlinVarargArrayMethod {
    fun action(vararg arg: Array<String>)
}
